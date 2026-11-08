package com.cleanroute.api.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for ClearWay's AI features ({@code clearway.ai.*} in application.yml).
 * Checked at start-up: nonsense values stop the app with a clear message. A missing API key
 * is not nonsense; it only turns the Coach off (it then answers 503).
 */
@Validated
@ConfigurationProperties("clearway.ai")
public record AiProperties(
        boolean enabled,
        @NotNull @Valid Chat chat,
        @NotNull @Valid Providers providers,
        @NotNull @Valid Embedding embedding,
        @NotNull @Valid Limits limits) {

    public enum Provider { GROQ, GEMINI }

    public record Chat(
            @NotNull Provider provider,
            @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
            @Min(1) @Max(8192) int maxOutputTokens,
            @NotNull Duration timeout) {

        public Chat {
            requirePositive(timeout, "clearway.ai.chat.timeout");
        }
    }

    public record Providers(@NotNull @Valid ProviderSettings groq, @NotNull @Valid ProviderSettings gemini) {

        public ProviderSettings get(Provider provider) {
            return provider == Provider.GROQ ? groq : gemini;
        }
    }

    /** One hosted provider, reached through its OpenAI-compatible endpoint. */
    public record ProviderSettings(String apiKey, @NotBlank String baseUrl, @NotBlank String chatModel) {

        /** True only for a real-looking key: blank values and .env.example placeholders don't count. */
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("change_me");
        }

        /** Keeps the key out of logs and error messages. */
        @Override
        public String toString() {
            return "ProviderSettings[baseUrl=" + baseUrl + ", chatModel=" + chatModel + ", key=" + (hasKey() ? "set" : "missing") + "]";
        }
    }

    public record Embedding(@NotBlank String model, @Min(1) @Max(2000) int dimensions) {
    }

    public record Limits(
            @Min(1) int messagesPerHour,
            @Min(1) @Max(20_000) int maxInputChars,
            @Min(1) long dailyTokenBudget,
            @NotNull Duration maxRetryWait) {

        public Limits {
            requirePositive(maxRetryWait, "clearway.ai.limits.max-retry-wait");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be greater than zero, got " + value);
        }
    }
}
