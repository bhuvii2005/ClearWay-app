package com.cleanroute.api.service;

import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
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
        
        LineString path = route.getPath();
        Coordinate[] coordinates = path.getCoordinates();

        if (coordinates.length == 0) {
            throw new IllegalArgumentException("Route path must have at least one coordinate");
        }

        double totalAqi = 0.0;
        int sampleCount = 0;

        // Sample every Nth coordinate to avoid excessive API calls
        // For short routes, sample every coordinate. For long routes, sample 10 points.
        int step = Math.max(1, coordinates.length / 10);

        for (int i = 0; i < coordinates.length; i += step) {
            Coordinate coord = coordinates[i];
            Double currentAqi = aqiClient.getCurrentAqi(coord.y, coord.x); // Y is lat, X is lon
            
            if (currentAqi != null) {
                totalAqi += currentAqi;
                sampleCount++;
            }
        }

        Double avgAqi;
        if (sampleCount == 0) {
            logger.warn("Could not fetch AQI for any coordinate on route: {}", route.getId());
            avgAqi = 50.0; // Fallback "Moderate" score
        } else {
            avgAqi = totalAqi / sampleCount;
        }

        // Apply hypothetical traffic factor if available (Optional for MVP).
        double trafficFactor = 1.0; 
        double distanceWeight = route.getDistanceKm() != null ? route.getDistanceKm() : 1.0;
        
        // Final exposure score balances the AQI and the distance spent traveling
        double exposureScore = avgAqi * trafficFactor;

        RouteMetrics metrics = new RouteMetrics();
        metrics.setRoute(route);
        metrics.setPollutionScore(exposureScore);
        metrics.setTrafficIndex(trafficFactor);
        
        return metrics;
    }
}
