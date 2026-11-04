package com.cleanroute.api.service;

import com.cleanroute.api.dto.PollutionData;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Computes a route's pollution exposure score.
 *
 * <p>Up to {@code maxSamples} points are sampled evenly along the route (always including both ends).
 * For each point the base score is the European AQI. When the user is sensitive to a pollutant,
 * that pollutant's concentration (µg/m³) is added with a weight:
 * PM2.5 × 1.5, ozone × 1.2, NO₂ × 1.2, PM10 × 1.0.
 * The route score is the mean of the point scores; lower is cleaner.
 *
 * <p>If no point could be sampled (air-quality service down), the returned metrics have a null score.
 */
@Service
public class PollutionEngineService {

    private static final Logger logger = LoggerFactory.getLogger(PollutionEngineService.class);

    static final double PM25_WEIGHT = 1.5;
    static final double OZONE_WEIGHT = 1.2;
    static final double NO2_WEIGHT = 1.2;
    static final double PM10_WEIGHT = 1.0;

    private final OpenMeteoAqiClient aqiClient;
    private final int maxSamples;

    public PollutionEngineService(OpenMeteoAqiClient aqiClient,
                                  @Value("${pollution.max-samples-per-route:10}") int maxSamples) {
        this.aqiClient = aqiClient;
        this.maxSamples = Math.max(2, maxSamples);
    }

    public RouteMetrics calculateExposureScore(Route route, User user) {
        Coordinate[] coordinates = route.getPath().getCoordinates();
        if (coordinates.length == 0) {
            throw new IllegalArgumentException("Route path must have at least one coordinate");
        }

        RouteMetrics metrics = new RouteMetrics();
        double scoreSum = 0, aqiSum = 0, pm25Sum = 0, pm10Sum = 0, no2Sum = 0, ozoneSum = 0;
        int samples = 0;

        for (int index : sampleIndices(coordinates.length, maxSamples)) {
            Coordinate coord = coordinates[index];
            PollutionData data = aqiClient.getPollutionData(coord.y, coord.x); // y = latitude, x = longitude
            if (data == null) {
                continue;
            }
            scoreSum += pointScore(data, user);
            aqiSum += data.getAqi();
            pm25Sum += data.getPm25();
            pm10Sum += data.getPm10();
            no2Sum += data.getNo2();
            ozoneSum += data.getOzone();
            samples++;
            metrics.getSamples().add(java.util.List.of(coord.x, coord.y, data.getAqi()));
        }

        metrics.setRoute(route);
        metrics.setTrafficIndex(1.0);
        if (samples == 0) {
            logger.warn("No air-quality samples available for route {}", route.getId());
            return metrics;
        }
        metrics.setPollutionScore(round1(scoreSum / samples));
        metrics.setAqi(round1(aqiSum / samples));
        metrics.setPm25(round1(pm25Sum / samples));
        metrics.setPm10(round1(pm10Sum / samples));
        metrics.setNo2(round1(no2Sum / samples));
        metrics.setOzone(round1(ozoneSum / samples));
        return metrics;
    }

    static double pointScore(PollutionData data, User user) {
        double score = data.getAqi();
        if (user != null) {
            if (Boolean.TRUE.equals(user.getAvoidPm25())) score += data.getPm25() * PM25_WEIGHT;
            if (Boolean.TRUE.equals(user.getAvoidOzone())) score += data.getOzone() * OZONE_WEIGHT;
            if (Boolean.TRUE.equals(user.getAvoidPm10())) score += data.getPm10() * PM10_WEIGHT;
            if (Boolean.TRUE.equals(user.getAvoidNo2())) score += data.getNo2() * NO2_WEIGHT;
        }
        return score;
    }

    /** Evenly spaced, distinct indices from 0 to length-1 inclusive, at most maxSamples of them. */
    static int[] sampleIndices(int length, int maxSamples) {
        int count = Math.min(length, maxSamples);
        if (count == 1) {
            return new int[]{0};
        }
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = (int) Math.round((double) i * (length - 1) / (count - 1));
        }
        return indices;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
