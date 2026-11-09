package com.cleanroute.api.ai;

import com.openai.core.http.Headers;
import com.openai.errors.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFailureTest {

    @Test
    void readsStatusAndRetryAfterFromTheOpenAiSdkError() {
        RateLimitException sdkError = RateLimitException.builder()
                .headers(Headers.builder().put("retry-after", "2.5").build())
                .build();

        // Wrapped, the way a framework might rethrow it.
        ProviderFailure failure = ProviderFailure.of(new RuntimeException("wrapped", sdkError));

        assertThat(failure.status()).isEqualTo(429);
        assertThat(failure.retryable()).isTrue();
        assertThat(failure.retryAfter()).contains(Duration.ofMillis(2500));
    }

    @Test
    void unknownErrorsAreTreatedAsNotRetryable() {
        ProviderFailure failure = ProviderFailure.of(new IllegalStateException("boom"));
        assertThat(failure.status()).isZero();
        assertThat(failure.retryable()).isFalse();
    }

    @Test
    void retryAfterIgnoresDatesAndNegativeValues() {
        assertThat(ProviderFailure.retryAfter(List.of("Wed, 21 Oct 2026 07:28:00 GMT"))).isEqualTo(Optional.empty());
        assertThat(ProviderFailure.retryAfter(List.of("-1"))).isEqualTo(Optional.empty());
        assertThat(ProviderFailure.retryAfter(List.of(" 7 "))).contains(Duration.ofSeconds(7));
    }

    @Test
    void readsStatusAndRetryDelayFromGeminiSdkErrors() {
        var quota = new com.google.genai.errors.ClientException(429, "RESOURCE_EXHAUSTED",
                "You exceeded your current quota. Please retry in 17.5s.");
        ProviderFailure failure = ProviderFailure.of(new RuntimeException("wrapped", quota));

        assertThat(failure.rateLimited()).isTrue();
        assertThat(failure.retryAfter()).contains(Duration.ofMillis(17_500));
        assertThat(ProviderFailure.geminiRetryDelay("{\"retryDelay\": \"9s\"}")).contains(Duration.ofSeconds(9));
        assertThat(ProviderFailure.of(new com.google.genai.errors.ServerException(503, "UNAVAILABLE", "overloaded")).retryable()).isTrue();
        assertThat(ProviderFailure.of(new com.google.genai.errors.GenAiIOException("timeout")).status()).isZero();
    }
}
