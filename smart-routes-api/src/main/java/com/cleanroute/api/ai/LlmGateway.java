package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The only way into a chat model. It wraps the provider's model and is itself a {@link ChatModel},
 * so Spring AI's ChatClient (and its tool loop) sends every round trip through here. Each call gets:
 * <ul>
 *   <li>the daily hosted budget of requests and tokens ({@link HostedAiBudget}),</li>
 *   <li>a bulkhead "llm" (at most a few calls at once, so one burst can't use the whole free tier),</li>
 *   <li>a circuit breaker "llm" (stop calling a provider that keeps failing; a 429 is the free
 *       limit doing its job, not a broken provider, so it never counts as a failure),</li>
 *   <li>one retry, only on 429 or 5xx, waiting as long as Retry-After says (up to a cap).</li>
 * </ul>
 * Every request sent and every token used is also added to the caller's {@link AiUsageTally}.
 * Any provider failure becomes {@code ApiException(503)}; it never leaks provider details or keys.
 */
@Component
public class LlmGateway implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_SERVER_ERROR_WAIT = Duration.ofSeconds(1);

    /** Pauses before the retry; replaced in tests so they don't really wait. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final Optional<ProviderChatModel> model;
    private final HostedAiBudget budget;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final Duration maxRetryWait;
    private final Sleeper sleeper;

    @Autowired
    public LlmGateway(ChatModelFactory factory, HostedAiBudget budget, CircuitBreakerRegistry circuitBreakers,
                      BulkheadRegistry bulkheads, AiProperties properties) {
        this(factory.create(), budget, circuitBreakers.circuitBreaker("llm"), bulkheads.bulkhead("llm"),
                properties.limits().maxRetryWait(), duration -> Thread.sleep(duration.toMillis()));
    }

    LlmGateway(Optional<ProviderChatModel> model, HostedAiBudget budget, CircuitBreaker circuitBreaker,
               Bulkhead bulkhead, Duration maxRetryWait, Sleeper sleeper) {
        this.model = model;
        this.budget = budget;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.maxRetryWait = maxRetryWait;
        this.sleeper = sleeper;
    }

    /** False when AI is off or the chosen provider has no key; every call then answers 503. */
    public boolean isConfigured() {
        return model.isPresent();
    }

    public Optional<AiProperties.Provider> provider() {
        return model.map(ProviderChatModel::provider);
    }

    public Optional<String> modelName() {
        return model.map(ProviderChatModel::model);
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ProviderChatModel target = model.orElseThrow(() ->
                ApiException.serviceUnavailable("Coach is offline right now: no AI provider is set up."));
        budget.ensureAvailable();

        ChatResponse response;
        try {
            response = Bulkhead.decorateSupplier(bulkhead, () -> withCircuitBreaker(target.chatModel(), prompt)).get();
        } catch (BulkheadFullException e) {
            throw ApiException.serviceUnavailable("Coach is busy right now. Please try again in a moment.");
        } catch (CallNotPermittedException e) {
            throw ApiException.serviceUnavailable("Coach is offline for a moment. Please try again in a minute.");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            ProviderFailure failure = ProviderFailure.of(e);
            // Status only: provider messages can echo the request, and the request may hold user text.
            log.warn("AI call to {} failed (status {}, {})", target.provider(),
                    failure.status() == 0 ? "network/timeout" : failure.status(), e.getClass().getSimpleName());
            throw ApiException.serviceUnavailable(failure.rateLimited()
                    ? "Coach is busy right now (the free AI limit was reached). Please try again in a minute."
                    : "Coach is offline right now. Please try again later.");
        }
        long tokens = totalTokens(response);
        budget.recordTokens(tokens);
        AiUsageTally.recordTokens(tokens);
        return response;
    }

    /** Like CircuitBreaker.decorateSupplier, except a 429 releases the permit instead of counting as a failure. */
    private ChatResponse withCircuitBreaker(ChatModel delegate, Prompt prompt) {
        circuitBreaker.acquirePermission();
        long start = System.nanoTime();
        try {
            ChatResponse response = callWithOneRetry(delegate, prompt);
            circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return response;
        } catch (RuntimeException e) {
            if (ProviderFailure.of(e).rateLimited()) {
                circuitBreaker.releasePermission();
            } else {
                circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            }
            throw e;
        }
    }

    private ChatResponse callWithOneRetry(ChatModel delegate, Prompt prompt) {
        try {
            return send(delegate, prompt);
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
            try {
                sleeper.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw first;
            }
            return send(delegate, prompt);
        }
    }

    /** One request to the provider, counted whether or not it succeeds. */
    private ChatResponse send(ChatModel delegate, Prompt prompt) {
        budget.recordRequest();
        AiUsageTally.recordRequest();
        return delegate.call(prompt);
    }

    private static long totalTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        Integer total = usage == null ? null : usage.getTotalTokens();
        return total == null ? 0 : total;
    }

    @Override
    public ChatOptions getOptions() {
        return model.map(m -> m.chatModel().getOptions()).orElseGet(ChatModel.super::getOptions);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // v1 Coach replies are plain JSON; streaming would bypass the retry and budget above.
        return Flux.error(new UnsupportedOperationException("Streaming is not supported by the Coach"));
    }
}
