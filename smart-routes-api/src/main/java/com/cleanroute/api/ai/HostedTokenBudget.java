package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Caps the tokens the app spends on hosted AI per day (UTC), so the free tier is never
 * overrun. In memory, so it resets on restart; the provider's own limit is the hard backstop.
 */
@Component
public class HostedTokenBudget {

    private static final Logger log = LoggerFactory.getLogger(HostedTokenBudget.class);

    private final long dailyBudget;
    private final Clock clock;
    private LocalDate day;
    private long used;
    private boolean warned;

    @Autowired
    public HostedTokenBudget(AiProperties properties) {
        this(properties.limits().dailyTokenBudget(), Clock.systemUTC());
    }

    HostedTokenBudget(long dailyBudget, Clock clock) {
        this.dailyBudget = dailyBudget;
        this.clock = clock;
        this.day = today();
    }

    /** Throws 503 once today's budget is used up. */
    public synchronized void ensureAvailable() {
        rollOver();
        if (used >= dailyBudget) {
            if (!warned) {
                log.warn("Daily hosted AI token budget of {} is used up; the Coach is off until midnight UTC.", dailyBudget);
                warned = true;
            }
            throw ApiException.serviceUnavailable(
                    "The Coach has used today's free AI allowance. Please try again tomorrow.");
        }
    }

    public synchronized void record(long tokens) {
        rollOver();
        if (tokens > 0) {
            used += tokens;
        }
    }

    public synchronized long usedToday() {
        rollOver();
        return used;
    }

    private void rollOver() {
        LocalDate now = today();
        if (!now.equals(day)) {
            day = now;
            used = 0;
            warned = false;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
