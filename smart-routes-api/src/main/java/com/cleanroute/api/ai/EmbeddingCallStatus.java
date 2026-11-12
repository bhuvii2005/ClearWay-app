package com.cleanroute.api.ai;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The result of the most recent embedding call (ingestion or search), for the health details.
 * Health reads it; it never makes a call itself. Failures keep only a short reason (the
 * AiUnavailableException reason or the exception type), never a message that could hold a key.
 */
public final class EmbeddingCallStatus {

    public record LastCall(Instant at, boolean ok, String reason) {
    }

    private final Clock clock;
    private final AtomicReference<LastCall> last = new AtomicReference<>();

    public EmbeddingCallStatus(Clock clock) {
        this.clock = clock;
    }

    void ok() {
        last.set(new LastCall(clock.instant(), true, null));
    }

    void failed(RuntimeException e) {
        String reason = e instanceof AiUnavailableException unavailable ? unavailable.reason().tag() : e.getClass().getSimpleName();
        last.set(new LastCall(clock.instant(), false, reason));
    }

    public Optional<LastCall> last() {
        return Optional.ofNullable(last.get());
    }
}
