package com.cleanroute.api.scheduler;

import com.cleanroute.api.repository.RouteRepository;
import com.cleanroute.api.service.RouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Re-scores every monitored route on a fixed interval (15 minutes by default),
 * reading routes in pages so memory use stays flat as the number of routes grows.
 */
@Component
public class PollutionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PollutionScheduler.class);
    private static final int PAGE_SIZE = 100;

    private final RouteRepository routeRepository;
    private final RouteService routeService;
    private final boolean enabled;

    public PollutionScheduler(RouteRepository routeRepository, RouteService routeService,
                              @Value("${pollution.scheduler.enabled:true}") boolean enabled) {
        this.routeRepository = routeRepository;
        this.routeService = routeService;
        this.enabled = enabled;
    }

    @Scheduled(fixedRateString = "${pollution.scheduler.interval:900000}",
               initialDelayString = "${pollution.scheduler.initial-delay:60000}")
    public void evaluateActiveRoutes() {
        if (!enabled) {
            return;
        }
        long started = System.currentTimeMillis();
        int evaluated = 0;
        int failed = 0;

        Pageable page = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));
        Slice<UUID> slice;
        do {
            slice = routeRepository.findActiveRouteIds(page);
            for (UUID routeId : slice) {
                try {
                    routeService.computeScoreForRoute(routeId);
                    evaluated++;
                } catch (RuntimeException e) {
                    logger.error("Failed to evaluate route {}: {}", routeId, e.getMessage());
                    failed++;
                }
            }
            page = slice.nextPageable();
        } while (slice.hasNext());

        logger.info("Scheduled evaluation finished: {} routes scored, {} failed, {} ms",
                evaluated, failed, System.currentTimeMillis() - started);
    }
}
