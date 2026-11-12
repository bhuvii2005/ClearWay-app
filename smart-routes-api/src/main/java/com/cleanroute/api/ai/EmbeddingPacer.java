package com.cleanroute.api.ai;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Spaces knowledge-base embedding requests for the provider's per-minute limits, on both counts:
 * <ul>
 *   <li>requests: at least 60 s / RPM between two requests,</li>
 *   <li>tokens: no more than the token limit in any 60-second window.</li>
 * </ul>
 * A single request bigger than the token limit is still sent (alone in its window), so ingestion
 * can't get stuck. Time is tracked as "the later of the real clock and where our own waits
 * should have taken us", so a fake {@link Sleeper} in tests gives the same waits as a real one.
 */
final class EmbeddingPacer {

    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

    private record Sent(long atNanos, long tokens) {
    }

    private final long gapNanos;
    private final long tokensPerMinute;
    private final Sleeper sleeper;
    private final LongSupplier nanoTime;
    private final Deque<Sent> window = new ArrayDeque<>();
    private long now;
    private Long lastRequestNanos;

    EmbeddingPacer(int requestsPerMinute, int tokensPerMinute, Sleeper sleeper, LongSupplier nanoTime) {
        this.gapNanos = WINDOW_NANOS / requestsPerMinute;
        this.tokensPerMinute = tokensPerMinute;
        this.sleeper = sleeper;
        this.nanoTime = nanoTime;
    }

    /** Waits, if needed, until a request of {@code tokens} tokens fits both limits, then counts it as sent. */
    synchronized void beforeRequest(long tokens) {
        now = Math.max(now, nanoTime.getAsLong());
        dropOlderThan(now - WINDOW_NANOS);
        long wait = Math.max(requestWait(), tokenWait(tokens));
        if (wait > 0) {
            try {
                sleeper.sleep(Duration.ofNanos(wait));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while pacing embedding requests", e);
            }
            now += wait;
            dropOlderThan(now - WINDOW_NANOS);
        }
        window.addLast(new Sent(now, tokens));
        lastRequestNanos = now;
    }

    private long requestWait() {
        return lastRequestNanos == null ? 0 : Math.max(0, gapNanos - (now - lastRequestNanos));
    }

    /** How long until enough of the window's oldest requests age out for this one to fit. */
    private long tokenWait(long tokens) {
        long used = window.stream().mapToLong(Sent::tokens).sum();
        if (used + tokens <= tokensPerMinute) {
            return 0;
        }
        long freed = 0;
        for (Sent sent : window) {
            freed += sent.tokens();
            if (used - freed + tokens <= tokensPerMinute) {
                return Math.max(0, sent.atNanos() + WINDOW_NANOS - now);
            }
        }
        // Too big even for an empty window: wait for the whole window to clear, then send it alone.
        return Math.max(0, window.getLast().atNanos() + WINDOW_NANOS - now);
    }

    private void dropOlderThan(long cutoff) {
        while (!window.isEmpty() && window.getFirst().atNanos() <= cutoff) {
            window.removeFirst();
        }
    }
}
