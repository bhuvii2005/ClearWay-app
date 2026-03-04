package com.cleanroute.api.controller;

import com.cleanroute.api.dto.CreateRouteRequest;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.service.RouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/routes")
@CrossOrigin(origins = "*") // For React frontend testing
public class RouteController {

    private static final Logger logger = LoggerFactory.getLogger(RouteController.class);

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<?> createRoute(@RequestBody CreateRouteRequest request) {
        logger.info("Received request to create route: {}", request.getName());
        try {
            Route createdRoute = routeService.createRoute(request);
            logger.info("Successfully created route with ID: {}", createdRoute.getId());
            // In a real app we'd map this back to a detailed Response DTO to avoid Jackson spatial serialization issues
            return ResponseEntity.ok("Route Created successfully with ID: " + createdRoute.getId() + 
                                     " | Initial Score: " + createdRoute.getLastPollutionScore());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to create route: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserRoutes(@PathVariable UUID userId) {
        List<Route> routes = routeService.getActiveRoutesForUser(userId);
        
        List<String> simplifiedResponses = routes.stream()
                .map(r -> String.format("Route: %s | Score: %.2f | Checked: %s", 
                                        r.getName(), r.getLastPollutionScore(), r.getLastCheckedAt()))
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(simplifiedResponses);
    }
}
