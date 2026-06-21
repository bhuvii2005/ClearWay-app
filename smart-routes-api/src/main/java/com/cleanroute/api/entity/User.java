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

    @Column(name = "password_hash")
    private String passwordHash;

    private String salt;

    @Column(name = "avoid_pm25")
    private Boolean avoidPm25 = false;

    @Column(name = "avoid_ozone")
    private Boolean avoidOzone = false;

    @Column(name = "avoid_pm10")
    private Boolean avoidPm10 = false;

    @Column(name = "avoid_no2")
    private Boolean avoidNo2 = false;

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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public Boolean getAvoidPm25() {
        return avoidPm25;
    }

    public void setAvoidPm25(Boolean avoidPm25) {
        this.avoidPm25 = avoidPm25;
    }

    public Boolean getAvoidOzone() {
        return avoidOzone;
    }

    public void setAvoidOzone(Boolean avoidOzone) {
        this.avoidOzone = avoidOzone;
    }

    public Boolean getAvoidPm10() {
        return avoidPm10;
    }

    public void setAvoidPm10(Boolean avoidPm10) {
        this.avoidPm10 = avoidPm10;
    }

    public Boolean getAvoidNo2() {
        return avoidNo2;
    }

    public void setAvoidNo2(Boolean avoidNo2) {
        this.avoidNo2 = avoidNo2;
    }
}
