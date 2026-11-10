package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * The only way into a chat model. It wraps the provider's model and is itself a {@link ChatModel},
 * so Spring AI's ChatClient (and its tool loop) sends every round trip through here. Each call goes
 * through a {@link ProviderGuard} with the chat budget, bulkhead "llm" and circuit breaker "llm".
 * Every request sent and every token used is also added to the caller's {@link AiUsageTally}.
 */
@Component
public class LlmGateway implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final Optional<ProviderChatModel> model;
    private final ProviderGuard guard;

    @Autowired
    public LlmGateway(ChatModelFactory factory, CircuitBreakerRegistry circuitBreakers,
                      BulkheadRegistry bulkheads, AiProperties properties) {
        this(factory.create(), chatBudget(properties), circuitBreakers.circuitBreaker("llm"), bulkheads.bulkhead("llm"),
                properties.limits().maxRetryWait(), Sleeper.REAL);
    }

    LlmGateway(Optional<ProviderChatModel> model, HostedAiBudget budget, CircuitBreaker circuitBreaker,
               Bulkhead bulkhead, Duration maxRetryWait, Sleeper sleeper) {
        this.model = model;
        this.guard = new ProviderGuard("chat", budget, circuitBreaker, bulkhead, maxRetryWait, sleeper);
    }

    private static HostedAiBudget chatBudget(AiProperties properties) {
        var limits = properties.limits();
        return new HostedAiBudget("chat", limits.dailyRequestBudget(), limits.dailyTokenBudget(),
                Clock.system(limits.budgetResetZone()));
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
        return guard.circuitState();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ProviderChatModel target = model.orElseThrow(() ->
                ApiException.serviceUnavailable("Coach is offline right now: no AI provider is set up."));
        long start = System.nanoTime();
        ChatResponse response = guard.call(() -> target.chatModel().call(withinDeadline(prompt, target)),
                AiUsageTally::recordChatRequest);
        long tokens = totalTokens(response);
        guard.budget().recordTokens(tokens);
        AiUsageTally.recordChatTokens(tokens);
        log.info("AI chat call: {} ms, {} tokens", (System.nanoTime() - start) / 1_000_000, tokens);
        return response;
    }

    /**
     * When a time budget is open ({@link AiDeadline}), cuts this call's timeout to the time left,
     * so a slow model can't hold one Coach message open for several full timeouts.
     */
    private static Prompt withinDeadline(Prompt prompt, ProviderChatModel target) {
        Optional<Duration> left = AiDeadline.remaining();
        if (left.isEmpty() || !(prompt.getOptions() instanceof OpenAiChatOptions options)) {
            return prompt;
        }
        Duration configured = options.getTimeout() != null ? options.getTimeout()
                : target.chatModel().getOptions() instanceof OpenAiChatOptions defaults ? defaults.getTimeout() : null;
        if (configured != null && configured.compareTo(left.get()) <= 0) {
            return prompt;
        }
        return prompt.mutate().chatOptions(options.mutate().timeout(left.get()).build()).build();
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
