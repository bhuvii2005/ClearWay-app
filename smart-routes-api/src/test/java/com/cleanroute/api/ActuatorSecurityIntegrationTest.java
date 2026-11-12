package com.cleanroute.api;

import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import com.cleanroute.api.service.integration.TomTomRoutingClient;
import com.cleanroute.api.support.TestDatabase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The actuator's own security chain: health is public; metrics and info need the HTTP Basic login
 * from CLEARWAY_METRICS_USER / CLEARWAY_METRICS_PASSWORD, and are switched off without it.
 */
@Testcontainers(disabledWithoutDocker = true)
class ActuatorSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgis = TestDatabase.newContainer();

    static final String USER = "metrics-admin";
    static final String PASSWORD = "test-only-metrics-password";

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @SpringBootTest(properties = {
            "jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==",
            "pollution.scheduler.enabled=false",
            "security.auth-rate-limit.per-minute=1000",
            "clearway.metrics.user=" + USER,
            "clearway.metrics.password=" + PASSWORD
    })
    @AutoConfigureMockMvc
    class WithCredentials {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        TomTomRoutingClient tomTom;

        @MockitoBean
        OpenMeteoAqiClient openMeteo;

        @Test
        void health_IsPublic() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        void healthDetails_OnlyForTheLogin() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(jsonPath("$.components").doesNotExist());
            mvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, basic(USER, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.ai.status").value("UP"))
                    // AI is off in tests, so no embedding call has been made.
                    .andExpect(jsonPath("$.components.ai.details.lastEmbeddingCall").value("none yet"));
        }

        @Test
        void metrics_WithoutLogin_Is401WithBasicChallenge() throws Exception {
            mvc.perform(get("/actuator/metrics"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Basic")))
                    .andExpect(jsonPath("$.status").value(401));
            mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        }

        @Test
        void metrics_WithWrongLogin_Is401() throws Exception {
            mvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, basic(USER, "wrong-password-123")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, basic("someone", PASSWORD)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void metrics_WithTheLogin_Is200() throws Exception {
            mvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, basic(USER, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.names").isArray());
            mvc.perform(get("/actuator/metrics/jvm.memory.used").header(HttpHeaders.AUTHORIZATION, basic(USER, PASSWORD)))
                    .andExpect(status().isOk());
        }

        @Test
        void anAppUsersJwt_DoesNotOpenMetrics() throws Exception {
            String body = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Metrics Tester\",\"email\":\"metrics-" + UUID.randomUUID()
                                    + "@test.dev\",\"password\":\"Str0ng!Pass123\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String token = JsonPath.read(body, "$.token");

            mvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==",
            "pollution.scheduler.enabled=false",
            "clearway.metrics.user=",
            "clearway.metrics.password="
    })
    @AutoConfigureMockMvc
    class WithoutCredentials {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        TomTomRoutingClient tomTom;

        @MockitoBean
        OpenMeteoAqiClient openMeteo;

        @Autowired
        org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier endpoints;

        @Test
        void health_IsStillPublic() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        void otherEndpoints_AreSwitchedOff() throws Exception {
            org.assertj.core.api.Assertions.assertThat(endpoints.getEndpoints())
                    .extracting(e -> e.getEndpointId().toString())
                    .containsExactly("health");
            mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, basic("", "")))
                    .andExpect(status().isUnauthorized());
        }
    }
}
