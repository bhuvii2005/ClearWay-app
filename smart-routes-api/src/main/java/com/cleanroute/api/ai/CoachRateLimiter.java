package com.cleanroute.api.ai;

import com.cleanroute.api.exception.RateLimitedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Limits Coach messages per user with a fixed one-hour window, like AuthRateLimitFilter does
 * for logins (ADR-19). In memory, so it applies per backend instance.
 */
@Component
public class CoachRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int maxPerHour;
    private final Clock clock;
    private final Cache<UUID, Window> windows = Caffeine.newBuilder()
            .expireAfterAccess(WINDOW.plusMinutes(5))
            .maximumSize(100_000)
            .build();

    @Autowired
    public CoachRateLimiter(AiProperties properties) {
        this(properties.limits().messagesPerHour(), Clock.systemUTC());
    }

    CoachRateLimiter(int maxPerHour, Clock clock) {
        this.maxPerHour = maxPerHour;
        this.clock = clock;
    }

    /** Counts one message for this user, or throws 429 with the seconds left in the window. */
    public void acquire(UUID userId) {
        Instant now = clock.instant();
        Window window = windows.asMap().compute(userId, (id, w) ->
                w == null || !now.isBefore(w.start.plus(WINDOW)) ? new Window(now, 1) : new Window(w.start, w.count + 1));
        if (window.count > maxPerHour) {
            long wait = (Duration.between(now, window.start.plus(WINDOW)).toMillis() + 999) / 1000;
            long minutes = Math.max(1, (wait + 59) / 60);
            throw new RateLimitedException("You've reached the Coach limit of " + maxPerHour
                    + " messages an hour. Please try again in " + minutes + (minutes == 1 ? " minute." : " minutes."), wait);
        }
    }

    /** Forgets a user's count, for example when their account is deleted. */
    public void forget(UUID userId) {
        windows.invalidate(userId);
    }

    private record Window(Instant start, int count) {
    }
}
