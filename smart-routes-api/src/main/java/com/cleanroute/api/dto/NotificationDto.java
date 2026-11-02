package com.cleanroute.api.dto;

import com.cleanroute.api.entity.Notification;

import java.time.ZonedDateTime;
import java.util.UUID;

/** An alert as sent over WebSocket and returned by GET /api/v1/notifications. */
public record NotificationDto(
        UUID id,
        UUID routeId,
        String routeName,
        String kind,
        String message,
        Double pollutionScore,
        boolean read,
        ZonedDateTime createdAt) {

    public static NotificationDto from(Notification n, String routeName) {
        return new NotificationDto(n.getId(), n.getRouteId(), routeName, n.getKind().name(), n.getMessage(),
                n.getPollutionScore(), n.isRead(), n.getCreatedAt());
    }
}
