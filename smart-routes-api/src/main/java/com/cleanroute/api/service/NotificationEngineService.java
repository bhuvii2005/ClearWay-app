package com.cleanroute.api.service;

import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationEngineService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationEngineService.class);

    // Thresholds
    private static final double PM25_POOR_THRESHOLD = 35.0; 
    private static final double EUROPEAN_AQI_POOR_THRESHOLD = 75.0;

    /**
     * Checks if a score breaches thresholds and triggers notifications.
     */
    public void analyzeAndNotify(Route route, RouteMetrics metrics) {
        if (metrics.getPollutionScore() == null) {
            return;
        }

        if (metrics.getPollutionScore() > EUROPEAN_AQI_POOR_THRESHOLD) {
            sendNotification(route, "Air quality is poor (" + metrics.getPollutionScore() + " AQI). Avoid this route if possible.");
        } 
        else if (isSignificantlyBetterThanAverage(route, metrics)) {
            sendNotification(route, "Air quality on your route is exceptionally clear today (" + metrics.getPollutionScore() + " AQI). Great time for a run!");
        }
    }

    private boolean isSignificantlyBetterThanAverage(Route route, RouteMetrics metrics) {
        if (route.getAvgPollutionScore() == null || route.getAvgPollutionScore() == 0) return false;
        
        // If current score is 30% better than average
        return metrics.getPollutionScore() < (route.getAvgPollutionScore() * 0.7);
    }

    private void sendNotification(Route route, String message) {
        // In a real MVP, this would call FCM (Firebase Cloud Messaging), 
        // Twilio, Amazon SES, or a WebSocket to push to the React UI.
        
        logger.info("-----------------------------------------------------------------");
        logger.info("NOTIFICATION TRIGGERED");
        logger.info("User: {}", route.getUser().getEmail());
        logger.info("Route: {}", route.getName());
        logger.info("Message: {}", message);
        logger.info("-----------------------------------------------------------------");
    }
}
