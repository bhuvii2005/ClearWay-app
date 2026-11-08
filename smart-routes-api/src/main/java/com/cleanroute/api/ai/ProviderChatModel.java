package com.cleanroute.api.ai;

import org.springframework.ai.chat.model.ChatModel;

/** The chat model in use, with the provider and model name it talks to (for health, logs and metrics). */
public record ProviderChatModel(AiProperties.Provider provider, String model, ChatModel chatModel) {
}
