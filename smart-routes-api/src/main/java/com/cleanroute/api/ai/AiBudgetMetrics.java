package com.cleanroute.api.ai;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Today's use of each daily AI budget, as gauges under {@code /actuator/metrics}:
 * {@code clearway.ai.budget.requests{budget=chat|embedding}}, {@code clearway.ai.budget.tokens{budget=chat}}
 * and {@code clearway.ai.budget.exhausted{budget}} (1 when no more calls go out today). The eval
 * harness reads the chat one before a run, because the app and the evals share one daily quota.
 */
@Component
public class AiBudgetMetrics implements MeterBinder {

    private final LlmGateway chat;
    private final EmbeddingService embeddings;

    public AiBudgetMetrics(LlmGateway chat, EmbeddingService embeddings) {
        this.chat = chat;
        this.embeddings = embeddings;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        bind(registry, "chat", chat.budget(), true);
        bind(registry, "embedding", embeddings.budget(), false);
    }

    private static void bind(MeterRegistry registry, String name, HostedAiBudget budget, boolean withTokens) {
        Gauge.builder("clearway.ai.budget.requests", budget, HostedAiBudget::requestsToday)
                .description("Provider requests sent today (resets at midnight Pacific)").tag("budget", name)
                .register(registry);
        if (withTokens) {
            Gauge.builder("clearway.ai.budget.tokens", budget, HostedAiBudget::tokensToday)
                    .description("Tokens used today").tag("budget", name).register(registry);
        }
        Gauge.builder("clearway.ai.budget.exhausted", budget, b -> b.exhaustedToday() ? 1 : 0)
                .description("1 when today's budget or the provider's daily quota is used up").tag("budget", name)
                .register(registry);
    }
}
