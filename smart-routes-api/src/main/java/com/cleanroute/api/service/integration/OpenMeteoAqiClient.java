package com.cleanroute.api.service.integration;

import com.cleanroute.api.dto.OpenMeteoAqiResponse;
import com.cleanroute.api.dto.PollutionData;
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
        PollutionData data = getPollutionData(latitude, longitude);
        return data != null ? data.getAqi() : null;
    }

    /**
     * Fetches detailed pollution data (AQI, PM2.5, PM10, CO, NO2, Ozone) for a given location.
     */
    public PollutionData getPollutionData(double latitude, double longitude) {
        double clampedLat = Math.max(-90.0, Math.min(90.0, latitude));
        double lon = (longitude - 180) % 360;
        double normalizedLon = (lon > 0) ? (lon - 180) : (lon + 180);

        String url = UriComponentsBuilder.fromUriString(OPEN_METEO_URL)
                .queryParam("latitude", clampedLat)
                .queryParam("longitude", normalizedLon)
                .queryParam("hourly", "european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone")
                .queryParam("timezone", "auto")
                .toUriString();

        try {
            OpenMeteoAqiResponse response = restTemplate.getForObject(url, OpenMeteoAqiResponse.class);
            
            if (response != null && response.getHourly() != null) {
                int index = getCurrentHourIndex(response);
                
                double aqi = getValOrDefault(response.getHourly().getEuropean_aqi(), index, 50.0);
                double pm25 = getValOrDefault(response.getHourly().getPm2_5(), index, 0.0);
                double pm10 = getValOrDefault(response.getHourly().getPm10(), index, 0.0);
                double co = getValOrDefault(response.getHourly().getCarbon_monoxide(), index, 0.0);
                double no2 = getValOrDefault(response.getHourly().getNitrogen_dioxide(), index, 0.0);
                double ozone = getValOrDefault(response.getHourly().getOzone(), index, 0.0);
                
                return new PollutionData(aqi, pm25, pm10, no2, ozone, co);
            }
        } catch (Exception e) {
            logger.error("Failed to fetch pollution data for lat: {}, lon: {}", latitude, longitude, e);
        }
        
        return null;
    }

    private double getValOrDefault(java.util.List<Double> list, int index, double defaultVal) {
        if (list != null && index >= 0 && index < list.size() && list.get(index) != null) {
            return list.get(index);
        }
        return defaultVal;
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
