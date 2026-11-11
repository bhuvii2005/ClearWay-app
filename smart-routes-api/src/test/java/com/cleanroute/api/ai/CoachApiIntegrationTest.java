package com.cleanroute.api.ai;

import com.cleanroute.api.dto.PollutionData;
import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import com.cleanroute.api.service.integration.TomTomRoutingClient;
import com.cleanroute.api.support.TestDatabase;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Coach endpoint and tools in the whole application, against a throwaway PostGIS database.
 * The model is scripted (no network) and Open-Meteo and TomTom are mocked.
 */
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==",
        "pollution.scheduler.enabled=false",
        "security.auth-rate-limit.per-minute=1000",
        "clearway.ai.limits.messages-per-hour=3"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CoachApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgis = TestDatabase.newContainer();

    /** When set, the scripted provider fails with 500s (the gateway then answers 503). */
    static final AtomicBoolean PROVIDER_DOWN = new AtomicBoolean();

    @TestConfiguration
    static class ScriptedProvider {
        /** A configured gateway on a scripted model: it lists the routes once, then answers from the result. */
        @Bean
        @Primary
        LlmGateway scriptedGateway() {
            ScriptedChatModel[] model = new ScriptedChatModel[1];
            model[0] = new ScriptedChatModel(prompt -> {
                if (PROVIDER_DOWN.get()) {
                    throw HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "down", null, null, null);
                }
                return ScriptedChatModel.toolResults(prompt) == 0
                        ? model[0].toolCall("listMyRoutes", "{}")
                        : ScriptedChatModel.answer("Your routes: " + ScriptedChatModel.lastToolResult(prompt));
            });
            return new LlmGateway(Optional.of(new ProviderChatModel(AiProperties.Provider.GEMINI, "scripted", model[0])),
                    new HostedAiBudget(1000, 1_000_000, Clock.systemUTC()), CircuitBreaker.ofDefaults("llm-test"),
                    Bulkhead.ofDefaults("llm-test"), Duration.ofSeconds(20), d -> { });
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    CoachTools tools;

    @Autowired
    CoachMemory memory;

    @MockitoBean
    TomTomRoutingClient tomTom;

    @MockitoBean
    OpenMeteoAqiClient openMeteo;

    @BeforeEach
    void setUp() {
        PROVIDER_DOWN.set(false);
        when(openMeteo.getPollutionData(anyDouble(), anyDouble())).thenReturn(new PollutionData(40, 10, 20, 20, 30, 0));
    }

    @Test
    void chat_AnswersFromTheCallersOwnRoutes() throws Exception {
        String token = signup();
        saveRoute(token, "Office walk");

        mvc.perform(chat(token, "{\"conversationId\":\"" + UUID.randomUUID() + "\",\"message\":\"What routes do I have?\","
                        + "\"timeZone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("Office walk")))
                .andExpect(jsonPath("$.answer", containsString("\"lastScore\":40.0")))
                .andExpect(jsonPath("$.toolsUsed", hasSize(1)))
                .andExpect(jsonPath("$.toolsUsed[0]").value("listMyRoutes"))
                .andExpect(jsonPath("$.citations", hasSize(0)));
    }

    @Test
    void chat_ValidationAndAuthErrors() throws Exception {
        mvc.perform(post("/api/v1/coach/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + UUID.randomUUID() + "\",\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());

        String token = signup();
        String id = UUID.randomUUID().toString();
        mvc.perform(chat(token, "{\"conversationId\":\"" + id + "\",\"message\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("message")));
        mvc.perform(chat(token, "{\"conversationId\":\"" + id + "\",\"message\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(chat(token, "{\"message\":\"hi\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("conversationId")));
        mvc.perform(chat(token, "{\"conversationId\":\"not-a-uuid\",\"message\":\"hi\"}"))
                .andExpect(status().isBadRequest());
        // timeZone must be an IANA zone id (ZoneId.of); a bad one gets the standard error body.
        for (String zone : java.util.List.of("Mars/Base", "Europe/Atlantis", "not a zone", "+99:00")) {
            mvc.perform(chat(token, "{\"conversationId\":\"" + id + "\",\"message\":\"hi\",\"timeZone\":\"" + zone + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message", containsString("timeZone must be an IANA time zone")))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    @Test
    void chat_OverTheHourlyLimit_Is429WithRetryAfter() throws Exception {
        String token = signup();
        String body = "{\"conversationId\":\"" + UUID.randomUUID() + "\",\"message\":\"hi\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(chat(token, body)).andExpect(status().isOk());
        }
        mvc.perform(chat(token, body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message", containsString("Coach limit")))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void chat_ProviderDown_Is503_AndTheRestOfTheAppWorks() throws Exception {
        String token = signup();
        PROVIDER_DOWN.set(true);

        mvc.perform(chat(token, "{\"conversationId\":\"" + UUID.randomUUID() + "\",\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                // So the UI can tell "offline" from "took too long" or "today's limit" without reading the message.
                .andExpect(jsonPath("$.code").value("provider_error"));
        mvc.perform(auth(get("/api/v1/routes"), token)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void tools_NeverReachAnotherUsersRoute() throws Exception {
        String alice = signup();
        String bob = signup();
        String bobsRoute = saveRoute(bob, "Bob's secret commute");
        ToolContext asAlice = contextFor(alice);

        Object history = tools.getRouteHistory(bobsRoute, 7, asAlice);
        Object list = tools.listMyRoutes(asAlice);

        // Exactly like the REST API: someone else's route is "not found".
        assertThat(history).isEqualTo(new CoachTools.ToolError("Route not found"));
        assertThat(list).isInstanceOfSatisfying(CoachTools.RouteList.class, l -> {
            assertThat(l.routes()).isEmpty();
            assertThat(l.note()).contains("no saved routes");
        });
        // Bob's own context does see it, with its first reading.
        assertThat(tools.getRouteHistory(bobsRoute, 7, contextFor(bob))).isInstanceOfSatisfying(CoachTools.RouteHistory.class,
                h -> {
                    assertThat(h.readings()).isEqualTo(1);
                    assertThat(h.note()).contains("not reliable yet"); // one reading is not a "best hour"
                });
    }

    @Test
    void tools_RejectInvalidArguments_WithAnErrorTheModelCanReadAndRecoverFrom() throws Exception {
        ToolContext context = contextFor(signup());

        assertThat(error(tools.getRouteHistory("not-a-uuid", 7, context))).contains("routeId");
        assertThat(error(tools.getRouteHistory(null, 7, context))).contains("routeId");
        assertThat(error(tools.getRouteHistory(UUID.randomUUID().toString(), 0, context))).contains("days");
        assertThat(error(tools.getRouteHistory(UUID.randomUUID().toString(), 31, context))).contains("days");
        assertThat(error(tools.getRouteHistory(UUID.randomUUID().toString(), null, context))).contains("days");
        assertThat(error(tools.getCurrentAirQuality(91.0, 0.0, context))).contains("latitude");
        assertThat(error(tools.getCurrentAirQuality(null, 0.0, context))).contains("latitude");
        assertThat(error(tools.getCurrentAirQuality(0.0, -181.0, context))).contains("longitude");
        assertThat(error(tools.getCurrentAirQuality(Double.NaN, 0.0, context))).contains("latitude");
        assertThat(error(tools.searchKnowledgeBase("  ", context))).contains("query");
        assertThat(error(tools.searchKnowledgeBase("x".repeat(301), context))).contains("query");
    }

    @Test
    void tools_SensitivitiesAirQualityAndKnowledgeBase() throws Exception {
        String token = signup();
        mvc.perform(auth(post("/api/v1/auth/preferences"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avoidPm25\":true,\"avoidOzone\":false,\"avoidPm10\":false,\"avoidNo2\":true}"))
                .andExpect(status().isOk());
        ToolContext context = contextFor(token);

        assertThat(tools.getMySensitivities(context)).isEqualTo(new CoachTools.Sensitivities(true, false, true, false));
        assertThat(tools.getCurrentAirQuality(28.6, 77.2, context)).isInstanceOfSatisfying(CoachTools.AirQuality.class, a -> {
            assertThat(a.europeanAqi()).isEqualTo(40.0);
            assertThat(a.band()).isEqualTo("Fair");
            assertThat(a.note()).contains("not measured at street level");
        });
        when(openMeteo.getPollutionData(anyDouble(), anyDouble())).thenReturn(null); // Open-Meteo down
        assertThat(tools.getCurrentAirQuality(28.6, 77.2, context))
                .isEqualTo(new CoachTools.Unavailable("unavailable", "Air-quality data is unavailable right now."));
        // AI is off in tests, so the knowledge base is too, and the tool says so instead of failing.
        assertThat(tools.searchKnowledgeBase("safe PM2.5 level", context)).isInstanceOf(CoachTools.Unavailable.class);
    }

    @Test
    void toolsWithoutAServerSetUser_RefuseToRun() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tools.listMyRoutes(new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingTheAccount_ForgetsCoachHistory() throws Exception {
        String token = signup();
        UUID userId = userId(token);
        UUID conversation = UUID.randomUUID();
        mvc.perform(chat(token, "{\"conversationId\":\"" + conversation + "\",\"message\":\"hi\"}")).andExpect(status().isOk());
        assertThat(memory.get(userId, conversation)).hasSize(2);

        mvc.perform(auth(delete("/api/v1/auth/account"), token)).andExpect(status().isNoContent());

        assertThat(memory.get(userId, conversation)).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private static String error(Object result) {
        assertThat(result).isInstanceOf(CoachTools.ToolError.class);
        return ((CoachTools.ToolError) result).error();
    }

    private ToolContext contextFor(String token) throws Exception {
        return new ToolContext(Map.of(CoachTurn.KEY, new CoachTurn(userId(token), ZoneId.of("UTC"))));
    }

    private UUID userId(String token) throws Exception {
        String me = mvc.perform(auth(get("/api/v1/auth/me"), token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(me, "$.id"));
    }

    private String saveRoute(String token, String name) throws Exception {
        String saved = mvc.perform(auth(post("/api/v1/routes"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"walking\",\"pathCoordinates\":[[77.20,28.60],[77.30,28.60]]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(saved, "$.id");
    }

    private String signup() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coach Tester\",\"email\":\"coach-" + UUID.randomUUID()
                                + "@test.dev\",\"password\":\"Str0ng!Pass123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static MockHttpServletRequestBuilder chat(String token, String json) {
        return auth(post("/api/v1/coach/chat"), token).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
}
