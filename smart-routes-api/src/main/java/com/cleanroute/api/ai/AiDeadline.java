package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;

import java.time.Duration;
import java.util.Optional;

/**
 * The time budget for one piece of AI work, such as one Coach message with all its tool rounds.
 * It lives on the current thread (Spring AI runs the tool loop on the caller's thread), so the
 * gateways and tools can see it without passing it around:
 * <ul>
 *   <li>{@link LlmGateway} cuts each model call's timeout to the time left,</li>
 *   <li>{@link ProviderGuard} won't start a call, or wait for a retry, past the deadline,</li>
 *   <li>{@link CoachTools} won't start a tool past the deadline.</li>
 * </ul>
 * With no deadline open, nothing changes (ingestion and evals run without one).
 *
 * <pre>
 * try (AiDeadline deadline = AiDeadline.start(Duration.ofSeconds(45))) { ... }
 * </pre>
 */
public final class AiDeadline implements AutoCloseable {

    /** Below this, a new provider call can't finish in time, so it isn't started. */
    static final Duration MIN_CALL_TIME = Duration.ofSeconds(1);
    static final String OUT_OF_TIME = "The Coach took too long to answer. Please try again.";

    private static final ThreadLocal<AiDeadline> CURRENT = new ThreadLocal<>();

    private final long deadlineNanos;
    private final AiDeadline outer;

    private AiDeadline(long deadlineNanos, AiDeadline outer) {
        this.deadlineNanos = deadlineNanos;
        this.outer = outer;
    }

    public static AiDeadline start(Duration budget) {
        AiDeadline deadline = new AiDeadline(System.nanoTime() + budget.toNanos(), CURRENT.get());
        CURRENT.set(deadline);
        return deadline;
    }

    /** Time left on the current thread's deadline; empty when there is none. Never negative. */
    static Optional<Duration> remaining() {
        AiDeadline deadline = CURRENT.get();
        if (deadline == null) {
            return Optional.empty();
        }
        long left = deadline.deadlineNanos - System.nanoTime();
        return Optional.of(Duration.ofNanos(Math.max(0, left)));
    }

    /** True when a deadline is open and less than {@link #MIN_CALL_TIME} of it is left. */
    static boolean expired() {
        return remaining().map(left -> left.compareTo(MIN_CALL_TIME) < 0).orElse(false);
    }

    /** Throws 503 when the current deadline has (nearly) run out. */
    static void ensureTimeLeft() {
        if (expired()) {
            throw ApiException.serviceUnavailable(OUT_OF_TIME);
        }
    }

    @Override
    public void close() {
        if (outer == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(outer);
        }
    }
}
