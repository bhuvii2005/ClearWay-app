package com.cleanroute.api.service.integration;

import com.cleanroute.api.dto.OpenMeteoAqiResponse;
import com.cleanroute.api.dto.PollutionData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client for the free Open-Meteo Air Quality API (no API key required).
 *
 * Results are cached per ~1 km grid cell (coordinates rounded to 0.01 degrees) for 15 minutes,
 * which matches Open-Meteo's update frequency and keeps request volume low.
 * Returns null when the service is unavailable; callers decide how to degrade.
 */
@Service
public class OpenMeteoAqiClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenMeteoAqiClient.class);
    private static final String OPEN_METEO_URL = "https://air-quality-api.open-meteo.com/v1/air-quality";
    private static final String CURRENT_FIELDS = "european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone";

    private final RestTemplate restTemplate;

    public OpenMeteoAqiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "aqi",
            key = "T(java.lang.Math).round(#latitude * 100) + ',' + T(java.lang.Math).round(#longitude * 100)",
            unless = "#result == null")
    @CircuitBreaker(name = "openMeteo", fallbackMethod = "getPollutionDataFallback")
    @Retry(name = "openMeteo")
    public PollutionData getPollutionData(double latitude, double longitude) {
        double clampedLat = Math.max(-90.0, Math.min(90.0, latitude));
        double normalizedLon = ((longitude + 180) % 360 + 360) % 360 - 180;

        String url = UriComponentsBuilder.fromUriString(OPEN_METEO_URL)
                .queryParam("latitude", clampedLat)
                .queryParam("longitude", normalizedLon)
                .queryParam("current", CURRENT_FIELDS)
                .queryParam("timezone", "GMT")
                .toUriString();

        OpenMeteoAqiResponse response = restTemplate.getForObject(url, OpenMeteoAqiResponse.class);
        if (response == null || response.current == null || response.current.european_aqi == null) {
            return null;
        }
        OpenMeteoAqiResponse.Current c = response.current;
        return new PollutionData(
                c.european_aqi,
                orZero(c.pm2_5),
                orZero(c.pm10),
                orZero(c.nitrogen_dioxide),
                orZero(c.ozone),
                orZero(c.carbon_monoxide));
    }

    @SuppressWarnings("unused") // invoked by Resilience4j
    public PollutionData getPollutionDataFallback(double latitude, double longitude, Throwable t) {
        logger.warn("Open-Meteo unavailable for ({}, {}): {}", latitude, longitude, t.getMessage());
        return null;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
