package com.cleanroute.api.dto;

import java.util.List;

public class AlternativeRouteDto {
    private List<List<Double>> pathCoordinates;
    private double pollutionScore;
    private int distanceMeters;
    private int travelTimeSeconds;

    public List<List<Double>> getPathCoordinates() { return pathCoordinates; }
    public void setPathCoordinates(List<List<Double>> pathCoordinates) { this.pathCoordinates = pathCoordinates; }

    public double getPollutionScore() { return pollutionScore; }
    public void setPollutionScore(double pollutionScore) { this.pollutionScore = pollutionScore; }

    public int getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(int distanceMeters) { this.distanceMeters = distanceMeters; }

    public int getTravelTimeSeconds() { return travelTimeSeconds; }
    public void setTravelTimeSeconds(int travelTimeSeconds) { this.travelTimeSeconds = travelTimeSeconds; }
}
