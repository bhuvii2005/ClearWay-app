package com.cleanroute.api.service;

import com.cleanroute.api.dto.*;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.exception.ApiException;
import com.cleanroute.api.repository.RouteMetricsRepository;
import com.cleanroute.api.repository.RouteRepository;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.service.integration.TomTomRoutingClient;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Route use cases: computing cleanest-first alternatives, saving and managing routes,
 * re-scoring monitored routes, and building exposure history.
 * Every method that touches a saved route checks that it belongs to the calling user.
 */
@Service
public class RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);
    static final int MAX_HISTORY_DAYS = 90;

    private final RouteRepository routeRepository;
    private final UserRepository userRepository;
    private final RouteMetricsRepository metricsRepository;
    private final PollutionEngineService pollutionEngineService;
    private final NotificationEngineService notificationEngineService;
    private final TomTomRoutingClient tomtomClient;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public RouteService(RouteRepository routeRepository,
                        UserRepository userRepository,
                        RouteMetricsRepository metricsRepository,
                        PollutionEngineService pollutionEngineService,
                        NotificationEngineService notificationEngineService,
                        TomTomRoutingClient tomtomClient) {
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.metricsRepository = metricsRepository;
        this.pollutionEngineService = pollutionEngineService;
        this.notificationEngineService = notificationEngineService;
        this.tomtomClient = tomtomClient;
    }

    // ---------------------------------------------------------------- alternatives

    /**
     * Fetches up to three road-snapped routes from TomTom, scores each one for the user,
     * and returns them sorted cleanest first.
     */
    @Transactional(readOnly = true)
    public List<AlternativeRouteDto> fetchAlternatives(UUID userId, AlternativesRequest request) {
        List<List<Double>> points = request.getPathCoordinates();
        validateCoordinates(points);
        List<Double> start = points.get(0); // [lon, lat]
        List<Double> end = points.get(1);

        TomTomRouteResponse tomtomRes = tomtomClient.getDirections(
                start.get(1), start.get(0), end.get(1), end.get(0), request.getType());
        if (tomtomRes == null || tomtomRes.getRoutes() == null || tomtomRes.getRoutes().isEmpty()) {
            throw ApiException.notFound("No route found between these points");
        }

        User user = userRepository.findById(userId).orElse(null);
        List<AlternativeRouteDto> alternatives = new ArrayList<>();
        boolean first = true; // TomTom returns its fastest route first

        for (TomTomRouteResponse.TomTomRoute ttRoute : tomtomRes.getRoutes()) {
            List<List<Double>> rawCoords = new ArrayList<>();
            if (ttRoute.getLegs() != null) {
                for (TomTomRouteResponse.TomTomLeg leg : ttRoute.getLegs()) {
                    for (TomTomRouteResponse.TomTomPoint pt : leg.getPoints()) {
                        rawCoords.add(List.of(pt.getLongitude(), pt.getLatitude()));
                    }
                }
            }
            if (rawCoords.size() < 2) {
                continue;
            }

            Route tempRoute = new Route(); // not persisted; used only for scoring
            tempRoute.setPath(toLineString(rawCoords));
            RouteMetrics metrics = pollutionEngineService.calculateExposureScore(tempRoute, user);
            if (metrics.getPollutionScore() == null) {
                throw ApiException.serviceUnavailable("Air-quality data is currently unavailable. Please try again shortly.");
            }

            AlternativeRouteDto dto = new AlternativeRouteDto();
            dto.setPathCoordinates(rawCoords);
            dto.setPollutionScore(metrics.getPollutionScore());
            dto.setPollutionData(new PollutionData(metrics.getAqi(), metrics.getPm25(), metrics.getPm10(),
                    metrics.getNo2(), metrics.getOzone(), 0.0));
            dto.setFastest(first);
            if (ttRoute.getSummary() != null) {
                dto.setDistanceMeters(ttRoute.getSummary().getLengthInMeters());
                dto.setTravelTimeSeconds(ttRoute.getSummary().getTravelTimeInSeconds());
            }
            alternatives.add(dto);
            first = false;
        }

        alternatives.stream().filter(AlternativeRouteDto::isFastest).findFirst().ifPresent(fastest -> {
            for (AlternativeRouteDto alt : alternatives) {
                double saving = fastest.getPollutionScore() <= 0 ? 0
                        : (fastest.getPollutionScore() - alt.getPollutionScore()) / fastest.getPollutionScore() * 100;
                alt.setExposureSavingPercent(Math.round(saving * 10) / 10.0);
            }
        });
        alternatives.sort(Comparator.comparingDouble(AlternativeRouteDto::getPollutionScore));
        return alternatives;
    }

    // ---------------------------------------------------------------- saved routes

    @Transactional
    public Route createRoute(UUID userId, CreateRouteRequest request) {
        validateCoordinates(request.getPathCoordinates());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Route route = new Route();
        route.setUser(user);
        route.setName(request.getName().trim());
        route.setType(request.getType() == null ? "walking" : request.getType());
        route.setIsActive(!Boolean.FALSE.equals(request.getMonitor()));
        route.setPath(toLineString(request.getPathCoordinates()));
        if (request.getDistanceMeters() != null) {
            route.setDistanceKm(request.getDistanceMeters() / 1000.0);
        }
        route.setTravelTimeSeconds(request.getTravelTimeSeconds());
        route = routeRepository.save(route);

        scoreRoute(route, user);
        return route;
    }

    @Transactional(readOnly = true)
    public List<Route> getRoutesForUser(UUID userId) {
        return routeRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public RouteDetailDto getRoute(UUID routeId, UUID userId) {
        return new RouteDetailDto(findOwnedRoute(routeId, userId));
    }

    @Transactional
    public Route updateRoute(UUID routeId, UUID userId, UpdateRouteRequest request) {
        Route route = findOwnedRoute(routeId, userId);
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw ApiException.badRequest("Route name must not be blank");
            }
            route.setName(request.getName().trim());
        }
        if (request.getIsActive() != null) {
            route.setIsActive(request.getIsActive());
        }
        return routeRepository.save(route);
    }

    @Transactional
    public void deleteRoute(UUID routeId, UUID userId) {
        routeRepository.delete(findOwnedRoute(routeId, userId));
    }

    // ---------------------------------------------------------------- scoring

    /**
     * Re-scores one monitored route; used by the scheduler. Runs in its own transaction
     * so one failing route does not affect the others.
     */
    @Transactional
    public void computeScoreForRoute(UUID routeId) {
        routeRepository.findById(routeId).ifPresent(route -> scoreRoute(route, route.getUser()));
    }

    private void scoreRoute(Route route, User user) {
        RouteMetrics metrics = pollutionEngineService.calculateExposureScore(route, user);
        if (metrics.getPollutionScore() == null) {
            logger.warn("Skipping score update for route {}: no air-quality data", route.getId());
            return;
        }
        Double previousAverage = route.getAvgPollutionScore();
        metricsRepository.save(metrics);

        route.setLastPollutionScore(metrics.getPollutionScore());
        route.setLastCheckedAt(ZonedDateTime.now());
        route.setAvgPollutionScore(metricsRepository.findAveragePollutionScoreByRouteId(route.getId()));
        routeRepository.save(route);

        notificationEngineService.analyzeAndNotify(route, metrics, previousAverage);
    }

    // ---------------------------------------------------------------- history

    /**
     * Returns the route's scores over the last {@code days} days, plus the mean score for
     * each hour of the day in {@code timeZone}, and the cleanest and most polluted hours.
     */
    @Transactional(readOnly = true)
    public RouteHistoryDto getHistory(UUID routeId, UUID userId, int days, String timeZone) {
        findOwnedRoute(routeId, userId);
        int window = Math.max(1, Math.min(days, MAX_HISTORY_DAYS));
        ZoneId zone = parseZone(timeZone);

        List<RouteMetrics> metrics = metricsRepository.findByRouteIdAndTimestampAfterOrderByTimestampAsc(
                routeId, ZonedDateTime.now().minusDays(window));

        Map<Integer, Double> hourly = metrics.stream()
                .filter(m -> m.getPollutionScore() != null && m.getTimestamp() != null)
                .collect(Collectors.groupingBy(m -> m.getTimestamp().withZoneSameInstant(zone).getHour(),
                        TreeMap::new,
                        Collectors.averagingDouble(RouteMetrics::getPollutionScore)));
        hourly.replaceAll((h, v) -> Math.round(v * 10) / 10.0);

        Integer best = hourly.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        Integer worst = hourly.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        return new RouteHistoryDto(routeId, window, zone.getId(),
                metrics.stream().map(RouteMetricsDto::from).toList(), hourly, best, worst);
    }

    // ---------------------------------------------------------------- helpers

    private Route findOwnedRoute(UUID routeId, UUID userId) {
        // Report "not found" for other users' routes too, so route ids cannot be probed.
        return routeRepository.findByIdAndUserId(routeId, userId)
                .orElseThrow(() -> ApiException.notFound("Route not found"));
    }

    private LineString toLineString(List<List<Double>> coords) {
        Coordinate[] coordinates = coords.stream()
                .map(c -> new Coordinate(c.get(0), c.get(1)))
                .toArray(Coordinate[]::new);
        return geometryFactory.createLineString(coordinates);
    }

    /** Each point must be [longitude, latitude] within valid ranges. */
    static void validateCoordinates(List<List<Double>> coords) {
        if (coords == null || coords.size() < 2) {
            throw ApiException.badRequest("A route needs at least a start and an end point");
        }
        for (List<Double> c : coords) {
            if (c == null || c.size() != 2 || c.get(0) == null || c.get(1) == null) {
                throw ApiException.badRequest("Each point must be [longitude, latitude]");
            }
            double lon = c.get(0), lat = c.get(1);
            if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
                throw ApiException.badRequest("Coordinates out of range: [" + lon + ", " + lat + "]");
            }
        }
    }

    private static ZoneId parseZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw ApiException.badRequest("Unknown time zone: " + timeZone);
        }
    }
}
