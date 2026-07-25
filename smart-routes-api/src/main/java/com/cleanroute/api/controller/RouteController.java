package com.cleanroute.api.controller;

import com.cleanroute.api.dto.CreateRouteRequest;
import com.cleanroute.api.dto.ErrorResponse;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.dto.AlternativeRouteDto;
import com.cleanroute.api.service.RouteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private static final Logger logger = LoggerFactory.getLogger(RouteController.class);

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    private UUID getAuthenticatedUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        return null;
    }

    @PostMapping("/alternatives")
    public ResponseEntity<?> computeAlternativeRoutes(@Valid @RequestBody CreateRouteRequest request) {
        logger.info("Calculating alternatives for route: {}", request.getName());
        UUID currentUserId = getAuthenticatedUserId();
        if (currentUserId != null) {
            request.setUserId(currentUserId);
        }

        List<AlternativeRouteDto> alternatives = routeService.fetchAlternatives(request);
        return ResponseEntity.ok(alternatives);
    }

    @PostMapping
    public ResponseEntity<?> createRoute(@Valid @RequestBody CreateRouteRequest request) {
        logger.info("Received request to create route: {}", request.getName());
        UUID currentUserId = getAuthenticatedUserId();
        if (currentUserId != null) {
            request.setUserId(currentUserId);
        } else if (request.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(401, "Unauthorized", "User authentication required"));
        }

        Route createdRoute = routeService.createRoute(request);
        logger.info("Successfully created route with ID: {}", createdRoute.getId());
        return ResponseEntity.ok("Route Created successfully with ID: " + createdRoute.getId() + 
                                 " | Initial Score: " + createdRoute.getLastPollutionScore());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserRoutes(@PathVariable UUID userId) {
        UUID authUserId = getAuthenticatedUserId();
        if (authUserId != null && !authUserId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(403, "Forbidden", "Cannot view routes for another user"));
        }

        List<Route> routes = routeService.getActiveRoutesForUser(userId);
        
        List<String> simplifiedResponses = routes.stream()
                .map(r -> String.format("Route: %s | Score: %.2f | Checked: %s", 
                                        r.getName(), r.getLastPollutionScore(), r.getLastCheckedAt()))
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(simplifiedResponses);
    }
}
