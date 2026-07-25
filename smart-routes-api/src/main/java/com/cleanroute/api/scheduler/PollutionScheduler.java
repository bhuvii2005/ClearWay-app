package com.cleanroute.api.scheduler;

import com.cleanroute.api.entity.Route;
import com.cleanroute.api.repository.RouteRepository;
import com.cleanroute.api.service.RouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PollutionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PollutionScheduler.class);

    private final RouteRepository routeRepository;
    private final RouteService routeService;

    public PollutionScheduler(RouteRepository routeRepository, RouteService routeService) {
        this.routeRepository = routeRepository;
        this.routeService = routeService;
    }

    /**
     * Runs every 15 minutes to compute the pollution score for all active routes.
     * Scheduled natively using Spring's @Scheduled annotation.
     */
    @Scheduled(fixedRateString = "${pollution.scheduler.interval:900000}")
    public void evaluateActiveRoutes() {
        logger.info("Starting scheduled evaluation of active routes...");
        
        List<Route> routes = routeRepository.findByIsActiveTrue();
        
        int evaluated = 0;
        int failed = 0;
        for (Route route : routes) {
            try {
                routeService.computeScoreForRoute(route);
                evaluated++;
            } catch (Exception e) {
                logger.error("Failed to evaluate route {}: {}", route.getId(), e.getMessage());
                failed++;
            }
        }
        
        logger.info("Completed evaluation: {} succeeded, {} failed.", evaluated, failed);
    }
}
