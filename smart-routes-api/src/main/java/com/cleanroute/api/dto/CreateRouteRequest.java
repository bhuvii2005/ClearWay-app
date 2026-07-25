package com.cleanroute.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.List;

public class CreateRouteRequest {
    
    private UUID userId;

    @NotBlank(message = "Route name is required")
    private String name;

    private String type = "walking";
    
    @NotEmpty(message = "Path coordinates are required")
    @Size(min = 2, message = "Route must contain at least a start and end point")
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
