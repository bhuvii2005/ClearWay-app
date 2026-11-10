package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The protections every hosted-AI request goes through, shared by chat ({@link LlmGateway}) and
 * embeddings ({@link EmbeddingGateway}), each with its own instance:
 * <ul>
 *   <li>a daily budget ({@link HostedAiBudget}),</li>
 *   <li>a bulkhead (at most a few calls at once, so one burst can't use the whole free tier),</li>
 *   <li>a circuit breaker (stop calling a provider that keeps failing; a 429 is the free limit
 *       doing its job, not a broken provider, so it never counts as a failure),</li>
 *   <li>one retry, only on 429 or 5xx, waiting as long as the provider says (up to a cap).</li>
 * </ul>
 * Any provider failure becomes {@code ApiException(503)}; it never leaks provider details or keys.
 */
final class ProviderGuard {

    private static final Logger log = LoggerFactory.getLogger(ProviderGuard.class);

    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_SERVER_ERROR_WAIT = Duration.ofSeconds(1);

    private final String name;
    private final HostedAiBudget budget;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final Duration maxRetryWait;
    private final Sleeper sleeper;

    ProviderGuard(String name, HostedAiBudget budget, CircuitBreaker circuitBreaker, Bulkhead bulkhead,
                  Duration maxRetryWait, Sleeper sleeper) {
        this.name = name;
        this.budget = budget;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.maxRetryWait = maxRetryWait;
        this.sleeper = sleeper;
    }

    HostedAiBudget budget() {
        return budget;
    }

    CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    /**
     * Sends {@code request} to the provider. Every attempt (a retry is a second one) is counted
     * in the budget and passed to {@code onRequest}, for the caller's usage tally.
     */
    <T> T call(Supplier<T> request, Runnable onRequest) {
        AiDeadline.ensureTimeLeft();
        budget.ensureAvailable();
        Supplier<T> counted = () -> {
            budget.recordRequest();
            onRequest.run();
            return request.get();
        };
        try {
            return Bulkhead.decorateSupplier(bulkhead, () -> withCircuitBreaker(counted)).get();
        } catch (BulkheadFullException e) {
            throw ApiException.serviceUnavailable("Coach is busy right now. Please try again in a moment.");
        } catch (CallNotPermittedException e) {
            throw ApiException.serviceUnavailable("Coach is offline for a moment. Please try again in a minute.");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            if (AiDeadline.expired()) {
                // Our time budget ran out (the call's timeout was cut to fit it); not a provider fault.
                log.info("AI {} call stopped: the time budget ran out ({})", name, e.getClass().getSimpleName());
                throw ApiException.serviceUnavailable(AiDeadline.OUT_OF_TIME);
            }
            ProviderFailure failure = ProviderFailure.of(e);
            // Status only: provider messages can echo the request, and the request may hold user text.
            log.warn("AI {} call failed (status {}, {})", name,
                    failure.status() == 0 ? "network/timeout" : failure.status(), e.getClass().getSimpleName());
            throw ApiException.serviceUnavailable(failure.rateLimited()
                    ? "Coach is busy right now (the free AI limit was reached). Please try again in a minute."
                    : "Coach is offline right now. Please try again later.");
        }
    }

    /** Like CircuitBreaker.decorateSupplier, except a 429 releases the permit instead of counting as a failure. */
    private <T> T withCircuitBreaker(Supplier<T> request) {
        circuitBreaker.acquirePermission();
        long start = System.nanoTime();
        try {
            T result = withOneRetry(request);
            circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return result;
        } catch (RuntimeException e) {
            if (ProviderFailure.of(e).rateLimited() || AiDeadline.expired()) {
                circuitBreaker.releasePermission();
            } else {
                circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            }
            throw e;
        }
    }

    private <T> T withOneRetry(Supplier<T> request) {
        try {
            return request.get();
        } catch (RuntimeException first) {
            ProviderFailure failure = ProviderFailure.of(first);
            if (!failure.retryable()) {
                throw first;
            }
            Duration wait = failure.retryAfter()
                    .orElse(failure.rateLimited() ? DEFAULT_RATE_LIMIT_WAIT : DEFAULT_SERVER_ERROR_WAIT);
            if (wait.compareTo(maxRetryWait) > 0) {
                // The provider wants a long pause; answer now instead of holding the request open.
                throw first;
            }
            boolean noTimeToRetry = AiDeadline.remaining()
                    .map(left -> wait.plus(AiDeadline.MIN_CALL_TIME).compareTo(left) > 0).orElse(false);
            if (noTimeToRetry) {
                throw first;
            }
            try {
                sleeper.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw first;
            }
            return request.get();
        }
    }
}
