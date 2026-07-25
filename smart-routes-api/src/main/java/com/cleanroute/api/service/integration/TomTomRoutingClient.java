package com.cleanroute.api.service.integration;

import com.cleanroute.api.dto.TomTomRouteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;

@Service
public class TomTomRoutingClient {

    private static final Logger logger = LoggerFactory.getLogger(TomTomRoutingClient.class);
    private static final String TOMTOM_URL = "https://api.tomtom.com/routing/1/calculateRoute";

    @Value("${tomtom.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public TomTomRoutingClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Gets alternative routes between start [lat, lon] and end [lat, lon].
     */
    @CircuitBreaker(name = "tomtom", fallbackMethod = "getDirectionsFallback")
    @Retry(name = "tomtom")
    public TomTomRouteResponse getDirections(double startLat, double startLon, double endLat, double endLon) {
        String coordinates = startLat + "," + startLon + ":" + endLat + "," + endLon;
        
        String url = UriComponentsBuilder.fromUriString(TOMTOM_URL + "/" + coordinates + "/json")
                .queryParam("key", apiKey)
                .queryParam("maxAlternatives", 2)
                .toUriString();

        return restTemplate.getForObject(url, TomTomRouteResponse.class);
    }

    public TomTomRouteResponse getDirectionsFallback(double startLat, double startLon, double endLat, double endLon, Throwable t) {
        logger.warn("TomTom API failed or circuit broken. Reason: {}", t.getMessage());
        TomTomRouteResponse emptyResponse = new TomTomRouteResponse();
        emptyResponse.setRoutes(Collections.emptyList());
        return emptyResponse;
    }
}
