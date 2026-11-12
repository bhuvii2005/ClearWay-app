package com.cleanroute.api.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

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
        @NotNull @Valid KnowledgeBase kb,
        @NotNull @Valid Limits limits) {

    public enum Provider { GROQ, GEMINI }

    public record Chat(
            @NotNull Provider provider,
            @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
            @Min(1) @Max(8192) int maxOutputTokens,
            @NotNull Duration timeout,
            // Sent as reasoning_effort. Blank means the model's default. Gemini 3 Flash takes
            // minimal/low/medium/high (thinking can't be fully off); Groq's gpt-oss takes low/medium/high.
            @Pattern(regexp = "|none|minimal|low|medium|high",
                    message = "must be blank or one of none, minimal, low, medium, high") String reasoningEffort) {

        public Chat {
            requirePositive(timeout, "clearway.ai.chat.timeout");
            reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.strip();
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

    /** Gemini embeddings (always Gemini, whichever chat provider is chosen), with their own daily quota. */
    public record Embedding(
            @NotBlank String model,
            @Min(1) @Max(2000) int dimensions,
            @NotNull Duration timeout,
            @Min(1) long dailyRequestBudget) {

        public Embedding {
            requirePositive(timeout, "clearway.ai.embedding.timeout");
        }
    }

    /** The small knowledge base the Coach searches (Markdown files under classpath:kb/). */
    public record KnowledgeBase(
            boolean ingestOnStartup,
            @Min(1) @Max(20) int topK,
            @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
            @Min(50) @Max(2000) int chunkSizeTokens,
            @Min(1) @Max(100) int batchSize,
            @Min(1) int embedRequestsPerMinute,
            @Min(1) int embedTokensPerMinute) {
    }

    public record Limits(
            @Min(1) int messagesPerHour,
            @Min(1) @Max(20_000) int maxInputChars,
            @Min(1) long dailyRequestBudget,
            @Min(1) long dailyTokenBudget,
            @NotNull ZoneId budgetResetZone,
            @NotNull Duration maxRetryWait,
            // Wall-clock budget for one Coach message, all tool rounds included.
            @NotNull Duration coachTotalTimeout) {

        public Limits {
            requirePositive(maxRetryWait, "clearway.ai.limits.max-retry-wait");
            requirePositive(coachTotalTimeout, "clearway.ai.limits.coach-total-timeout");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be greater than zero, got " + value);
        }
    }
}
