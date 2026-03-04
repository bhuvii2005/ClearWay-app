package com.cleanroute.api.service.integration;

import com.cleanroute.api.dto.OpenMeteoAqiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OpenMeteoAqiClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenMeteoAqiClient.class);
    private static final String OPEN_METEO_URL = "https://air-quality-api.open-meteo.com/v1/air-quality";
    
    private final RestTemplate restTemplate;

    public OpenMeteoAqiClient() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Fetches current AQI for a given latitude and longitude.
     */
    public Double getCurrentAqi(double latitude, double longitude) {
        String url = UriComponentsBuilder.fromUriString(OPEN_METEO_URL)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "european_aqi")
                .queryParam("timezone", "auto")
                .toUriString();

        try {
            OpenMeteoAqiResponse response = restTemplate.getForObject(url, OpenMeteoAqiResponse.class);
            
            if (response != null && response.getHourly() != null 
                    && response.getHourly().getEuropean_aqi() != null
                    && !response.getHourly().getEuropean_aqi().isEmpty()) {
                
                // Open-Meteo returns an array of hours. Get the current hour's AQI.
                int currentHourIndex = getCurrentHourIndex(response);
                return response.getHourly().getEuropean_aqi().get(currentHourIndex);
            }
        } catch (Exception e) {
            logger.error("Failed to fetch AQI for lat: {}, lon: {}", latitude, longitude, e);
        }
        
        return null;
    }

    private int getCurrentHourIndex(OpenMeteoAqiResponse response) {
        // Open Meteo returns historical and forecast data.
        // For simplicity, we assume index 0 is the start of the current day.
        // A more robust implementation would search `response.getHourly().getTime()` 
        // for the current LocalDateTime truncated to hours.
        LocalDateTime now = LocalDateTime.now();
        String currentHourPrefix = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"));
        
        for (int i = 0; i < response.getHourly().getTime().size(); i++) {
            if (response.getHourly().getTime().get(i).startsWith(currentHourPrefix)) {
                return i;
            }
        }
        return 0; // Fallback to first element if not found
    }
}
