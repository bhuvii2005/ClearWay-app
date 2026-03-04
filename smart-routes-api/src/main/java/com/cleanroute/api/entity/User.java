package com.cleanroute.api.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;
    
    @Column(name = "preferred_channels")
    private String preferredChannels;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "whatsapp_opt_in")
    private Boolean whatsappOptIn = false;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "last_active")
    private ZonedDateTime lastActive;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        lastActive = ZonedDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastActive = ZonedDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPreferredChannels() {
        return preferredChannels;
    }

    public void setPreferredChannels(String preferredChannels) {
        this.preferredChannels = preferredChannels;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public Boolean getWhatsappOptIn() {
        return whatsappOptIn;
    }

    public void setWhatsappOptIn(Boolean whatsappOptIn) {
        this.whatsappOptIn = whatsappOptIn;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getLastActive() {
        return lastActive;
    }

    public void setLastActive(ZonedDateTime lastActive) {
        this.lastActive = lastActive;
    }
}
