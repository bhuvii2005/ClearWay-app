package com.cleanroute.api.dto;

import java.util.List;

public class TomTomRouteResponse {
    private List<TomTomRoute> routes;

    public List<TomTomRoute> getRoutes() { return routes; }
    public void setRoutes(List<TomTomRoute> routes) { this.routes = routes; }

    public static class TomTomRoute {
        private List<TomTomLeg> legs;
        private TomTomSummary summary;

        public List<TomTomLeg> getLegs() { return legs; }
        public void setLegs(List<TomTomLeg> legs) { this.legs = legs; }
        
        public TomTomSummary getSummary() { return summary; }
        public void setSummary(TomTomSummary summary) { this.summary = summary; }
    }

    public static class TomTomLeg {
        private List<TomTomPoint> points;
        public List<TomTomPoint> getPoints() { return points; }
        public void setPoints(List<TomTomPoint> points) { this.points = points; }
    }

    public static class TomTomPoint {
        private double latitude;
        private double longitude;

        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
    }
    
    public static class TomTomSummary {
        private int lengthInMeters;
        private int travelTimeInSeconds;
        
        public int getLengthInMeters() { return lengthInMeters; }
        public void setLengthInMeters(int lengthInMeters) { this.lengthInMeters = lengthInMeters; }
        public int getTravelTimeInSeconds() { return travelTimeInSeconds; }
        public void setTravelTimeInSeconds(int travelTimeInSeconds) { this.travelTimeInSeconds = travelTimeInSeconds; }
    }
}
