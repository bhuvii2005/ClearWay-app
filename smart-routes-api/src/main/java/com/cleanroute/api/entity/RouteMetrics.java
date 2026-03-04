package com.cleanroute.api.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "route_metrics")
public class RouteMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(updatable = false)
    private ZonedDateTime timestamp;

    @Column(name = "pollution_score", nullable = false)
    private Double pollutionScore;

    @Column(name = "traffic_index")
    private Double trafficIndex;

    private Double temperature;

    @PrePersist
    protected void onCreate() {
        timestamp = ZonedDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Double getPollutionScore() {
        return pollutionScore;
    }

    public void setPollutionScore(Double pollutionScore) {
        this.pollutionScore = pollutionScore;
    }

    public Double getTrafficIndex() {
        return trafficIndex;
    }

    public void setTrafficIndex(Double trafficIndex) {
        this.trafficIndex = trafficIndex;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
}
