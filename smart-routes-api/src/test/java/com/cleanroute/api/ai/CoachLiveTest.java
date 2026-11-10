package com.cleanroute.api.ai;

import com.cleanroute.api.dto.RouteHistoryDto;
import com.cleanroute.api.dto.RouteMetricsDto;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.service.RouteService;
import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Coach on the real eval provider (Gemini by default) with fixed route data: "when is the best
 * time to walk my saved route?" must call listMyRoutes, then getRouteHistory, and answer with the
 * real best hour (07:00 here). Runs only with {@code ./gradlew evalTest} and only with a key.
 */
@Tag("eval")
class CoachLiveTest {

    @Test
    void bestTimeToWalk_UsesBothRouteTools_AndGivesTheRealBestHour() {
        EvalSettings eval = EvalSettings.load();
        assumeTrue(eval.apiKey.isPresent(), eval.keyName() + " not set; skipping the live Coach check");

        UUID user = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        RouteService routes = mock(RouteService.class);
        when(routes.getRoutesForUser(user)).thenReturn(List.of(CoachServiceTest.route(route, "Office walk")));
        when(routes.getHistory(eq(route), eq(user), anyInt(), anyString()))
                .thenAnswer(inv -> history(route, inv.getArgument(2), inv.getArgument(3)));
        CoachTools tools = new CoachTools(routes, mock(UserRepository.class), mock(OpenMeteoAqiClient.class),
                mock(KnowledgeBaseSearch.class));
        // The app's total budget per message (CLEARWAY_AI_COACH_TOTAL_TIMEOUT, default 45 s).
        java.time.Duration total = com.cleanroute.api.support.DotEnv.get("CLEARWAY_AI_COACH_TOTAL_TIMEOUT")
                .map(v -> org.springframework.boot.convert.DurationStyle.detectAndParse(v.trim()))
                .orElse(java.time.Duration.ofSeconds(45));
        CoachService coach = new CoachService(eval.gateway(eval.budget()), new CoachRateLimiter(100, Clock.systemUTC()),
                new CoachMemory(), new Object[] {tools}, 2000, total, ObservationRegistry.NOOP, CoachServiceTest.systemTemplate(),
                Clock.systemUTC());

        CoachService.CoachReply reply;
        try (AiUsageTally tally = AiUsageTally.open("live Coach eval")) {
            long start = System.nanoTime();
            reply = coach.chat(user, UUID.randomUUID(), "When is the best time to walk my saved route?", "Asia/Kolkata");
            System.out.printf("  answer: %s%n  tools: %s%n  chat requests: %d, tokens: %d, total %d ms%n",
                    reply.answer(), reply.toolsUsed(), tally.chatRequests(), tally.chatTokens(),
                    (System.nanoTime() - start) / 1_000_000);
        }

        assertThat(reply.toolsUsed()).containsSubsequence("listMyRoutes", "getRouteHistory");
        assertThat(reply.answer()).containsPattern("(?i)\\b0?7(:00)?\\s*(a\\.?m\\.?|am|hrs|h\\b|o'clock)?|07:00|7\\s*AM");
    }

    /** Readings over a week: cleanest at 07:00 and dirtiest at 18:00 in the user's zone. */
    private static RouteHistoryDto history(UUID route, int days, String zone) {
        List<RouteMetricsDto> points = new ArrayList<>();
        ZonedDateTime start = ZonedDateTime.now(java.time.ZoneId.of(zone)).minusDays(1).withMinute(0).withSecond(0).withNano(0);
        for (int hour = 0; hour < 24; hour++) {
            double score = hour == 7 ? 18.0 : hour == 18 ? 72.0 : 40.0;
            points.add(new RouteMetricsDto(start.withHour(hour), score, score, score / 4, score / 2, 20.0, 30.0));
        }
        Map<Integer, Double> hourly = new java.util.TreeMap<>();
        points.forEach(p -> hourly.put(p.timestamp().getHour(), p.pollutionScore()));
        return new RouteHistoryDto(route, days, zone, points, hourly, 7, 18);
    }
}
