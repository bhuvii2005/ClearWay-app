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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * When the provider says its per-day quota is used up, the app stops calling it until midnight
 * Pacific: an immediate 503, no retry and no further requests. A per-minute 429 is still retried.
 * Real OpenAI-compatible client against a fake provider on localhost.
 */
class DailyQuotaTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    private final AtomicInteger requests = new AtomicInteger();
    private final List<Duration> waits = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void perDayQuota429_Answers503_ThenNoMoreCallsUntilMidnightPacific() throws IOException {
        MutableClock clock = new MutableClock(ZonedDateTime.of(2026, 11, 11, 9, 0, 0, 0, PACIFIC).toInstant());
        HostedAiBudget budget = new HostedAiBudget("chat", 20, 1_000_000, clock);
        LlmGateway gateway = gateway(budget, round -> round == 1 ? reply(429, GEMINI_PER_DAY_429) : reply(200, ANSWER));

        assertUsedUp(gateway);
        assertThat(requests).as("no retry, even though 429 is normally retried once").hasValue(1);
        assertThat(waits).isEmpty();
        assertThat(budget.exhaustedToday()).isTrue();

        assertUsedUp(gateway);
        assertThat(requests).as("no call while the day's quota is gone").hasValue(1);

        clock.set(ZonedDateTime.of(2026, 11, 11, 23, 59, 0, 0, PACIFIC).toInstant());
        assertUsedUp(gateway);
        assertThat(requests).hasValue(1);

        clock.set(ZonedDateTime.of(2026, 11, 12, 0, 0, 1, 0, PACIFIC).toInstant());
        assertThat(gateway.call(new Prompt("hi")).getResult().getOutput().getText()).isEqualTo("ok");
        assertThat(requests).hasValue(2);
    }

    @Test
    void perMinute429_IsRetriedOnce_AndDoesNotEndTheDay() throws IOException {
        HostedAiBudget budget = new HostedAiBudget("chat", 20, 1_000_000, Clock.system(PACIFIC));
        LlmGateway gateway = gateway(budget, round -> round == 1 ? reply(429, GEMINI_PER_MINUTE_429) : reply(200, ANSWER));

        assertThat(gateway.call(new Prompt("hi")).getResult().getOutput().getText()).isEqualTo("ok");
        assertThat(requests).hasValue(2);
        // No Retry-After header on Gemini's OpenAI endpoint: the wait comes from the body ("retry in 6.2s").
        assertThat(waits).containsExactly(Duration.ofMillis(6_200));
        assertThat(budget.exhaustedToday()).isFalse();
    }

    @Test
    void dailyQuotaWording_FromGeminiAndGroq() {
        assertThat(ProviderFailure.mentionsDailyQuota(GEMINI_PER_DAY_429)).isTrue();
        assertThat(ProviderFailure.mentionsDailyQuota(
                "Rate limit reached for model `openai/gpt-oss-20b` on requests per day (RPD): Limit 1000, Used 1000")).isTrue();
        assertThat(ProviderFailure.mentionsDailyQuota(
                "Rate limit reached for model `openai/gpt-oss-20b` on tokens per minute (TPM): Limit 8000")).isFalse();
        assertThat(ProviderFailure.mentionsDailyQuota(GEMINI_PER_MINUTE_429)).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    private void assertUsedUp(LlmGateway gateway) {
        assertThatThrownBy(() -> gateway.call(new Prompt("hi")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessage(HostedAiBudget.USED_UP);
    }

    private record Reply(int status, String body) {
    }

    private static Reply reply(int status, String body) {
        return new Reply(status, body);
    }

    private LlmGateway gateway(HostedAiBudget budget, java.util.function.IntFunction<Reply> script) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            Reply reply = script.apply(requests.incrementAndGet());
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        AiProperties settings = AiTestSettings.forProvider(AiProperties.Provider.GEMINI, "test-key-not-real", baseUrl,
                Duration.ofSeconds(5));
        return new LlmGateway(new ChatModelFactory(settings).create(), budget, CircuitBreaker.ofDefaults("llm"),
                Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20), waits::add);
    }

    /** Clock whose time the test moves; the zone decides when the day rolls over. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return PACIFIC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** The shape Gemini's OpenAI-compatible endpoint returned on 11 Nov 2026 (shortened). */
    private static final String GEMINI_PER_DAY_429 = """
            [{
              "error": {
                "code": 429,
                "message": "You exceeded your current quota, please check your plan and billing details.\\n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-3.6-flash, description: Generate content requests per day limit exceeded on Free Tier.\\nPlease retry in 12h4m27.18s.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {"@type": "type.googleapis.com/google.rpc.QuotaFailure",
                   "violations": [{"quotaMetric": "generativelanguage.googleapis.com/generate_content_free_tier_requests",
                                   "quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier",
                                   "quotaValue": "20"}]},
                  {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "43467s"}
                ]
              }
            }]
            """;

    private static final String GEMINI_PER_MINUTE_429 = """
            [{
              "error": {
                "code": 429,
                "message": "You exceeded your current quota.\\n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 5, model: gemini-3.5-flash-lite\\nPlease retry in 6.2s.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {"@type": "type.googleapis.com/google.rpc.QuotaFailure",
                   "violations": [{"quotaId": "GenerateRequestsPerMinutePerProjectPerModel-FreeTier", "quotaValue": "5"}]},
                  {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "7s"}
                ]
              }
            }]
            """;

    private static final String ANSWER = """
            {"id":"r1","object":"chat.completion","created":1,"model":"fake",
             "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"ok"}}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;
}
