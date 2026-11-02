package com.cleanroute.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Asks for cleanest-first route alternatives between a start and an end point,
 * each given as [longitude, latitude].
 */
public class AlternativesRequest {

    @Pattern(regexp = "walking|jogging|cycling|driving|commute", message = "Unsupported route type")
    private String type = "walking";

    @NotNull(message = "Start and end points are required")
    @Size(min = 2, max = 2, message = "Provide exactly a start and an end point")
    private List<List<Double>> pathCoordinates;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<List<Double>> getPathCoordinates() { return pathCoordinates; }
    public void setPathCoordinates(List<List<Double>> pathCoordinates) { this.pathCoordinates = pathCoordinates; }
}
