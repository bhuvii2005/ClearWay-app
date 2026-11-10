package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The Coach's total time budget per message (clearway.ai.limits.coach-total-timeout), with the
 * real OpenAI-compatible client against a slow fake provider on localhost. Each call's own
 * timeout is 30 s here; the budget must still end the message in about the budget, not 5 x 30 s.
 */
class CoachTimeBudgetTest {

    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private CircuitBreaker breaker;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void slowFinalAnswer_IsCutToTheTimeLeft_AndAnswers503() throws IOException {
        // Round 1 asks for a tool at once; round 2 would take 20 s.
        CoachService coach = coach(round -> round == 1 ? reply(0, TOOL_CALL) : reply(20_000, FINAL_ANSWER),
                Duration.ofSeconds(2), "low");

        long start = System.nanoTime();
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> coach.chat(UUID.randomUUID(), UUID.randomUUID(), "who am I?", "UTC"));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(error.getMessage()).isEqualTo(AiDeadline.OUT_OF_TIME);
        assertThat(millis).as("ends near the 2 s budget, not after the 30 s call timeout").isLessThan(5_000);
        assertThat(requestBodies).hasSize(2);
        // Our budget ran out; the provider didn't fail, so the breaker doesn't count it.
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void noTimeLeftAfterATool_TheNextRoundIsNeverSent() throws IOException {
        // Round 1 takes 1.5 s of the 2 s budget, leaving less than the 1 s minimum for round 2.
        CoachService coach = coach(round -> round == 1 ? reply(1_500, TOOL_CALL) : reply(0, FINAL_ANSWER),
                Duration.ofSeconds(2), "low");

        ApiException error = catchThrowableOfType(ApiException.class,
                () -> coach.chat(UUID.randomUUID(), UUID.randomUUID(), "who am I?", "UTC"));

        assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(error.getMessage()).isEqualTo(AiDeadline.OUT_OF_TIME);
        assertThat(requestBodies).hasSize(1);
    }

    @Test
    void withinTheBudget_AnswersNormally_AndSendsTheReasoningEffort() throws IOException {
        CoachService coach = coach(round -> round == 1 ? reply(0, TOOL_CALL) : reply(0, FINAL_ANSWER),
                Duration.ofSeconds(10), "low");

        CoachService.CoachReply reply = coach.chat(UUID.randomUUID(), UUID.randomUUID(), "who am I?", "UTC");

        assertThat(reply.answer()).isEqualTo("You are signed in.");
        assertThat(requestBodies.get(1)).contains("\"role\":\"tool\"");
        assertThat(requestBodies).hasSize(2).allSatisfy(body -> assertThat(body).contains("\"reasoning_effort\":\"low\""));
    }

    @Test
    void blankReasoningEffort_SendsNone_SoTheModelDefaultApplies() throws IOException {
        CoachService coach = coach(round -> round == 1 ? reply(0, TOOL_CALL) : reply(0, FINAL_ANSWER),
                Duration.ofSeconds(10), "");

        coach.chat(UUID.randomUUID(), UUID.randomUUID(), "who am I?", "UTC");

        assertThat(requestBodies).hasSize(2).noneSatisfy(body -> assertThat(body).contains("reasoning_effort"));
    }

    // ------------------------------------------------------------------ helpers

    private record Reply(long delayMillis, String body) {
    }

    private static Reply reply(long delayMillis, String body) {
        return new Reply(delayMillis, body);
    }

    /** A Coach on the real client, talking to a fake provider whose replies come from {@code script} (round 1, 2, ...). */
    private CoachService coach(IntFunction<Reply> script, Duration totalTimeout, String reasoningEffort) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Reply reply = script.apply(requestBodies.size());
            try {
                Thread.sleep(reply.delayMillis());
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (InterruptedException | IOException e) {
                exchange.close(); // the client gave up
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        AiProperties settings = AiTestSettings.forProvider(AiProperties.Provider.GEMINI, "test-key-not-real", baseUrl,
                Duration.ofSeconds(30), AiTestSettings.GEMINI_CHAT_MODEL, reasoningEffort);
        breaker = CircuitBreaker.ofDefaults("llm");
        LlmGateway gateway = new LlmGateway(new ChatModelFactory(settings).create(),
                new HostedAiBudget(10, 10_000, Clock.systemUTC()), breaker, Bulkhead.ofDefaults("llm"),
                Duration.ofSeconds(20), d -> { });
        return new CoachService(gateway, new CoachRateLimiter(100, Clock.systemUTC()), new CoachMemory(),
                new Object[] {new CoachWireTest.WhoAmI()}, 2000, totalTimeout, ObservationRegistry.NOOP,
                CoachServiceTest.systemTemplate(), Clock.systemUTC());
    }

    private static final String TOOL_CALL = """
            {"id":"r1","object":"chat.completion","created":1,"model":"fake",
             "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
               "tool_calls":[{"id":"call_1","type":"function","function":{"name":"whoAmI","arguments":"{}"}}]}}],
             "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
            """;

    private static final String FINAL_ANSWER = """
            {"id":"r2","object":"chat.completion","created":2,"model":"fake",
             "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"You are signed in."}}],
             "usage":{"prompt_tokens":15,"completion_tokens":5,"total_tokens":20}}
            """;
}
