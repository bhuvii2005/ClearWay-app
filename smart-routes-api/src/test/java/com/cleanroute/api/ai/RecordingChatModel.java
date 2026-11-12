package com.cleanroute.api.ai;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * For evals: passes calls through unchanged and keeps the tool results the model was shown in the
 * current turn (the last prompt of a turn carries all of them), for grounding and citation checks.
 */
final class RecordingChatModel implements ChatModel {

    record ToolOutput(String tool, String data) {
    }

    private final ChatModel delegate;
    private List<ToolOutput> lastTurn = List.of();

    RecordingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<ToolOutput> outputs = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            if (message instanceof ToolResponseMessage tool) {
                tool.getResponses().forEach(r -> outputs.add(new ToolOutput(r.name(), r.responseData())));
            }
        }
        synchronized (this) {
            lastTurn = List.copyOf(outputs);
        }
        return delegate.call(prompt);
    }

    /** Call before each eval case. */
    synchronized void reset() {
        lastTurn = List.of();
    }

    synchronized List<ToolOutput> toolOutputs() {
        return lastTurn;
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }
}
