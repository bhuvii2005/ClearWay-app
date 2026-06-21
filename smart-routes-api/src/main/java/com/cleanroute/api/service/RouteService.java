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
import com.cleanroute.api.dto.AlternativeRouteDto;
import com.cleanroute.api.dto.TomTomRouteResponse;
import com.cleanroute.api.service.integration.TomTomRoutingClient;

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
    private final TomTomRoutingClient tomtomClient;
    private final GeometryFactory geometryFactory;

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
            User user = null;
            if (route.getUser() != null) {
                user = userRepository.findById(route.getUser().getId()).orElse(null);
                if (user != null) {
                    route.setUser(user);
                }
            }

            RouteMetrics metrics = pollutionEngineService.calculateExposureScore(route, user);
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

    public List<AlternativeRouteDto> fetchAlternatives(CreateRouteRequest request) {
        List<Double> start = request.getPathCoordinates().get(0); // [lon, lat]
        List<Double> end = request.getPathCoordinates().get(1);

        // Query TomTom (TomTom uses lat, lon)
        TomTomRouteResponse tomtomRes = tomtomClient.getDirections(
                start.get(1), start.get(0), end.get(1), end.get(0));

        // Load User to check allergy preferences
        User user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId()).orElse(null);
        }

        List<AlternativeRouteDto> alternatives = new java.util.ArrayList<>();
        
        for (TomTomRouteResponse.TomTomRoute ttRoute : tomtomRes.getRoutes()) {
            List<Coordinate> coords = new java.util.ArrayList<>();
            List<List<Double>> rawCoords = new java.util.ArrayList<>();
            
            if (ttRoute.getLegs() != null) {
                for (TomTomRouteResponse.TomTomLeg leg : ttRoute.getLegs()) {
                    for (TomTomRouteResponse.TomTomPoint pt : leg.getPoints()) {
                        coords.add(new Coordinate(pt.getLongitude(), pt.getLatitude()));
                        rawCoords.add(List.of(pt.getLongitude(), pt.getLatitude()));
                    }
                }
            }
            
            LineString path = geometryFactory.createLineString(coords.toArray(new Coordinate[0]));
            path.setSRID(4326);
            
            // Temporary Route entity just for scoring
            Route tempRoute = new Route();
            tempRoute.setPath(path);
            if (ttRoute.getSummary() != null) {
                tempRoute.setDistanceKm(ttRoute.getSummary().getLengthInMeters() / 1000.0);
            }
            
            RouteMetrics metrics = pollutionEngineService.calculateExposureScore(tempRoute, user);
            
            AlternativeRouteDto dto = new AlternativeRouteDto();
            dto.setPathCoordinates(rawCoords);
            dto.setPollutionScore(metrics.getPollutionScore());
            
            if (ttRoute.getSummary() != null) {
                dto.setDistanceMeters(ttRoute.getSummary().getLengthInMeters());
                dto.setTravelTimeSeconds(ttRoute.getSummary().getTravelTimeInSeconds());
            }
            
            alternatives.add(dto);
        }
        
        // sort by pollution score lowest to highest
        alternatives.sort(java.util.Comparator.comparingDouble(AlternativeRouteDto::getPollutionScore));
        
        return alternatives;
    }
}
