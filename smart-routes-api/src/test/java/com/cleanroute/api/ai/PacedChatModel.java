package com.cleanroute.api.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;

/** For live evals: spaces calls at least 60s / RPM apart so the free per-minute limit is never hit. */
final class PacedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final long gapNanos;
    private long lastCall;
    private boolean called;
    private long waitedNanos;

    PacedChatModel(ChatModel delegate, int requestsPerMinute) {
        this.delegate = delegate;
        this.gapNanos = Duration.ofMinutes(1).toNanos() / requestsPerMinute;
    }

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        if (called) {
            long wait = gapNanos - (System.nanoTime() - lastCall);
            if (wait > 0) {
                waitedNanos += wait;
                try {
                    Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while pacing", e);
                }
            }
        }
        try {
            return delegate.call(prompt);
        } finally {
            called = true;
            lastCall = System.nanoTime();
        }
    }

    /** Total time spent pausing for the RPM limit, so reports can leave it out of latency. */
    synchronized long waitedNanos() {
        return waitedNanos;
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }
}
