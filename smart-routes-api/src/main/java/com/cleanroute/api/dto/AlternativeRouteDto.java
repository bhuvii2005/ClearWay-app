package com.cleanroute.api.dto;

import java.util.List;

/**
 * One candidate route returned by POST /api/v1/routes/alternatives.
 * The list is sorted cleanest first.
 */
public class AlternativeRouteDto {

    /** Geometry as [longitude, latitude] pairs. */
    private List<List<Double>> pathCoordinates;
    /** Exposure score (lower is cleaner), personalised to the user's sensitivities. */
    private double pollutionScore;
    private int distanceMeters;
    private int travelTimeSeconds;
    /** True for the route TomTom considers fastest. */
    private boolean fastest;
    /**
     * How much lower this route's score is than the fastest route's, in percent.
     * Positive = cleaner than the fastest route; 0 for the fastest route itself.
     */
    private double exposureSavingPercent;
    /** Mean pollutant readings along the route. */
    private PollutionData pollutionData;
    /** Air quality at points along the route, start to end, as [longitude, latitude, European AQI]. */
    private List<List<Double>> airSamples;

    public List<List<Double>> getAirSamples() { return airSamples; }
    public void setAirSamples(List<List<Double>> airSamples) { this.airSamples = airSamples; }

    public List<List<Double>> getPathCoordinates() { return pathCoordinates; }
    public void setPathCoordinates(List<List<Double>> pathCoordinates) { this.pathCoordinates = pathCoordinates; }

    public double getPollutionScore() { return pollutionScore; }
    public void setPollutionScore(double pollutionScore) { this.pollutionScore = pollutionScore; }

    public int getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(int distanceMeters) { this.distanceMeters = distanceMeters; }

    public int getTravelTimeSeconds() { return travelTimeSeconds; }
    public void setTravelTimeSeconds(int travelTimeSeconds) { this.travelTimeSeconds = travelTimeSeconds; }

    public boolean isFastest() { return fastest; }
    public void setFastest(boolean fastest) { this.fastest = fastest; }

    public double getExposureSavingPercent() { return exposureSavingPercent; }
    public void setExposureSavingPercent(double exposureSavingPercent) { this.exposureSavingPercent = exposureSavingPercent; }

    public PollutionData getPollutionData() { return pollutionData; }
    public void setPollutionData(PollutionData pollutionData) { this.pollutionData = pollutionData; }
}
