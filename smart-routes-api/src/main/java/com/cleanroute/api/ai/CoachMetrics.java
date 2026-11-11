package com.cleanroute.api.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The Coach's own counters, next to Spring AI's built-in model metrics (gen_ai.client.*). All are
 * read through {@code /actuator/metrics} (HTTP Basic). Tags hold only fixed words, never user text.
 * <ul>
 *   <li>{@code coach.tool.calls{tool, outcome}}: outcome is ok, invalid, not_found, unavailable,
 *       out_of_time or error,</li>
 *   <li>{@code coach.fallbacks{reason}}: budget, quota_exhausted, time_budget, provider_error,
 *       tool_cap or rate_limit,</li>
 *   <li>{@code kb.search{result}}: hit, empty or error.</li>
 * </ul>
 */
@Component
public class CoachMetrics {

    private final MeterRegistry registry;

    @Autowired
    public CoachMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Counters that go nowhere, for code built by hand in tests and evals. */
    static CoachMetrics detached() {
        return new CoachMetrics(new SimpleMeterRegistry());
    }

    MeterRegistry registry() {
        return registry;
    }

    void toolCall(String tool, String outcome) {
        Counter.builder("coach.tool.calls").description("Coach tool calls by tool and outcome")
                .tag("tool", tool).tag("outcome", outcome).register(registry).increment();
    }

    void fallback(String reason) {
        Counter.builder("coach.fallbacks").description("Coach messages that ended in a fallback answer or an error")
                .tag("reason", reason).register(registry).increment();
    }

    void knowledgeBaseSearch(String result) {
        Counter.builder("kb.search").description("Knowledge-base searches by the Coach")
                .tag("result", result).register(registry).increment();
    }
}
