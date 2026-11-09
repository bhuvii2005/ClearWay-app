package com.cleanroute.api.ai;

import java.time.Duration;
import java.time.ZoneId;

/** Builds AiProperties in code for tests, with the same values as application.yml. */
final class AiTestSettings {

    static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/";
    static final String GROQ_URL = "https://api.groq.com/openai/v1";

    private AiTestSettings() {
    }

    /** The chosen provider gets this key and base URL; the other one has no key. */
    static AiProperties forProvider(AiProperties.Provider provider, String apiKey, String baseUrl, Duration timeout) {
        boolean gemini = provider == AiProperties.Provider.GEMINI;
        return new AiProperties(true,
                new AiProperties.Chat(provider, 0.2, 800, timeout),
                new AiProperties.Providers(
                        new AiProperties.ProviderSettings(gemini ? null : apiKey, gemini ? GROQ_URL : baseUrl, "openai/gpt-oss-20b"),
                        new AiProperties.ProviderSettings(gemini ? apiKey : null, gemini ? baseUrl : GEMINI_URL, "gemini-3.8-flash")),
                new AiProperties.Embedding("gemini-embedding-001", 768),
                new AiProperties.Limits(20, 2000, 200, 300_000, ZoneId.of("America/Los_Angeles"), Duration.ofSeconds(20)));
    }
}
