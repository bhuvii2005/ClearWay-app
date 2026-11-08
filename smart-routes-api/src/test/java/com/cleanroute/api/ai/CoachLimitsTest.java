package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import com.cleanroute.api.exception.RateLimitedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The per-user hourly Coach limit and the daily hosted token budget. */
class CoachLimitsTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-08T10:00:00Z"));

    @Test
    void rateLimiter_AllowsTheLimitThen429sWithRetryAfter() {
        CoachRateLimiter limiter = new CoachRateLimiter(20, clock);
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            limiter.acquire(user);
        }

        clock.advance(Duration.ofMinutes(15));
        assertThatThrownBy(() -> limiter.acquire(user))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(429);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(45 * 60L);
                })
                .hasMessageContaining("45 minutes");

        // Another user is not affected.
        limiter.acquire(UUID.randomUUID());
    }

    @Test
    void rateLimiter_ResetsAfterTheHourAndOnForget() {
        CoachRateLimiter limiter = new CoachRateLimiter(1, clock);
        UUID user = UUID.randomUUID();
        limiter.acquire(user);
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(RateLimitedException.class);

        clock.advance(Duration.ofHours(1));
        limiter.acquire(user);

        limiter.forget(user);
        limiter.acquire(user);
    }

    @Test
    void tokenBudget_BlocksWhenUsedUpAndResetsAtMidnightUtc() {
        HostedTokenBudget budget = new HostedTokenBudget(100, clock);
        budget.record(60);
        budget.ensureAvailable();
        budget.record(40);

        assertThatThrownBy(budget::ensureAvailable)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(503));

        clock.advance(Duration.ofHours(14)); // 2026-11-09T00:00Z
        budget.ensureAvailable();
        assertThat(budget.usedToday()).isZero();
    }

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
