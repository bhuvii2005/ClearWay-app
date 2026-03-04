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
        
        // In reality, this should be paginated/batched, but since this is MVP we fetch all.
        List<Route> routes = routeRepository.findAll();
        
        int evaluated = 0;
        for (Route route : routes) {
            if (Boolean.TRUE.equals(route.getIsActive())) {
                routeService.computeScoreForRoute(route);
                evaluated++;
            }
        }
        
        logger.info("Completed evaluation of {} active routes.", evaluated);
    }
}
