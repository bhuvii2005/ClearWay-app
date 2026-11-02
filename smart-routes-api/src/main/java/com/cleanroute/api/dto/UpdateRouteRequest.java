package com.cleanroute.api.dto;

import jakarta.validation.constraints.Size;

/** Partial update of a saved route. Null fields are left unchanged. */
public class UpdateRouteRequest {

    @Size(min = 1, max = 100, message = "Route name must be 1 to 100 characters")
    private String name;

    /** Turns scheduler monitoring on or off. */
    private Boolean isActive;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
