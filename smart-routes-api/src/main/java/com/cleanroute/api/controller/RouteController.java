package com.cleanroute.api.controller;

import com.cleanroute.api.dto.*;
import com.cleanroute.api.service.RouteService;
import com.cleanroute.api.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Route endpoints. All require a Bearer token and only ever expose the caller's own routes.
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    /** Up to three road-snapped routes between two points, cleanest first. Nothing is saved. */
    @PostMapping("/alternatives")
    public List<AlternativeRouteDto> computeAlternativeRoutes(@Valid @RequestBody AlternativesRequest request) {
        return routeService.fetchAlternatives(CurrentUser.id(), request);
    }

    /** Saves a route (usually one of the alternatives) and scores it immediately. */
    @PostMapping
    public ResponseEntity<RouteDto> createRoute(@Valid @RequestBody CreateRouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RouteDto(routeService.createRoute(CurrentUser.id(), request)));
    }

    /** The caller's saved routes, newest first, without geometry. */
    @GetMapping
    public List<RouteDto> listRoutes() {
        return routeService.getRoutesForUser(CurrentUser.id()).stream().map(RouteDto::new).toList();
    }

    /** One saved route including its geometry. */
    @GetMapping("/{routeId}")
    public RouteDetailDto getRoute(@PathVariable UUID routeId) {
        return routeService.getRoute(routeId, CurrentUser.id());
    }

    /** Renames a route or turns monitoring on or off. */
    @PatchMapping("/{routeId}")
    public RouteDto updateRoute(@PathVariable UUID routeId, @Valid @RequestBody UpdateRouteRequest request) {
        return new RouteDto(routeService.updateRoute(routeId, CurrentUser.id(), request));
    }

    @DeleteMapping("/{routeId}")
    public ResponseEntity<Void> deleteRoute(@PathVariable UUID routeId) {
        routeService.deleteRoute(routeId, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * Exposure history for the last {@code days} days (1-90, default 7), with hourly averages
     * computed in the IANA time zone {@code tz} (default UTC).
     */
    @GetMapping("/{routeId}/metrics")
    public RouteHistoryDto getRouteMetrics(@PathVariable UUID routeId,
                                           @RequestParam(defaultValue = "7") int days,
                                           @RequestParam(defaultValue = "UTC") String tz) {
        return routeService.getHistory(routeId, CurrentUser.id(), days, tz);
    }
}
