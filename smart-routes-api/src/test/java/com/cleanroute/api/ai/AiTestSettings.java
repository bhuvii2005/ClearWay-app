package com.cleanroute.api.ai;

import java.time.Duration;

/** Builds AiProperties in code for tests, with the same values as application.yml. */
final class AiTestSettings {

    private AiTestSettings() {
    }

    static AiProperties groq(String apiKey, String baseUrl, Duration timeout) {
        return new AiProperties(true,
                new AiProperties.Chat(AiProperties.Provider.GROQ, 0.2, 800, timeout),
                new AiProperties.Providers(
                        new AiProperties.ProviderSettings(apiKey, baseUrl, "openai/gpt-oss-20b"),
                        new AiProperties.ProviderSettings(null, "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-3.8-flash")),
                new AiProperties.Embedding("gemini-embedding-001", 768),
                new AiProperties.Limits(20, 2000, 150_000, Duration.ofSeconds(20)));
    }
}
