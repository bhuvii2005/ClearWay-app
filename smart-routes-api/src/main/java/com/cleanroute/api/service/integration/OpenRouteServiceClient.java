package com.cleanroute.api.service.integration;

import com.cleanroute.api.dto.OpenRouteServiceDirectionsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenRouteServiceClient {

    private static final String ORS_URL = "https://api.openrouteservice.org/v2/directions/driving-car";

    @Value("${openrouteservice.api.key:YOUR_MOCK_KEY}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public OpenRouteServiceClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Gets a route between start [lon, lat] and end [lon, lat].
     */
    public OpenRouteServiceDirectionsResponse getDirections(List<Double> start, List<Double> end) {
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("coordinates", List.of(start, end));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<OpenRouteServiceDirectionsResponse> response = restTemplate.exchange(
                    ORS_URL,
                    HttpMethod.POST,
                    entity,
                    OpenRouteServiceDirectionsResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            // Log it and throw custom exception in a real prod app
            throw new RuntimeException("Failed to fetch directions from ORS: " + e.getMessage(), e);
        }
    }
}
