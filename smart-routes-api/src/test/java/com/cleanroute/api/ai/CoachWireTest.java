package com.cleanroute.api.ai;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real OpenAI-compatible client (as used for Gemini and Groq) inside the Coach's ChatClient,
 * talking to a fake provider on localhost. Checks what actually goes over the wire: the tool
 * definitions, the tool result sent back, and that each round is one gateway request.
 */
class CoachWireTest {

    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** A tool that reports the user the server put in the ToolContext. */
    static class WhoAmI {
        @Tool(name = "whoAmI", description = "Who is signed in")
        public Map<String, String> whoAmI(ToolContext context) {
            return Map.of("user", ((CoachTurn) context.getContext().get(CoachTurn.KEY)).userId().toString());
        }
    }

    @Test
    void toolRoundTrip_OverTheOpenAiProtocol() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(body);
            String reply = requestBodies.size() == 1 ? TOOL_CALL : FINAL_ANSWER;
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        AiProperties settings = AiTestSettings.forProvider(AiProperties.Provider.GEMINI, "test-key-not-real", baseUrl,
                Duration.ofSeconds(5));
        LlmGateway gateway = new LlmGateway(new ChatModelFactory(settings).create(), new HostedAiBudget(10, 10_000, Clock.systemUTC()),
                CircuitBreaker.ofDefaults("llm"), Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20), d -> { });
        UUID user = UUID.randomUUID();

        String answer;
        try (AiUsageTally tally = AiUsageTally.open("wire test")) {
            answer = CoachService.chatClient(gateway, ObservationRegistry.NOOP).prompt()
                    .user("who am I?")
                    .tools(new WhoAmI())
                    .toolContext(Map.of(CoachTurn.KEY, new CoachTurn(user, java.time.ZoneId.of("UTC"))))
                    .call().content();
            assertThat(tally.chatRequests()).isEqualTo(2);
            assertThat(tally.chatTokens()).isEqualTo(15 + 20);
            // The model the provider reported, not the configured name.
            assertThat(tally.servedModels()).containsExactly("fake");
        }

        assertThat(answer).isEqualTo("You are signed in.");
        assertThat(requestBodies).hasSize(2);
        // Round 1 offers the tool, with no parameters at all (the user is never an argument).
        assertThat(requestBodies.get(0)).contains("\"tools\"").contains("\"name\":\"whoAmI\"")
                .doesNotContainIgnoringCase("userId").doesNotContain(CoachTurn.KEY);
        // Round 2 carries the tool result, which holds the id the server supplied.
        assertThat(requestBodies.get(1)).contains("\"role\":\"tool\"").contains("call_1").contains(user.toString());
    }

    @Test
    void modelCalls_AreObserved_ForSpringAisDurationAndTokenMetrics() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = FINAL_ANSWER.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        AiProperties settings = AiTestSettings.forProvider(AiProperties.Provider.GEMINI, "test-key-not-real", baseUrl,
                Duration.ofSeconds(5));
        List<String> observed = new CopyOnWriteArrayList<>();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new io.micrometer.observation.ObservationHandler<>() {
            @Override
            public boolean supportsContext(io.micrometer.observation.Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(io.micrometer.observation.Observation.Context context) {
                observed.add(context.getName());
            }
        });
        ProviderChatModel model = new ChatModelFactory(settings, new org.springframework.beans.factory.support
                .StaticListableBeanFactory(Map.of("registry", registry)).getBeanProvider(ObservationRegistry.class))
                .create().orElseThrow();

        model.chatModel().call(new org.springframework.ai.chat.prompt.Prompt("hi"));

        // gen_ai.client.operation is the observation Spring AI turns into the duration and token metrics.
        assertThat(observed).contains("gen_ai.client.operation");
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
