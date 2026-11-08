package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real OpenAI-compatible client pointed at a local port where nothing listens, standing in
 * for "provider down". It must give a clear 503 quickly, not hang. No internet is used.
 */
class LlmGatewayRealClientTest {

    @Test
    void providerDown_Answers503WithinTheTimeout() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        AiProperties settings = AiTestSettings.groq("test-key-not-real", "http://127.0.0.1:" + closedPort + "/v1", Duration.ofSeconds(3));
        LlmGateway gateway = new LlmGateway(new ChatModelFactory(settings).create(), new HostedTokenBudget(1000, Clock.systemUTC()),
                CircuitBreaker.ofDefaults("llm"), Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20), d -> { });

        long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.call(new Prompt("hello")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(503));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }
}
