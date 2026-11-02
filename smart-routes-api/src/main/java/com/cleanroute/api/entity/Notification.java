package com.cleanroute.api.entity;

import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/** An air-quality alert sent to a user about one of their monitored routes. */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Kind { POOR_AIR, CLEAN_AIR }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "route_id")
    private UUID routeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "pollution_score")
    private Double pollutionScore;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getRouteId() { return routeId; }
    public void setRouteId(UUID routeId) { this.routeId = routeId; }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Double getPollutionScore() { return pollutionScore; }
    public void setPollutionScore(Double pollutionScore) { this.pollutionScore = pollutionScore; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
}
