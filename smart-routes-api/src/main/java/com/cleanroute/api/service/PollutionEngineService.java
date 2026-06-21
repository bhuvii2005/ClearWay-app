package com.cleanroute.api.service;

import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.dto.PollutionData;
import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PollutionEngineService {

    private static final Logger logger = LoggerFactory.getLogger(PollutionEngineService.class);

    private final OpenMeteoAqiClient aqiClient;

    public PollutionEngineService(OpenMeteoAqiClient aqiClient) {
        this.aqiClient = aqiClient;
    }

    /**
     * Calculates the pollution exposure score for a given route.
     * Iterates over coordinate points along the route geometry,
     * queries AQI for a spatial sample, and averages the results.
     */
    public RouteMetrics calculateExposureScore(Route route) {
        return calculateExposureScore(route, null);
    }

    /**
     * Calculates the pollution exposure score for a given route,
     * applying weights for any user allergy preferences.
     */
    public RouteMetrics calculateExposureScore(Route route, User user) {
        LineString path = route.getPath();
        Coordinate[] coordinates = path.getCoordinates();

        if (coordinates.length == 0) {
            throw new IllegalArgumentException("Route path must have at least one coordinate");
        }

        double totalScore = 0.0;
        int sampleCount = 0;

        // Sample every Nth coordinate to avoid excessive API calls
        // For short routes, sample every coordinate. For long routes, sample 10 points.
        int step = Math.max(1, coordinates.length / 10);

        for (int i = 0; i < coordinates.length; i += step) {
            Coordinate coord = coordinates[i];
            PollutionData data = aqiClient.getPollutionData(coord.y, coord.x); // Y is lat, X is lon
            
            if (data != null) {
                double pointScore = data.getAqi(); // Base european AQI
                
                // Apply allergy penalties
                if (user != null) {
                    if (Boolean.TRUE.equals(user.getAvoidPm25())) {
                        pointScore += data.getPm25() * 1.5; // Penalize PM2.5 (ug/m3)
                    }
                    if (Boolean.TRUE.equals(user.getAvoidOzone())) {
                        pointScore += data.getOzone() * 1.2; // Penalize Ozone (ug/m3)
                    }
                    if (Boolean.TRUE.equals(user.getAvoidPm10())) {
                        pointScore += data.getPm10() * 1.0; // Penalize PM10 (ug/m3)
                    }
                    if (Boolean.TRUE.equals(user.getAvoidNo2())) {
                        pointScore += data.getNo2() * 1.2; // Penalize NO2 (ug/m3)
                    }
                }
                
                totalScore += pointScore;
                sampleCount++;
            }
        }

        Double avgScore;
        if (sampleCount == 0) {
            logger.warn("Could not fetch AQI for any coordinate on route: {}", route.getId());
            avgScore = 50.0; // Fallback "Moderate" score
        } else {
            avgScore = totalScore / sampleCount;
        }

        // Apply hypothetical traffic factor if available (Optional for MVP).
        double trafficFactor = 1.0; 
        
        // Final exposure score balances the AQI and the distance spent traveling
        double exposureScore = avgScore * trafficFactor;

        RouteMetrics metrics = new RouteMetrics();
        metrics.setRoute(route);
        metrics.setPollutionScore(exposureScore);
        metrics.setTrafficIndex(trafficFactor);
        
        return metrics;
    }
}
