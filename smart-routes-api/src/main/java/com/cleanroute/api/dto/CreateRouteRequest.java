package com.cleanroute.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Saves a route for the authenticated user. pathCoordinates is the full geometry
 * of the chosen alternative as [longitude, latitude] pairs.
 */
public class CreateRouteRequest {

    @NotBlank(message = "Route name is required")
    @Size(max = 100, message = "Route name must not exceed 100 characters")
    private String name;

    @Pattern(regexp = "walking|jogging|cycling|driving|commute", message = "Unsupported route type")
    private String type = "walking";

    @NotEmpty(message = "Path coordinates are required")
    @Size(min = 2, max = 10000, message = "A route needs between 2 and 10000 points")
    private List<List<Double>> pathCoordinates;

    @PositiveOrZero
    private Integer distanceMeters;

    @PositiveOrZero
    private Integer travelTimeSeconds;

    /** Whether the scheduler should monitor this route. Defaults to true. */
    private Boolean monitor = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<List<Double>> getPathCoordinates() { return pathCoordinates; }
    public void setPathCoordinates(List<List<Double>> pathCoordinates) { this.pathCoordinates = pathCoordinates; }

    public Integer getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Integer distanceMeters) { this.distanceMeters = distanceMeters; }

    public Integer getTravelTimeSeconds() { return travelTimeSeconds; }
    public void setTravelTimeSeconds(Integer travelTimeSeconds) { this.travelTimeSeconds = travelTimeSeconds; }

    public Boolean getMonitor() { return monitor; }
    public void setMonitor(Boolean monitor) { this.monitor = monitor; }
}
