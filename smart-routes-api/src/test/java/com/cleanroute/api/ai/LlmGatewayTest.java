package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gateway behaviour with a fake chat model: nothing here calls a real provider. */
class LlmGatewayTest {

    private static final Prompt PROMPT = new Prompt("hello");

    private final List<Duration> waits = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final HostedAiBudget budget = new HostedAiBudget(100, 1_000, Clock.systemUTC());

    @Test
    void notConfigured_Answers503() {
        LlmGateway gateway = new LlmGateway(Optional.empty(), budget, breaker(), bulkhead(4), Duration.ofSeconds(20), waits::add);

        assertThatThrownBy(() -> gateway.call(PROMPT))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessageContaining("offline");
        assertThat(gateway.isConfigured()).isFalse();
    }

    @Test
    void success_ReturnsAnswerAndCountsTokens() {
        LlmGateway gateway = gateway(prompt -> reply("hi", 30));

        assertThat(gateway.call(PROMPT).getResult().getOutput().getText()).isEqualTo("hi");
        assertThat(budget.tokensToday()).isEqualTo(30);
        assertThat(budget.requestsToday()).isEqualTo(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void usageTally_AddsUpRequestsAndTokensAcrossCallsOfOneMessage() {
        LlmGateway gateway = gateway(prompt -> calls.incrementAndGet() == 2 ? throwStatus(429, "1") : reply("ok", 10));

        try (AiUsageTally tally = AiUsageTally.open("test message")) {
            gateway.call(PROMPT);           // 1 request, 10 tokens
            gateway.call(PROMPT);           // 429 then retry: 2 requests, 10 tokens
            assertThat(tally.chatRequests()).isEqualTo(3);
            assertThat(tally.chatTokens()).isEqualTo(20);
            assertThat(tally.embeddingRequests()).isZero();
        }
        assertThat(budget.requestsToday()).isEqualTo(3);

        // Outside a tally the gateway still works, and nothing is left on the thread.
        gateway.call(PROMPT);
        try (AiUsageTally next = AiUsageTally.open("next message")) {
            assertThat(next.chatRequests()).isZero();
        }
    }

    @Test
    void rateLimited_NeverTripsTheCircuitBreaker() {
        LlmGateway gateway = gateway(prompt -> {
            calls.incrementAndGet();
            return throwStatus(429, "1");
        });
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> gateway.call(PROMPT)).isInstanceOf(ApiException.class).hasMessageContaining("free AI limit");
        }
        assertThat(gateway.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(calls).hasValue(12); // every message still reached the provider: once plus one retry
    }

    @Test
    void rateLimited_RetriesOnceAfterRetryAfter() {
        LlmGateway gateway = gateway(prompt -> calls.incrementAndGet() == 1 ? throwStatus(429, "3") : reply("ok", 5));

        assertThat(gateway.call(PROMPT).getResult().getOutput().getText()).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        assertThat(waits).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void rateLimitedTwice_Answers503AfterExactlyTwoCalls() {
        LlmGateway gateway = gateway(prompt -> {
            calls.incrementAndGet();
            return throwStatus(429, "1");
        });

        assertThatThrownBy(() -> gateway.call(PROMPT))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessageContaining("free AI limit");
        assertThat(calls).hasValue(2);
    }

    @Test
    void retryAfterLongerThanCap_DoesNotWait() {
        LlmGateway gateway = gateway(prompt -> {
            calls.incrementAndGet();
            return throwStatus(429, "120");
        });

        assertThatThrownBy(() -> gateway.call(PROMPT)).isInstanceOf(ApiException.class);
        assertThat(calls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void serverError_RetriesOnceWithBackoff() {
        LlmGateway gateway = gateway(prompt -> calls.incrementAndGet() == 1 ? throwStatus(502, null) : reply("ok", 5));

        assertThat(gateway.call(PROMPT).getResult().getOutput().getText()).isEqualTo("ok");
        assertThat(waits).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void clientErrorAndNetworkError_AreNotRetried() {
        LlmGateway badRequest = gateway(prompt -> {
            calls.incrementAndGet();
            return throwStatus(400, null);
        });
        assertThatThrownBy(() -> badRequest.call(PROMPT)).isInstanceOf(ApiException.class).hasMessageContaining("offline");

        LlmGateway timeout = gateway(prompt -> {
            calls.incrementAndGet();
            throw new ResourceAccessException("Read timed out");
        });
        assertThatThrownBy(() -> timeout.call(PROMPT)).isInstanceOf(ApiException.class);

        assertThat(calls).hasValue(2);
        assertThat(waits).isEmpty();
    }

    @Test
    void circuitOpensAfterRepeatedFailures_ThenFailsFastWithoutCallingProvider() {
        LlmGateway gateway = gateway(prompt -> {
            calls.incrementAndGet();
            return throwStatus(400, null);
        });
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> gateway.call(PROMPT)).isInstanceOf(ApiException.class);
        }
        assertThat(gateway.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> gateway.call(PROMPT)).isInstanceOf(ApiException.class).hasMessageContaining("for a moment");
        assertThat(calls).hasValue(4);
    }

    @Test
    void bulkheadFull_Answers503Busy() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ChatModel slow = prompt -> {
            inside.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return reply("late", 1);
        };
        LlmGateway gateway = new LlmGateway(model(slow), budget, breaker(), bulkhead(1), Duration.ofSeconds(20), waits::add);

        CompletableFuture<ChatResponse> first = CompletableFuture.supplyAsync(() -> gateway.call(PROMPT));
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(() -> gateway.call(PROMPT)).isInstanceOf(ApiException.class).hasMessageContaining("busy");
        } finally {
            release.countDown();
        }
        assertThat(first.get(5, TimeUnit.SECONDS).getResult().getOutput().getText()).isEqualTo("late");
    }

    @Test
    void budgetUsedUp_Answers503WithoutCallingProvider() {
        budget.recordTokens(1_000);
        LlmGateway gateway = gateway(prompt -> {
            calls.incrementAndGet();
            return reply("never", 1);
        });

        assertThatThrownBy(() -> gateway.call(PROMPT)).isInstanceOf(ApiException.class).hasMessageContaining("allowance");
        assertThat(calls).hasValue(0);
    }

    @Test
    void requestBudgetUsedUp_Answers503WithoutCallingProvider() {
        HostedAiBudget twoRequests = new HostedAiBudget(2, 1_000_000, Clock.systemUTC());
        LlmGateway gateway = new LlmGateway(model(prompt -> {
            calls.incrementAndGet();
            return reply("ok", 1);
        }), twoRequests, breaker(), bulkhead(4), Duration.ofSeconds(20), waits::add);

        gateway.call(PROMPT);
        gateway.call(PROMPT);
        assertThatThrownBy(() -> gateway.call(PROMPT)).isInstanceOf(ApiException.class).hasMessageContaining("allowance");
        assertThat(calls).hasValue(2);
    }

    @Test
    void streaming_IsNotOffered() {
        LlmGateway gateway = gateway(prompt -> reply("x", 1));
        assertThatThrownBy(() -> gateway.stream(PROMPT).blockFirst()).isInstanceOf(UnsupportedOperationException.class);
    }

    // ------------------------------------------------------------------ helpers

    private LlmGateway gateway(ChatModel fake) {
        return new LlmGateway(model(fake), budget, breaker(), bulkhead(4), Duration.ofSeconds(20), waits::add);
    }

    private static Optional<ProviderChatModel> model(ChatModel fake) {
        return Optional.of(new ProviderChatModel(AiProperties.Provider.GEMINI, "test-model", fake));
    }

    private static CircuitBreaker breaker() {
        return CircuitBreaker.of("llm", CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1)).build());
    }

    private static Bulkhead bulkhead(int maxConcurrent) {
        return Bulkhead.of("llm", BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrent).maxWaitDuration(Duration.ZERO).build());
    }

    private static ChatResponse reply(String text, int totalTokens) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(totalTokens, 0, totalTokens)).build());
    }

    private static ChatResponse throwStatus(int status, String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) {
            headers.add("Retry-After", retryAfter);
        }
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        if (status >= 500) {
            throw HttpServerErrorException.create(httpStatus, "error", headers, new byte[0], StandardCharsets.UTF_8);
        }
        throw HttpClientErrorException.create(httpStatus, "error", headers, new byte[0], StandardCharsets.UTF_8);
    }
}
