package com.cleanroute.api.service;

import com.cleanroute.api.entity.Notification;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.RouteMetrics;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationEngineServiceTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationEngineService engine =
            new NotificationEngineService(notificationService, repository, 75, 120);

    private static Route route() {
        User user = new User();
        user.setId(UUID.randomUUID());
        Route route = new Route();
        route.setId(UUID.randomUUID());
        route.setName("Morning run");
        route.setUser(user);
        return route;
    }

    private static RouteMetrics score(double value) {
        RouteMetrics m = new RouteMetrics();
        m.setPollutionScore(value);
        return m;
    }

    @Test
    void poorAir_SendsAlert() {
        Route route = route();
        engine.analyzeAndNotify(route, score(90), 50.0);
        verify(notificationService).send(eq(route), eq(Notification.Kind.POOR_AIR), contains("poor"), eq(90.0));
    }

    @Test
    void unusuallyClean_SendsAlert() {
        Route route = route();
        engine.analyzeAndNotify(route, score(20), 50.0);
        verify(notificationService).send(eq(route), eq(Notification.Kind.CLEAN_AIR), anyString(), eq(20.0));
    }

    @Test
    void normalAir_SendsNothing() {
        engine.analyzeAndNotify(route(), score(45), 50.0);
        verifyNoInteractions(notificationService);
    }

    @Test
    void withinCooldown_SendsNothing() {
        when(repository.existsByRouteIdAndCreatedAtAfter(any(), any())).thenReturn(true);
        engine.analyzeAndNotify(route(), score(95), 50.0);
        verifyNoInteractions(notificationService);
    }
}
