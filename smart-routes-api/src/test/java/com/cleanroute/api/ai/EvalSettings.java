package com.cleanroute.api.ai;

import com.cleanroute.api.support.DotEnv;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * Settings for live evals ({@code ./gradlew evalTest}), read from the environment or .env.
 * Gemini by default. Evals get their own small budget, separate from the app's, and calls are
 * spaced to respect the provider's requests-per-minute limit.
 */
final class EvalSettings {

    final AiProperties.Provider provider;
    final Optional<String> apiKey;
    final int maxRequests;
    final int maxTokens;
    final int requestsPerMinute;

    private EvalSettings() {
        provider = AiProperties.Provider.valueOf(
                DotEnv.get("CLEARWAY_EVAL_PROVIDER").orElse("gemini").trim().toUpperCase(Locale.ROOT));
        apiKey = DotEnv.get(keyName());
        maxRequests = intSetting("CLEARWAY_EVAL_MAX_REQUESTS", 70);
        maxTokens = intSetting("CLEARWAY_EVAL_MAX_TOKENS", 150_000);
        requestsPerMinute = intSetting("CLEARWAY_EVAL_RPM", 5);
    }

    static EvalSettings load() {
        return new EvalSettings();
    }

    String keyName() {
        return provider == AiProperties.Provider.GEMINI ? "GEMINI_API_KEY" : "GROQ_API_KEY";
    }

    /** The eval's own budget; the day rolls over at midnight Pacific like Gemini's quota. */
    HostedAiBudget budget() {
        return new HostedAiBudget(maxRequests, maxTokens, Clock.system(ZoneId.of("America/Los_Angeles")));
    }

    /** A gateway on the real provider, with the eval budget and RPM spacing. */
    LlmGateway gateway(HostedAiBudget budget) {
        String baseUrl = provider == AiProperties.Provider.GEMINI ? AiTestSettings.GEMINI_URL : AiTestSettings.GROQ_URL;
        // The same model the app would use: GEMINI_CHAT_MODEL from .env, else the application.yml default.
        String geminiModel = DotEnv.get("GEMINI_CHAT_MODEL").map(String::trim).filter(s -> !s.isEmpty())
                .orElse(AiTestSettings.GEMINI_CHAT_MODEL);
        // Same reasoning effort as the app: CLEARWAY_AI_REASONING_EFFORT, else "low".
        // "default" sends none, so the model uses its own default (for before/after comparisons).
        String effort = DotEnv.get("CLEARWAY_AI_REASONING_EFFORT").map(String::trim)
                .map(v -> v.equalsIgnoreCase("default") ? "" : v).orElse("low");
        AiProperties settings = AiTestSettings.forProvider(provider, apiKey.orElseThrow(), baseUrl, Duration.ofSeconds(30),
                geminiModel, effort);
        ProviderChatModel real = new ChatModelFactory(settings).create().orElseThrow();
        ProviderChatModel paced = new ProviderChatModel(real.provider(), real.model(),
                new PacedChatModel(real.chatModel(), requestsPerMinute));
        return new LlmGateway(Optional.of(paced), budget, CircuitBreaker.ofDefaults("llm-eval"),
                Bulkhead.ofDefaults("llm-eval"), Duration.ofSeconds(20), d -> Thread.sleep(d.toMillis()));
    }

    private static int intSetting(String name, int fallback) {
        int value = DotEnv.get(name).map(String::trim).map(Integer::parseInt).orElse(fallback);
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1");
        }
        return value;
    }
}
