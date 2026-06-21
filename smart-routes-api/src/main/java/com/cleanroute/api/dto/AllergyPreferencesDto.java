package com.cleanroute.api.dto;

import java.util.UUID;

public class AllergyPreferencesDto {
    private UUID userId;
    private boolean avoidPm25;
    private boolean avoidOzone;
    private boolean avoidPm10;
    private boolean avoidNo2;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public boolean isAvoidPm25() { return avoidPm25; }
    public void setAvoidPm25(boolean avoidPm25) { this.avoidPm25 = avoidPm25; }

    public boolean isAvoidOzone() { return avoidOzone; }
    public void setAvoidOzone(boolean avoidOzone) { this.avoidOzone = avoidOzone; }

    public boolean isAvoidPm10() { return avoidPm10; }
    public void setAvoidPm10(boolean avoidPm10) { this.avoidPm10 = avoidPm10; }

    public boolean isAvoidNo2() { return avoidNo2; }
    public void setAvoidNo2(boolean avoidNo2) { this.avoidNo2 = avoidNo2; }
}
