package com.cleanroute.api.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Builds the chat model for the provider chosen in {@code clearway.ai.chat.provider}.
 * Groq and Gemini are both reached through Spring AI's OpenAI module with their own
 * OpenAI-compatible base URL, so adding another compatible provider later is only config.
 * Returns empty (Coach off, 503) when AI is disabled or the chosen provider has no key.
 */
@Component
public class ChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);

    private final AiProperties properties;

    public ChatModelFactory(AiProperties properties) {
        this.properties = properties;
    }

    public Optional<ProviderChatModel> create() {
        if (!properties.enabled()) {
            log.info("AI features are turned off (clearway.ai.enabled=false); the Coach will answer 503.");
            return Optional.empty();
        }
        AiProperties.Provider provider = properties.chat().provider();
        AiProperties.ProviderSettings settings = properties.providers().get(provider);
        if (!settings.hasKey()) {
            log.warn("No API key for the {} chat provider (set {}_API_KEY in .env). The Coach will answer 503; "
                    + "the rest of the app works normally.", provider, provider);
            return Optional.empty();
        }
        String reasoningEffort = properties.chat().reasoningEffort();
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (!reasoningEffort.isEmpty()) {
            // Less thinking means faster replies and fewer tokens; the Coach's work is simple lookups.
            builder.reasoningEffort(reasoningEffort);
        }
        OpenAiChatOptions options = builder
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .model(settings.chatModel())
                .temperature(properties.chat().temperature())
                .timeout(properties.chat().timeout())
                // LlmGateway owns retries, so the SDK must not add its own on top.
                .maxRetries(0)
                .maxTokens(properties.chat().maxOutputTokens())
                .build();
        log.info("Coach chat provider: {} ({}, reasoning effort {})", provider, settings.chatModel(),
                reasoningEffort.isEmpty() ? "model default" : reasoningEffort);
        return Optional.of(new ProviderChatModel(provider, settings.chatModel(),
                OpenAiChatModel.builder().options(options).build()));
    }
}
