package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Caps what the app spends on hosted AI per day: provider requests AND tokens, both from config.
 * The day ends at midnight in {@code clearway.ai.limits.budget-reset-zone} (Pacific by default,
 * when Gemini's free daily quota resets). In memory, so it resets on restart; the provider's own
 * limit is the hard backstop.
 */
@Component
public class HostedAiBudget {

    private static final Logger log = LoggerFactory.getLogger(HostedAiBudget.class);

    private final long maxRequests;
    private final long maxTokens;
    private final Clock clock;
    private LocalDate day;
    private long requests;
    private long tokens;
    private boolean warned;

    @Autowired
    public HostedAiBudget(AiProperties properties) {
        this(properties.limits().dailyRequestBudget(), properties.limits().dailyTokenBudget(),
                Clock.system(properties.limits().budgetResetZone()));
    }

    /** The clock's zone decides when the day rolls over. */
    HostedAiBudget(long maxRequests, long maxTokens, Clock clock) {
        this.maxRequests = maxRequests;
        this.maxTokens = maxTokens;
        this.clock = clock;
        this.day = today();
    }

    /** Throws 503 once today's request or token budget is used up. */
    public synchronized void ensureAvailable() {
        rollOver();
        if (requests >= maxRequests || tokens >= maxTokens) {
            if (!warned) {
                log.warn("Daily hosted AI budget used up ({}/{} requests, {}/{} tokens); the Coach is off until midnight {}.",
                        requests, maxRequests, tokens, maxTokens, zone());
                warned = true;
            }
            throw ApiException.serviceUnavailable(
                    "The Coach has used today's free AI allowance. Please try again tomorrow.");
        }
    }

    /** Counts one request sent to the provider (a retry is a second request). */
    public synchronized void recordRequest() {
        rollOver();
        requests++;
    }

    public synchronized void recordTokens(long count) {
        rollOver();
        if (count > 0) {
            tokens += count;
        }
    }

    public synchronized long requestsToday() {
        rollOver();
        return requests;
    }

    public synchronized long tokensToday() {
        rollOver();
        return tokens;
    }

    private void rollOver() {
        LocalDate now = today();
        if (!now.equals(day)) {
            day = now;
            requests = 0;
            tokens = 0;
            warned = false;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private ZoneId zone() {
        return clock.getZone();
    }
}
