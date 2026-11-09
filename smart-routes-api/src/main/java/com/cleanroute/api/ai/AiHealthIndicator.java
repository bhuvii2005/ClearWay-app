package com.cleanroute.api.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Shows the Coach's AI set-up under {@code /actuator/health} ("ai"). Always UP: the app works
 * without AI, so a missing key or an open circuit is reported as a detail, never as DOWN.
 * It does not call the provider, so health checks never spend free-tier quota.
 */
@Component("ai")
public class AiHealthIndicator implements HealthIndicator {

    private final LlmGateway gateway;
    private final EmbeddingService embeddings;

    public AiHealthIndicator(LlmGateway gateway, EmbeddingService embeddings) {
        this.gateway = gateway;
        this.embeddings = embeddings;
    }

    @Override
    public Health health() {
        Health.Builder health = Health.up().withDetail("coachAvailable", gateway.isConfigured()
                && gateway.circuitState() != CircuitBreaker.State.OPEN);
        gateway.provider().ifPresent(p -> health.withDetail("provider", p.name().toLowerCase()));
        gateway.modelName().ifPresent(m -> health.withDetail("model", m));
        if (!gateway.isConfigured()) {
            health.withDetail("reason", "no AI provider key set");
        }
        health.withDetail("knowledgeBaseAvailable", embeddings.isConfigured()
                && embeddings.circuitState() != CircuitBreaker.State.OPEN);
        return health.withDetail("circuit", gateway.circuitState().name()).build();
    }
}
