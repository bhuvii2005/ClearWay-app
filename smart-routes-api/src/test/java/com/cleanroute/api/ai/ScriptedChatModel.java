package com.cleanroute.api.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * An offline chat model driven by a script: for each call it sees the prompt and returns either a
 * tool call or a final answer. Records every prompt it received. Nothing leaves the machine.
 */
final class ScriptedChatModel implements ChatModel {

    final List<Prompt> prompts = new ArrayList<>();
    private final Function<Prompt, ChatResponse> script;
    private final AtomicInteger ids = new AtomicInteger();

    ScriptedChatModel(Function<Prompt, ChatResponse> script) {
        this.script = script;
    }

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return script.apply(prompt);
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    ChatResponse toolCall(String name, String argumentsJson) {
        AssistantMessage message = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call_" + ids.incrementAndGet(), "function", name, argumentsJson)))
                .build();
        return response(message);
    }

    static ChatResponse answer(String text) {
        return response(new AssistantMessage(text));
    }

    private static ChatResponse response(AssistantMessage message) {
        return ChatResponse.builder().generations(List.of(new Generation(message)))
                .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).build())
                .build();
    }

    /** How many tool results the prompt already holds (the tool rounds done so far in this message). */
    static long toolResults(Prompt prompt) {
        return prompt.getInstructions().stream().filter(m -> m instanceof ToolResponseMessage).count();
    }

    /** The text of the last tool result in the prompt, or "" if there is none. */
    static String lastToolResult(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ToolResponseMessage r && !r.getResponses().isEmpty()) {
                return r.getResponses().get(r.getResponses().size() - 1).responseData();
            }
        }
        return "";
    }
}
