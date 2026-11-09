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
    void budget_BlocksWhenTokensAreUsedUp() {
        HostedAiBudget budget = new HostedAiBudget(100, 100, clock);
        budget.recordTokens(60);
        budget.ensureAvailable();
        budget.recordTokens(40);

        assertThatThrownBy(budget::ensureAvailable)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(503));
    }

    @Test
    void budget_BlocksWhenRequestsAreUsedUpAndResetsAtMidnightPacific() {
        // 10:00Z on 8 Nov is 02:00 in Los Angeles (PST, UTC-8), so that Pacific day ends at 08:00Z on 9 Nov.
        HostedAiBudget budget = new HostedAiBudget(3, 1_000_000, clock.withZone(ZoneId.of("America/Los_Angeles")));
        for (int i = 0; i < 3; i++) {
            budget.ensureAvailable();
            budget.recordRequest();
        }
        assertThatThrownBy(budget::ensureAvailable).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofHours(14)); // midnight UTC: still 8 Nov in Pacific, still blocked
        assertThatThrownBy(budget::ensureAvailable).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofHours(8)); // 08:00Z = midnight Pacific
        budget.ensureAvailable();
        assertThat(budget.requestsToday()).isZero();
        assertThat(budget.tokensToday()).isZero();
    }

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        private final Instant[] now;
        private final ZoneId zone;

        MutableClock(Instant start) {
            this(new Instant[] {start}, ZoneOffset.UTC);
        }

        private MutableClock(Instant[] now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void advance(Duration by) {
            now[0] = now[0].plus(by);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        /** Same moving time, seen in another zone. */
        @Override
        public MutableClock withZone(ZoneId other) {
            return new MutableClock(now, other);
        }

        @Override
        public Instant instant() {
            return now[0];
        }
    }
}
