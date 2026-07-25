package com.cleanroute.api.dto;

import java.util.UUID;

public class NotificationDto {
    private UUID routeId;
    private String routeName;
    private String message;

    public NotificationDto() {}

    public NotificationDto(UUID routeId, String routeName, String message) {
        this.routeId = routeId;
        this.routeName = routeName;
        this.message = message;
    }

    public UUID getRouteId() { return routeId; }
    public void setRouteId(UUID routeId) { this.routeId = routeId; }

    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
