package com.cleanroute.api.entity;

import jakarta.persistence.*;
import org.locationtech.jts.geom.LineString;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // jogging, cycling, walking, commute

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(columnDefinition = "geometry(LineString,4326)", nullable = false)
    private LineString path;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "last_pollution_score")
    private Double lastPollutionScore;

    @Column(name = "avg_pollution_score")
    private Double avgPollutionScore;

    @Column(name = "last_checked_at")
    private ZonedDateTime lastCheckedAt;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RouteMetrics> metrics = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LineString getPath() {
        return path;
    }

    public void setPath(LineString path) {
        this.path = path;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Double getLastPollutionScore() {
        return lastPollutionScore;
    }

    public void setLastPollutionScore(Double lastPollutionScore) {
        this.lastPollutionScore = lastPollutionScore;
    }

    public Double getAvgPollutionScore() {
        return avgPollutionScore;
    }

    public void setAvgPollutionScore(Double avgPollutionScore) {
        this.avgPollutionScore = avgPollutionScore;
    }

    public ZonedDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(ZonedDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public List<RouteMetrics> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<RouteMetrics> metrics) {
        this.metrics = metrics;
    }
}
