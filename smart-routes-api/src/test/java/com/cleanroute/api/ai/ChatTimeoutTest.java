package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A model that never answers (as gemini-3.8-flash did on the free tier) must give a 503 after the
 * configured chat timeout, not hang. Uses the real OpenAI SDK against a local server that accepts
 * the request and never replies: no traffic leaves the machine.
 */
class ChatTimeoutTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;

    @AfterEach
    void stop() {
        release.countDown();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void hungModel_Answers503AfterTheTimeout_WithoutRetrying() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try {
                release.await(30, TimeUnit.SECONDS); // never reply while the test runs
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";

        AiProperties settings = AiTestSettings.forProvider(AiProperties.Provider.GEMINI, "test-key-not-real", baseUrl,
                Duration.ofSeconds(1));
        ProviderChatModel model = new ChatModelFactory(settings).create().orElseThrow();
        List<Duration> waits = new ArrayList<>();
        LlmGateway gateway = new LlmGateway(Optional.of(model), new HostedAiBudget(10, 10_000, Clock.systemUTC()),
                CircuitBreaker.ofDefaults("llm"), Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20), waits::add);

        long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.call(new Prompt("hello")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(took).isGreaterThanOrEqualTo(Duration.ofMillis(900)).isLessThan(Duration.ofSeconds(5));
        assertThat(requests).hasValue(1); // a timeout is not retried, so the wait is one timeout, not two
        assertThat(waits).isEmpty();
    }
}
