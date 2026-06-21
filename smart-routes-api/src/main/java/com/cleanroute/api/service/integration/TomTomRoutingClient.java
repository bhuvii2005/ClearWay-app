package com.cleanroute.api.service.integration;

import com.cleanroute.api.dto.TomTomRouteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TomTomRoutingClient {

    private static final String TOMTOM_URL = "https://api.tomtom.com/routing/1/calculateRoute";

    @Value("${tomtom.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public TomTomRoutingClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Gets alternative routes between start [lat, lon] and end [lat, lon].
     */
    public TomTomRouteResponse getDirections(double startLat, double startLon, double endLat, double endLon) {
        String coordinates = startLat + "," + startLon + ":" + endLat + "," + endLon;
        
        String url = UriComponentsBuilder.fromUriString(TOMTOM_URL + "/" + coordinates + "/json")
                .queryParam("key", apiKey)
                .queryParam("maxAlternatives", 2)
                .toUriString();

        try {
            return restTemplate.getForObject(url, TomTomRouteResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch directions from TomTom: " + e.getMessage(), e);
        }
    }
}
