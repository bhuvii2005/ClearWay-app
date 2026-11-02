package com.cleanroute.api.dto;

import com.cleanroute.api.entity.Route;
import java.time.ZonedDateTime;
import java.util.UUID;

public class RouteDto {
    private UUID id;
    private String name;
    private String type;
    private Double distanceKm;
    private Integer travelTimeSeconds;
    private Double lastPollutionScore;
    private Double avgPollutionScore;
    private ZonedDateTime lastCheckedAt;
    private ZonedDateTime createdAt;
    private Boolean isActive;

    public RouteDto() {}

    public RouteDto(Route route) {
        this.id = route.getId();
        this.name = route.getName();
        this.type = route.getType();
        this.distanceKm = route.getDistanceKm();
        this.travelTimeSeconds = route.getTravelTimeSeconds();
        this.lastPollutionScore = route.getLastPollutionScore();
        this.avgPollutionScore = route.getAvgPollutionScore();
        this.lastCheckedAt = route.getLastCheckedAt();
        this.createdAt = route.getCreatedAt();
        this.isActive = route.getIsActive();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    
    public Integer getTravelTimeSeconds() { return travelTimeSeconds; }
    public void setTravelTimeSeconds(Integer travelTimeSeconds) { this.travelTimeSeconds = travelTimeSeconds; }

    public Double getLastPollutionScore() { return lastPollutionScore; }
    public void setLastPollutionScore(Double lastPollutionScore) { this.lastPollutionScore = lastPollutionScore; }
    
    public Double getAvgPollutionScore() { return avgPollutionScore; }
    public void setAvgPollutionScore(Double avgPollutionScore) { this.avgPollutionScore = avgPollutionScore; }
    
    public ZonedDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(ZonedDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
