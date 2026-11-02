package com.cleanroute.api.service;

import com.cleanroute.api.entity.Notification;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
import com.cleanroute.api.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * Decides when a fresh score on a monitored route deserves an alert.
 *
 * <ul>
 *   <li>POOR_AIR: score above {@code poorThreshold} (European AQI 75 by default).</li>
 *   <li>CLEAN_AIR: score at least 30% below the route's historical average.</li>
 * </ul>
 * At most one alert per route is sent within the cooldown window, so the 15-minute
 * scheduler does not repeat the same message.
 */
@Service
public class NotificationEngineService {

    static final double CLEAN_RATIO = 0.7;

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final double poorThreshold;
    private final Duration cooldown;

    public NotificationEngineService(NotificationService notificationService,
                                     NotificationRepository notificationRepository,
                                     @Value("${alerts.poor-threshold:75}") double poorThreshold,
                                     @Value("${alerts.cooldown-minutes:120}") long cooldownMinutes) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.poorThreshold = poorThreshold;
        this.cooldown = Duration.ofMinutes(cooldownMinutes);
    }

    /**
     * @param previousAverage the route's average score before this run (null when unknown)
     */
    public void analyzeAndNotify(Route route, RouteMetrics metrics, Double previousAverage) {
        Double score = metrics.getPollutionScore();
        if (score == null || route.getUser() == null) {
            return;
        }

        Notification.Kind kind;
        String message;
        long rounded = Math.round(score);
        if (score > poorThreshold) {
            kind = Notification.Kind.POOR_AIR;
            message = "Air quality on \"" + route.getName() + "\" is poor right now (score " + rounded
                    + "). Consider another route or time.";
        } else if (previousAverage != null && previousAverage > 0 && score < previousAverage * CLEAN_RATIO) {
            kind = Notification.Kind.CLEAN_AIR;
            message = "Air on \"" + route.getName() + "\" is unusually clean right now (score " + rounded
                    + " vs a usual " + Math.round(previousAverage) + "). A good time to go.";
        } else {
            return;
        }

        if (notificationRepository.existsByRouteIdAndCreatedAtAfter(route.getId(), ZonedDateTime.now().minus(cooldown))) {
            return;
        }
        notificationService.send(route, kind, message, score);
    }
}
