package com.cleanroute.api.service.integration;

import com.cleanroute.api.dto.TomTomRouteResponse;
import com.cleanroute.api.exception.ApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;

/**
 * Client for the TomTom Routing API (free tier, API key required).
 * Returns the fastest route plus up to two alternatives, snapped to real roads.
 */
@Service
public class TomTomRoutingClient {

    private static final Logger logger = LoggerFactory.getLogger(TomTomRoutingClient.class);
    private static final String TOMTOM_URL = "https://api.tomtom.com/routing/1/calculateRoute";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public TomTomRoutingClient(RestTemplate restTemplate, @Value("${tomtom.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * @param routeType ClearWay route type (walking, jogging, cycling, driving, commute)
     */
    @CircuitBreaker(name = "tomtom", fallbackMethod = "getDirectionsFallback")
    @Retry(name = "tomtom")
    public TomTomRouteResponse getDirections(double startLat, double startLon, double endLat, double endLon,
                                             String routeType) {
        if (!isConfigured()) {
            throw ApiException.serviceUnavailable("Routing is not configured: set TOMTOM_API_KEY");
        }
        String coordinates = startLat + "," + startLon + ":" + endLat + "," + endLon;
        String url = UriComponentsBuilder.fromUriString(TOMTOM_URL + "/" + coordinates + "/json")
                .queryParam("key", apiKey)
                .queryParam("maxAlternatives", 2)
                .queryParam("travelMode", travelMode(routeType))
                .toUriString();

        return restTemplate.getForObject(url, TomTomRouteResponse.class);
    }

    @SuppressWarnings("unused") // invoked by Resilience4j
    public TomTomRouteResponse getDirectionsFallback(double startLat, double startLon, double endLat, double endLon,
                                                     String routeType, Throwable t) {
        if (t instanceof ApiException apiException) {
            throw apiException;
        }
        logger.warn("TomTom routing failed: {}", t.getMessage());
        throw ApiException.serviceUnavailable("The routing service is currently unavailable. Please try again shortly.");
    }

    /** Maps a ClearWay route type to a TomTom travel mode. */
    static String travelMode(String routeType) {
        if (routeType == null) {
            return "pedestrian";
        }
        return switch (routeType.toLowerCase(Locale.ROOT)) {
            case "cycling" -> "bicycle";
            case "driving", "commute" -> "car";
            default -> "pedestrian"; // walking, jogging
        };
    }
}
