package com.cleanroute.api.service;

import com.cleanroute.api.dto.PollutionData;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.service.integration.OpenMeteoAqiClient;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

class PollutionEngineServiceTest {

    private final OpenMeteoAqiClient client = mock(OpenMeteoAqiClient.class);
    private final PollutionEngineService engine = new PollutionEngineService(client, 10);

    private static Route route(int points) {
        Coordinate[] coords = new Coordinate[points];
        for (int i = 0; i < points; i++) {
            coords[i] = new Coordinate(77.0 + i * 0.001, 28.0);
        }
        Route route = new Route();
        route.setPath(new GeometryFactory().createLineString(coords));
        return route;
    }

    @Test
    void sampleIndices_CoverBothEndsWithoutDuplicates() {
        assertArrayEquals(new int[]{0, 1}, PollutionEngineService.sampleIndices(2, 10));
        int[] many = PollutionEngineService.sampleIndices(1000, 10);
        assertEquals(10, many.length);
        assertEquals(0, many[0]);
        assertEquals(999, many[9]);
        for (int i = 1; i < many.length; i++) {
            assertTrue(many[i] > many[i - 1]);
        }
    }

    @Test
    void score_IsMeanAqi_WithoutSensitivities() {
        when(client.getPollutionData(anyDouble(), anyDouble()))
                .thenReturn(new PollutionData(40, 10, 20, 30, 50, 0));

        RouteMetrics m = engine.calculateExposureScore(route(500), null);

        assertEquals(40.0, m.getPollutionScore());
        assertEquals(10.0, m.getPm25());
        verify(client, times(10)).getPollutionData(anyDouble(), anyDouble());
    }

    @Test
    void sensitivities_AddWeightedPollutants() {
        when(client.getPollutionData(anyDouble(), anyDouble()))
                .thenReturn(new PollutionData(40, 10, 20, 30, 50, 0));
        User user = new User();
        user.setAvoidPm25(true);  // + 10 * 1.5 = 15
        user.setAvoidNo2(true);   // + 30 * 1.2 = 36

        RouteMetrics m = engine.calculateExposureScore(route(5), user);

        assertEquals(91.0, m.getPollutionScore());
        assertEquals(40.0, m.getAqi(), "raw AQI is reported unweighted");
    }

    @Test
    void noData_GivesNullScore() {
        when(client.getPollutionData(anyDouble(), anyDouble())).thenReturn(null);
        assertNull(engine.calculateExposureScore(route(5), null).getPollutionScore());
    }
}
