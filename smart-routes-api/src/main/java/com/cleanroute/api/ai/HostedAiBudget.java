package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Caps what the app spends on one hosted AI quota per day: provider requests and tokens, both from
 * config. Chat and embeddings each get their own budget, because Gemini gives each model its own
 * free quota. The day ends at midnight in the clock's zone ({@code clearway.ai.limits.budget-reset-zone},
 * Pacific by default, when Gemini's free daily quota resets). In memory, so it resets on restart;
 * the provider's own limit is the hard backstop.
 */
public class HostedAiBudget {

    private static final Logger log = LoggerFactory.getLogger(HostedAiBudget.class);

    static final String USED_UP = "The Coach has used today's free AI allowance. Please try again tomorrow.";

    private final String name;
    private final long maxRequests;
    private final long maxTokens;
    private final Clock clock;
    private LocalDate day;
    private long requests;
    private long tokens;
    private boolean warned;
    /** The provider said today's quota is gone (a per-day 429); stays set until the day rolls over. */
    private boolean providerExhausted;

    /** The clock's zone decides when the day rolls over. */
    public HostedAiBudget(String name, long maxRequests, long maxTokens, Clock clock) {
        this.name = name;
        this.maxRequests = maxRequests;
        this.maxTokens = maxTokens;
        this.clock = clock;
        this.day = today();
    }

    HostedAiBudget(long maxRequests, long maxTokens, Clock clock) {
        this("test", maxRequests, maxTokens, clock);
    }

    /** Throws 503 once today's request or token budget is used up. */
    public synchronized void ensureAvailable() {
        rollOver();
        if (requests >= maxRequests || tokens >= maxTokens || providerExhausted) {
            if (!warned) {
                log.warn("Daily hosted AI {} budget used up ({}/{} requests, {} tokens); AI {} is off until midnight {}.",
                        name, requests, maxRequests, tokens, name, clock.getZone());
                warned = true;
            }
            throw new AiUnavailableException(providerExhausted
                    ? AiUnavailableException.Reason.QUOTA_EXHAUSTED : AiUnavailableException.Reason.BUDGET, USED_UP);
        }
    }

    /**
     * The provider answered 429 for its per-day quota: no more calls until midnight in the clock's
     * zone (Pacific by default, when Gemini's free quota resets), even if our own cap isn't reached.
     */
    public synchronized void markExhaustedForToday() {
        rollOver();
        if (!providerExhausted) {
            log.warn("The {} provider's daily free quota is used up ({} requests sent today); AI {} is off until midnight {}.",
                    name, requests, name, clock.getZone());
            providerExhausted = true;
            warned = true;
        }
    }

    public synchronized boolean exhaustedToday() {
        rollOver();
        return requests >= maxRequests || tokens >= maxTokens || providerExhausted;
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
            providerExhausted = false;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
