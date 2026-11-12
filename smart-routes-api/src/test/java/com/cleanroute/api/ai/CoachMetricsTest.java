package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.service.RouteService;
import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** coach.tool.calls, coach.fallbacks and kb.search count what actually happened. No network. */
class CoachMetricsTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID ROUTE = UUID.randomUUID();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CoachMetrics metrics = new CoachMetrics(registry);
    private final RouteService routes = mock(RouteService.class);
    private final KnowledgeBaseSearch knowledge = mock(KnowledgeBaseSearch.class);
    private final CoachTools tools = new CoachTools(routes, mock(UserRepository.class), mock(OpenMeteoAqiClient.class),
            knowledge, metrics);
    private HostedAiBudget budget = new HostedAiBudget(100, 100_000, Clock.systemUTC());
    private ScriptedChatModel model;

    @Test
    void toolCalls_AreCountedByToolAndOutcome() {
        when(routes.getRoutesForUser(ALICE)).thenReturn(List.of(CoachServiceTest.route(ROUTE, "Office walk")));
        when(routes.getHistory(eq(ROUTE), eq(ALICE), anyInt(), anyString()))
                .thenThrow(ApiException.notFound("Route not found"));
        CoachService coach = coach(prompt -> switch ((int) ScriptedChatModel.toolResults(prompt)) {
            case 0 -> model.toolCall("listMyRoutes", "{}");
            case 1 -> model.toolCall("getRouteHistory", "{\"routeId\":\"" + ROUTE + "\",\"days\":7}");
            case 2 -> model.toolCall("getRouteHistory", "{\"routeId\":\"not-a-uuid\",\"days\":7}");
            default -> ScriptedChatModel.answer("done");
        });

        coach.chat(ALICE, UUID.randomUUID(), "best time?", null);

        assertThat(count("coach.tool.calls", "tool", "listMyRoutes", "outcome", "ok")).isEqualTo(1);
        assertThat(count("coach.tool.calls", "tool", "getRouteHistory", "outcome", "not_found")).isEqualTo(1);
        assertThat(count("coach.tool.calls", "tool", "getRouteHistory", "outcome", "invalid")).isEqualTo(1);
        assertThat(registry.find("coach.fallbacks").counters()).isEmpty();
    }

    @Test
    void knowledgeBaseSearches_AreCountedAsHitEmptyOrError() {
        when(knowledge.isAvailable()).thenReturn(true);
        when(knowledge.search("pm25")).thenReturn(List.of(new KnowledgeChunk("WHO", "https://who.int", "who.md", "5", 0.7)));
        when(knowledge.search("pizza")).thenReturn(List.of());
        when(knowledge.search("down")).thenThrow(ApiException.serviceUnavailable("embedding provider down"));
        CoachService coach = coach(prompt -> switch ((int) ScriptedChatModel.toolResults(prompt)) {
            case 0 -> model.toolCall("searchKnowledgeBase", "{\"query\":\"pm25\"}");
            case 1 -> model.toolCall("searchKnowledgeBase", "{\"query\":\"pizza\"}");
            case 2 -> model.toolCall("searchKnowledgeBase", "{\"query\":\"down\"}");
            default -> ScriptedChatModel.answer("done");
        });

        coach.chat(ALICE, UUID.randomUUID(), "guidance?", null);

        assertThat(count("kb.search", "result", "hit")).isEqualTo(1);
        assertThat(count("kb.search", "result", "empty")).isEqualTo(1);
        assertThat(count("kb.search", "result", "error")).isEqualTo(1);
        assertThat(count("coach.tool.calls", "tool", "searchKnowledgeBase", "outcome", "unavailable")).isEqualTo(1);
    }

    @Test
    void toolCap_IsCountedAsAFallback() {
        when(routes.getRoutesForUser(any())).thenReturn(List.of());
        CoachService coach = coach(prompt -> model.toolCall("listMyRoutes", "{}"));

        coach.chat(ALICE, UUID.randomUUID(), "loop", null);

        assertThat(count("coach.fallbacks", "reason", "tool_cap")).isEqualTo(1);
    }

    @Test
    void ourDailyBudget_AndTheProvidersQuota_AreCountedSeparately() {
        budget = new HostedAiBudget(1, 100_000, Clock.systemUTC());
        CoachService coach = coach(prompt -> ScriptedChatModel.answer("ok"));
        coach.chat(ALICE, UUID.randomUUID(), "one", null);

        assertThatThrownBy(() -> coach.chat(ALICE, UUID.randomUUID(), "two", null)).isInstanceOf(AiUnavailableException.class);
        assertThat(count("coach.fallbacks", "reason", "budget")).isEqualTo(1);

        HostedAiBudget quotaGone = new HostedAiBudget(100, 100_000, Clock.systemUTC());
        quotaGone.markExhaustedForToday();
        budget = quotaGone;
        CoachService afterQuota = coach(prompt -> ScriptedChatModel.answer("ok"));
        assertThatThrownBy(() -> afterQuota.chat(ALICE, UUID.randomUUID(), "three", null))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.QUOTA_EXHAUSTED));
        assertThat(count("coach.fallbacks", "reason", "quota_exhausted")).isEqualTo(1);
    }

    @Test
    void providerErrors_TimeBudget_AndRateLimit_AreCounted() {
        CoachService failing = coach(prompt -> {
            throw new IllegalStateException("provider exploded");
        });
        assertThatThrownBy(() -> failing.chat(ALICE, UUID.randomUUID(), "hi", null)).isInstanceOf(AiUnavailableException.class);
        assertThat(count("coach.fallbacks", "reason", "provider_error")).isEqualTo(1);

        CoachService noTime = coach(prompt -> ScriptedChatModel.answer("too late"), Duration.ofMillis(100), 100);
        assertThatThrownBy(() -> noTime.chat(ALICE, UUID.randomUUID(), "hi", null))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.TIME_BUDGET));
        assertThat(count("coach.fallbacks", "reason", "time_budget")).isEqualTo(1);

        CoachService oneAnHour = coach(prompt -> ScriptedChatModel.answer("ok"), Duration.ofSeconds(45), 1);
        oneAnHour.chat(ALICE, UUID.randomUUID(), "first", null);
        assertThatThrownBy(() -> oneAnHour.chat(ALICE, UUID.randomUUID(), "second", null)).isInstanceOf(ApiException.class);
        assertThat(count("coach.fallbacks", "reason", "rate_limit")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private double count(String name, String... tags) {
        var counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private CoachService coach(Function<Prompt, ChatResponse> script) {
        return coach(script, Duration.ofSeconds(45), 100);
    }

    private CoachService coach(Function<Prompt, ChatResponse> script, Duration total, int perHour) {
        model = new ScriptedChatModel(script);
        LlmGateway gateway = new LlmGateway(Optional.of(new ProviderChatModel(AiProperties.Provider.GEMINI, "scripted", model)),
                budget, CircuitBreaker.ofDefaults("llm"), Bulkhead.ofDefaults("llm"), Duration.ofSeconds(20), d -> { });
        return new CoachService(gateway, new CoachRateLimiter(perHour, Clock.systemUTC()), new CoachMemory(),
                new Object[] {tools}, 2000, total, ObservationRegistry.NOOP, CoachServiceTest.systemTemplate(),
                Clock.systemUTC(), metrics);
    }
}
