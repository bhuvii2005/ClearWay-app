package com.cleanroute.api.service.integration;

import com.cleanroute.api.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class TomTomRoutingClientTest {

    @Test
    void travelModes_MapToTomTom() {
        assertEquals("pedestrian", TomTomRoutingClient.travelMode("walking"));
        assertEquals("pedestrian", TomTomRoutingClient.travelMode("jogging"));
        assertEquals("bicycle", TomTomRoutingClient.travelMode("cycling"));
        assertEquals("car", TomTomRoutingClient.travelMode("driving"));
        assertEquals("pedestrian", TomTomRoutingClient.travelMode(null));
    }

    @Test
    void missingApiKey_Gives503() {
        TomTomRoutingClient client = new TomTomRoutingClient(new RestTemplate(), "");
        assertFalse(client.isConfigured());
        ApiException ex = assertThrows(ApiException.class,
                () -> client.getDirections(28.6, 77.2, 28.7, 77.3, "walking"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }
}
