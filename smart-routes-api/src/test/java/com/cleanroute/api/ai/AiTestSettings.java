package com.cleanroute.api.ai;

import java.time.Duration;
import java.time.ZoneId;

/** Builds AiProperties in code for tests, with the same values as application.yml. */
final class AiTestSettings {

    static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/";
    static final String GROQ_URL = "https://api.groq.com/openai/v1";
    /** Same default as application.yml. */
    static final String GEMINI_CHAT_MODEL = "gemini-3.5-flash";

    private AiTestSettings() {
    }

    /** The chosen provider gets this key and base URL; the other one has no key. */
    static AiProperties forProvider(AiProperties.Provider provider, String apiKey, String baseUrl, Duration timeout) {
        return forProvider(provider, apiKey, baseUrl, timeout, GEMINI_CHAT_MODEL);
    }

    static AiProperties forProvider(AiProperties.Provider provider, String apiKey, String baseUrl, Duration timeout,
                                    String geminiChatModel) {
        return forProvider(provider, apiKey, baseUrl, timeout, geminiChatModel, "low");
    }

    static AiProperties forProvider(AiProperties.Provider provider, String apiKey, String baseUrl, Duration timeout,
                                    String geminiChatModel, String reasoningEffort) {
        boolean gemini = provider == AiProperties.Provider.GEMINI;
        return new AiProperties(true,
                new AiProperties.Chat(provider, 0.2, 800, timeout, reasoningEffort),
                new AiProperties.Providers(
                        new AiProperties.ProviderSettings(gemini ? null : apiKey, gemini ? GROQ_URL : baseUrl, "openai/gpt-oss-20b"),
                        new AiProperties.ProviderSettings(gemini ? apiKey : null, gemini ? baseUrl : GEMINI_URL, geminiChatModel)),
                embedding(),
                knowledgeBase(0.6, 20, 30),
                limits());
    }

    /** No keys at all, with the given knowledge-base settings (for tests that use fake models). */
    static AiProperties withKnowledgeBase(double threshold, int batchSize, int embedRpm) {
        return new AiProperties(true,
                new AiProperties.Chat(AiProperties.Provider.GEMINI, 0.2, 800, Duration.ofSeconds(30), "low"),
                new AiProperties.Providers(
                        new AiProperties.ProviderSettings(null, GROQ_URL, "openai/gpt-oss-20b"),
                        new AiProperties.ProviderSettings(null, GEMINI_URL, GEMINI_CHAT_MODEL)),
                embedding(),
                knowledgeBase(threshold, batchSize, embedRpm),
                limits());
    }

    private static AiProperties.Embedding embedding() {
        return new AiProperties.Embedding("gemini-embedding-001", 768, Duration.ofSeconds(20), 500);
    }

    private static AiProperties.KnowledgeBase knowledgeBase(double threshold, int batchSize, int embedRpm) {
        return new AiProperties.KnowledgeBase(true, 4, threshold, 300, batchSize, embedRpm);
    }

    private static AiProperties.Limits limits() {
        return new AiProperties.Limits(20, 2000, 200, 300_000, ZoneId.of("America/Los_Angeles"), Duration.ofSeconds(20),
                Duration.ofSeconds(45));
    }
}
