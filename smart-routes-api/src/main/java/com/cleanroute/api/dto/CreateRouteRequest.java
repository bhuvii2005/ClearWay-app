package com.cleanroute.api.dto;

import java.util.UUID;
import java.util.List;

public class CreateRouteRequest {
    
    private UUID userId;
    private String name;
    private String type;
    
    // Simple 2D array representing path segments [[lon1, lat1], [lon2, lat2]]
    private List<List<Double>> pathCoordinates;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<List<Double>> getPathCoordinates() {
        return pathCoordinates;
    }

    public void setPathCoordinates(List<List<Double>> pathCoordinates) {
        this.pathCoordinates = pathCoordinates;
    }
}
