package com.cleanroute.api;

import com.cleanroute.api.support.TestDatabase;
import com.cleanroute.api.dto.PollutionData;
import com.cleanroute.api.dto.TomTomRouteResponse;
import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import com.cleanroute.api.service.integration.TomTomRoutingClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application against a throwaway PostGIS database (Flyway migrations and
 * Hibernate schema validation included) and exercises the API end to end.
 * TomTom and Open-Meteo are replaced with mocks so the test is offline and deterministic.
 */
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==",
        "pollution.scheduler.enabled=false",
        "security.auth-rate-limit.per-minute=1000"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgis = TestDatabase.newContainer();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    TomTomRoutingClient tomTom;

    @MockitoBean
    OpenMeteoAqiClient openMeteo;

    @BeforeEach
    void stubExternalApis() {
        // Two routes: the fastest runs along latitude 28.60, a slower detour along 28.70.
        when(tomTom.getDirections(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(tomTomResponse());
        // Air is dirtier further south, so the detour (further north) is cleaner.
        when(openMeteo.getPollutionData(anyDouble(), anyDouble())).thenAnswer(inv -> {
            double lat = inv.getArgument(0);
            double aqi = lat < 28.65 ? 80 : 40;
            return new PollutionData(aqi, aqi / 4, aqi / 2, 20, 30, 0);
        });
    }

    // ------------------------------------------------------------------ tests

    @Test
    void databaseImage_OffersPgvector() {
        // The test database is built from db/Dockerfile, like production, so pgvector must be installable.
        Integer available = jdbc.queryForObject(
                "SELECT count(*) FROM pg_available_extensions WHERE name = 'vector'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(available).isEqualTo(1);
    }

    @Test
    void publicEndpoints_AndAuthErrors() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/routes")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/routes").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.dev\",\"password\":\"whatever123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signup_ValidatesAndRejectsDuplicates() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("password")));

        String email = uniqueEmail();
        signup(email);
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void fullRouteLifecycle() throws Exception {
        String token = signup(uniqueEmail());

        mvc.perform(auth(get("/api/v1/auth/me"), token)).andExpect(status().isOk());

        // Alternatives come back cleanest first, with the fastest one flagged.
        String alternatives = mvc.perform(auth(post("/api/v1/routes/alternatives"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"walking\",\"pathCoordinates\":[[77.20,28.60],[77.30,28.60]]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].pollutionScore").value(40.0))
                .andExpect(jsonPath("$[0].fastest").value(false))
                .andExpect(jsonPath("$[0].exposureSavingPercent").value(50.0))
                .andExpect(jsonPath("$[1].fastest").value(true))
                .andReturn().getResponse().getContentAsString();
        List<List<Double>> cleanestPath = JsonPath.read(alternatives, "$[0].pathCoordinates");

        // Save the cleanest alternative.
        String saved = mvc.perform(auth(post("/api/v1/routes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Office walk\",\"type\":\"walking\",\"distanceMeters\":12000,"
                                + "\"travelTimeSeconds\":9000,\"pathCoordinates\":" + toJson(cleanestPath) + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastPollutionScore").value(40.0))
                .andExpect(jsonPath("$.distanceKm").value(12.0))
                .andReturn().getResponse().getContentAsString();
        String routeId = JsonPath.read(saved, "$.id");

        mvc.perform(auth(get("/api/v1/routes"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(auth(get("/api/v1/routes/" + routeId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pathCoordinates", hasSize(cleanestPath.size())));

        mvc.perform(auth(patch("/api/v1/routes/" + routeId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.isActive").value(false));

        mvc.perform(auth(get("/api/v1/routes/" + routeId + "/metrics?days=7&tz=Asia/Kolkata"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].aqi").value(40.0))
                .andExpect(jsonPath("$.bestHour", notNullValue()));

        mvc.perform(auth(get("/api/v1/routes/" + routeId + "/metrics?tz=Not/AZone"), token))
                .andExpect(status().isBadRequest());

        // Another user cannot see or delete it.
        String intruder = signup(uniqueEmail());
        mvc.perform(auth(get("/api/v1/routes/" + routeId), intruder)).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/routes/" + routeId), intruder)).andExpect(status().isNotFound());

        mvc.perform(auth(delete("/api/v1/routes/" + routeId), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/routes"), token)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void invalidRouteInput_IsRejected() throws Exception {
        String token = signup(uniqueEmail());
        mvc.perform(auth(post("/api/v1/routes/alternatives"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathCoordinates\":[[77.2,28.6],[500,28.6]]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/v1/routes/alternatives"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"teleport\",\"pathCoordinates\":[[77.2,28.6],[77.3,28.6]]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/v1/routes/" + UUID.randomUUID()), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void preferences_ChangeTheScore() throws Exception {
        String token = signup(uniqueEmail());
        mvc.perform(auth(post("/api/v1/auth/preferences"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avoidPm25\":true,\"avoidOzone\":false,\"avoidPm10\":false,\"avoidNo2\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avoidPm25").value(true));

        // Cleanest route: AQI 40 + PM2.5 (10) x 1.5 = 55
        mvc.perform(auth(post("/api/v1/routes/alternatives"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathCoordinates\":[[77.20,28.60],[77.30,28.60]]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pollutionScore").value(55.0));
    }

    @Test
    void logoutAndPasswordChange_RevokeTokens() throws Exception {
        String email = uniqueEmail();
        String token = signup(email);

        String changed = mvc.perform(auth(post("/api/v1/auth/password"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Str0ng!Pass123\",\"newPassword\":\"An0ther!Pass456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(changed, "$.token");

        mvc.perform(auth(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/v1/auth/me"), newToken)).andExpect(status().isOk());

        mvc.perform(auth(post("/api/v1/auth/logout"), newToken)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/auth/me"), newToken)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"An0ther!Pass456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void notifications_AndAccountDeletion() throws Exception {
        String token = signup(uniqueEmail());
        mvc.perform(auth(get("/api/v1/notifications"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(auth(get("/api/v1/notifications/unread-count"), token))
                .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(auth(post("/api/v1/notifications/" + UUID.randomUUID() + "/read"), token))
                .andExpect(status().isNotFound());

        mvc.perform(auth(delete("/api/v1/auth/account"), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private String signup(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String signupJson(String email) {
        return "{\"name\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"Str0ng!Pass123\"}";
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.dev";
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private static String toJson(List<List<Double>> path) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(path.get(i).get(0)).append(',').append(path.get(i).get(1)).append(']');
        }
        return sb.append(']').toString();
    }

    private static TomTomRouteResponse tomTomResponse() {
        TomTomRouteResponse response = new TomTomRouteResponse();
        response.setRoutes(List.of(
                tomTomRoute(28.60, 10_000, 7_200),   // fastest, dirtier
                tomTomRoute(28.70, 12_000, 9_000))); // detour, cleaner
        return response;
    }

    private static TomTomRouteResponse.TomTomRoute tomTomRoute(double lat, int meters, int seconds) {
        List<TomTomRouteResponse.TomTomPoint> points = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            TomTomRouteResponse.TomTomPoint p = new TomTomRouteResponse.TomTomPoint();
            p.setLatitude(lat);
            p.setLongitude(77.20 + i * 0.005);
            points.add(p);
        }
        TomTomRouteResponse.TomTomLeg leg = new TomTomRouteResponse.TomTomLeg();
        leg.setPoints(points);
        TomTomRouteResponse.TomTomSummary summary = new TomTomRouteResponse.TomTomSummary();
        summary.setLengthInMeters(meters);
        summary.setTravelTimeInSeconds(seconds);
        TomTomRouteResponse.TomTomRoute route = new TomTomRouteResponse.TomTomRoute();
        route.setLegs(List.of(leg));
        route.setSummary(summary);
        return route;
    }
}
