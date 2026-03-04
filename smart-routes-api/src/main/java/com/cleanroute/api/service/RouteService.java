package com.cleanroute.api.service;

import com.cleanroute.api.dto.CreateRouteRequest;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.repository.RouteMetricsRepository;
import com.cleanroute.api.repository.RouteRepository;
import com.cleanroute.api.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);

    private final RouteRepository routeRepository;
    private final UserRepository userRepository;
    private final RouteMetricsRepository metricsRepository;
    private final PollutionEngineService pollutionEngineService;
    private final NotificationEngineService notificationEngineService;
    private final GeometryFactory geometryFactory;

    public RouteService(RouteRepository routeRepository,
                        UserRepository userRepository,
                        RouteMetricsRepository metricsRepository,
                        PollutionEngineService pollutionEngineService,
                        NotificationEngineService notificationEngineService) {
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.metricsRepository = metricsRepository;
        this.pollutionEngineService = pollutionEngineService;
        this.notificationEngineService = notificationEngineService;
        this.geometryFactory = new GeometryFactory();
    }

    public Route createRoute(CreateRouteRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Route route = new Route();
        route.setUser(user);
        route.setName(request.getName());
        route.setType(request.getType());
        
        // Convert List of coordinates back to PostGIS LineString
        Coordinate[] coordinates = request.getPathCoordinates().stream()
                .map(coord -> new Coordinate(coord.get(0), coord.get(1)))
                .toArray(Coordinate[]::new);
                
        LineString path = geometryFactory.createLineString(coordinates);
        path.setSRID(4326);
        route.setPath(path);
        
        // Save initial route immediately to construct table lock and generate UUID
        route = routeRepository.save(route);

        // Fetch API independently, and save metrics + route updates sequentially
        computeScoreForRoute(route);

        return route;
    }

    public List<Route> getActiveRoutesForUser(UUID userId) {
        return routeRepository.findByUserIdAndIsActiveTrue(userId);
    }

    public void computeScoreForRoute(Route route) {
        try {
            RouteMetrics metrics = pollutionEngineService.calculateExposureScore(route);
            metrics = metricsRepository.save(metrics);
            
            route.setLastPollutionScore(metrics.getPollutionScore());
            route.setLastCheckedAt(ZonedDateTime.now());
            
            // Recalculate average (simplified for MVP)
            Double avg = route.getAvgPollutionScore() == null ? 
                    metrics.getPollutionScore() : 
                    (route.getAvgPollutionScore() + metrics.getPollutionScore()) / 2;
            route.setAvgPollutionScore(avg);
            
            routeRepository.save(route);

            // Trigger notification rules
            notificationEngineService.analyzeAndNotify(route, metrics);
            
        } catch (Exception e) {
            logger.error("Error computing score for route {}", route.getId(), e);
        }
    }
}
