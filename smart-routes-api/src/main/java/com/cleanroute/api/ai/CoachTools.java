package com.cleanroute.api.ai;

import com.cleanroute.api.dto.PollutionData;
import com.cleanroute.api.dto.RouteHistoryDto;
import com.cleanroute.api.dto.RouteMetricsDto;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.exception.ApiException;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.service.RouteService;
import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The Coach's tools. All are read-only: none creates, changes or deletes anything.
 * <p>
 * The user is never a tool argument: each tool reads the signed-in user's id from the
 * {@link CoachTurn} in the ToolContext, which the server fills from the JWT. Arguments the model
 * sends are validated, and problems come back as an {@code error} result the model can recover
 * from. Results are plain data (serialised to JSON); text in them, such as route names, is
 * user-typed and the system prompt tells the model never to follow it.
 */
@Component
public class CoachTools {

    private static final Logger log = LoggerFactory.getLogger(CoachTools.class);

    static final int MAX_HISTORY_DAYS = 30;
    static final int MAX_QUERY_CHARS = 300;
    /** Below this many distinct hours with readings, a "best hour" is just the only hour seen. */
    static final int MIN_HOURS_FOR_BEST_HOUR = 3;
    private static final String GRID_NOTE = "Modelled on a grid about 10-40 km wide, not measured at street level.";

    private final RouteService routes;
    private final UserRepository users;
    private final OpenMeteoAqiClient airQuality;
    private final KnowledgeBaseSearch knowledgeBase;
    private final CoachMetrics metrics;

    @Autowired
    public CoachTools(RouteService routes, UserRepository users, OpenMeteoAqiClient airQuality,
                      KnowledgeBaseSearch knowledgeBase, CoachMetrics metrics) {
        this.routes = routes;
        this.users = users;
        this.airQuality = airQuality;
        this.knowledgeBase = knowledgeBase;
        this.metrics = metrics;
    }

    /** For tests and evals built by hand: counters go nowhere. */
    CoachTools(RouteService routes, UserRepository users, OpenMeteoAqiClient airQuality, KnowledgeBaseSearch knowledgeBase) {
        this(routes, users, airQuality, knowledgeBase, CoachMetrics.detached());
    }

    // ------------------------------------------------------------------ tools

    @Tool(name = "listMyRoutes", description = "List the signed-in user's saved routes: id, name, travel mode, "
            + "latest pollution score (lower is cleaner), average score, and whether the route is monitored.")
    public Object listMyRoutes(ToolContext context) {
        return run(context, "listMyRoutes", turn -> {
            List<RouteSummary> list = routes.getRoutesForUser(turn.userId()).stream().map(RouteSummary::of).toList();
            return list.isEmpty()
                    ? new RouteList(list, "The user has no saved routes yet. They can save one in the route planner.")
                    : new RouteList(list, null);
        });
    }

    @Tool(name = "getRouteHistory", description = "Pollution history of one of the user's saved routes over the "
            + "last 1-30 days: number of readings, latest/lowest/highest/mean score, the mean score for each hour "
            + "of the day in the user's time zone, and the cleanest (bestHour) and most polluted (worstHour) hours.")
    public Object getRouteHistory(
            @ToolParam(description = "The route id (a UUID) from listMyRoutes") String routeId,
            @ToolParam(description = "How many days back to look, 1 to 30") Integer days,
            ToolContext context) {
        return run(context, "getRouteHistory", turn -> {
            UUID id = parseUuid(routeId);
            if (days == null || days < 1 || days > MAX_HISTORY_DAYS) {
                throw invalid("days must be a whole number from 1 to " + MAX_HISTORY_DAYS);
            }
            RouteHistoryDto history = routes.getHistory(id, turn.userId(), days, turn.timeZone().getId());
            return RouteHistory.of(history);
        });
    }

    @Tool(name = "getCurrentAirQuality", description = "Current modelled air quality at a point: European AQI, "
            + "its band, and PM2.5, PM10, NO2 and ozone in µg/m³.")
    public Object getCurrentAirQuality(
            @ToolParam(description = "Latitude in degrees, -90 to 90 (north is positive)") Double latitude,
            @ToolParam(description = "Longitude in degrees, -180 to 180 (east is positive)") Double longitude,
            ToolContext context) {
        return run(context, "getCurrentAirQuality", turn -> {
            if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                throw invalid("latitude must be a number from -90 to 90");
            }
            if (longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                throw invalid("longitude must be a number from -180 to 180");
            }
            PollutionData data = airQuality.getPollutionData(latitude, longitude);
            if (data == null) {
                return new Unavailable("unavailable", "Air-quality data is unavailable right now.");
            }
            return new AirQuality(latitude, longitude, data.getAqi(), band(data.getAqi()), data.getPm25(),
                    data.getPm10(), data.getNo2(), data.getOzone(), GRID_NOTE);
        });
    }

    @Tool(name = "getMySensitivities", description = "The pollutants the signed-in user asked ClearWay to weigh "
            + "more heavily when scoring their routes (their sensitivities).")
    public Object getMySensitivities(ToolContext context) {
        return run(context, "getMySensitivities", turn -> {
            User user = users.findById(turn.userId()).orElseThrow(() -> ApiException.notFound("User not found"));
            return new Sensitivities(isTrue(user.getAvoidPm25()), isTrue(user.getAvoidPm10()),
                    isTrue(user.getAvoidNo2()), isTrue(user.getAvoidOzone()));
        });
    }

    @Tool(name = "searchKnowledgeBase", description = "Search ClearWay's air-quality and health guidance (WHO "
            + "guideline levels, European AQI bands, health effects, exercising in poor air, how ClearWay scores "
            + "routes). Use it for any health or guideline question. An empty result means there is no source.")
    public Object searchKnowledgeBase(
            @ToolParam(description = "What to look up, in a few words") String query,
            ToolContext context) {
        return run(context, "searchKnowledgeBase", turn -> {
            if (query == null || query.isBlank()) {
                throw invalid("query must not be empty");
            }
            if (query.length() > MAX_QUERY_CHARS) {
                throw invalid("query must be at most " + MAX_QUERY_CHARS + " characters");
            }
            if (!knowledgeBase.isAvailable()) {
                metrics.knowledgeBaseSearch("error");
                return new Unavailable("unavailable", "The knowledge base is not available right now.");
            }
            List<KnowledgeChunk> chunks;
            try {
                chunks = knowledgeBase.search(query);
            } catch (ApiException e) {
                metrics.knowledgeBaseSearch("error");
                return new Unavailable("unavailable", "The knowledge base is not available right now.");
            }
            metrics.knowledgeBaseSearch(chunks.isEmpty() ? "empty" : "hit");
            turn.retrieved(chunks);
            List<Passage> passages = chunks.stream().map(c -> new Passage(c.title(), c.text())).toList();
            return passages.isEmpty()
                    ? new SearchResult(passages, "No source found for this question.")
                    : new SearchResult(passages, null);
        });
    }

    // ------------------------------------------------------------------ plumbing

    private interface Body {
        Object apply(CoachTurn turn);
    }

    /**
     * Runs a tool for the user in the ToolContext. Validation errors and "not found" go back to
     * the model as an error result; anything unexpected is logged and reported without details.
     */
    private Object run(ToolContext context, String name, Body body) {
        CoachTurn turn = turn(context);
        turn.toolUsed(name);
        if (AiDeadline.expired()) {
            // The message's time budget is spent; the next model call will end it with a 503.
            metrics.toolCall(name, "out_of_time");
            return new ToolError("Out of time for this question.");
        }
        try {
            Object result = body.apply(turn);
            metrics.toolCall(name, result instanceof Unavailable ? "unavailable" : "ok");
            return result;
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND || e.getStatus() == HttpStatus.BAD_REQUEST) {
                metrics.toolCall(name, e.getStatus() == HttpStatus.NOT_FOUND ? "not_found" : "invalid");
                return new ToolError(e.getMessage());
            }
            log.warn("Coach tool {} failed: {}", name, e.getStatus());
            metrics.toolCall(name, "error");
            return new ToolError("This information is unavailable right now.");
        } catch (RuntimeException e) {
            log.warn("Coach tool {} failed: {}", name, e.getClass().getSimpleName());
            metrics.toolCall(name, "error");
            return new ToolError("This information is unavailable right now.");
        }
    }

    private static CoachTurn turn(ToolContext context) {
        Map<String, Object> values = context == null ? null : context.getContext();
        Object turn = values == null ? null : values.get(CoachTurn.KEY);
        if (turn instanceof CoachTurn t) {
            return t;
        }
        // Only the server sets this; without it there is no trusted user, so nothing may run.
        throw new IllegalStateException("Coach tool called without a signed-in user in the ToolContext");
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.strip());
        } catch (IllegalArgumentException e) {
            throw invalid("routeId must be a route id (UUID) from listMyRoutes");
        }
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("Invalid argument: " + message);
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    /** European AQI bands as Open-Meteo reports them (see kb/european-aqi-bands.md). */
    static String band(double aqi) {
        if (aqi <= 20) return "Good";
        if (aqi <= 40) return "Fair";
        if (aqi <= 60) return "Moderate";
        if (aqi <= 80) return "Poor";
        if (aqi <= 100) return "Very poor";
        return "Extremely poor";
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    // ------------------------------------------------------------------ results (serialised to JSON)

    public record ToolError(String error) {
    }

    public record Unavailable(String status, String message) {
    }

    public record RouteSummary(UUID id, String name, String type, Double lastScore, Double averageScore,
                               ZonedDateTime lastCheckedAt, boolean monitored) {
        static RouteSummary of(Route r) {
            return new RouteSummary(r.getId(), r.getName(), r.getType(), round(r.getLastPollutionScore()),
                    round(r.getAvgPollutionScore()), r.getLastCheckedAt(), Boolean.TRUE.equals(r.getIsActive()));
        }
    }

    public record RouteList(List<RouteSummary> routes, String note) {
    }

    public record RouteHistory(UUID routeId, int days, String timeZone, int readings, Double latestScore,
                               Double lowestScore, Double highestScore, Double meanScore,
                               Map<Integer, Double> hourlyAverageScore, Integer bestHour, Integer worstHour,
                               String note) {
        static RouteHistory of(RouteHistoryDto h) {
            List<Double> scores = h.points().stream().map(RouteMetricsDto::pollutionScore)
                    .filter(Objects::nonNull).toList();
            if (scores.isEmpty()) {
                return new RouteHistory(h.routeId(), h.days(), h.timeZone(), 0, null, null, null, null, Map.of(),
                        null, null, "This route has no history in this period yet.");
            }
            return new RouteHistory(h.routeId(), h.days(), h.timeZone(), scores.size(),
                    round(scores.get(scores.size() - 1)),
                    round(scores.stream().min(Double::compare).orElseThrow()),
                    round(scores.stream().max(Double::compare).orElseThrow()),
                    round(scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow()),
                    h.hourlyAverages(), h.bestHour(), h.worstHour(),
                    "Scores: lower is cleaner. Hours are 0-23 in " + h.timeZone() + "."
                            + (h.hourlyAverages().size() < MIN_HOURS_FOR_BEST_HOUR
                            ? " Only " + h.hourlyAverages().size() + " hour(s) of the day have readings so far, so "
                            + "bestHour and worstHour are not reliable yet; say so instead of recommending a time."
                            : ""));
        }
    }

    public record AirQuality(double latitude, double longitude, double europeanAqi, String band, double pm25,
                             double pm10, double no2, double ozone, String note) {
    }

    public record Sensitivities(boolean pm25, boolean pm10, boolean no2, boolean ozone) {
    }

    public record Passage(String title, String text) {
    }

    public record SearchResult(List<Passage> results, String note) {
    }
}
