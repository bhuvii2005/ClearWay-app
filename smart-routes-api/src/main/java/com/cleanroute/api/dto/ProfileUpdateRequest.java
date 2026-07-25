package com.cleanroute.api.dto;

public class ProfileUpdateRequest {
    private String name;
    private String phone;
    private Boolean whatsappOptIn;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Boolean getWhatsappOptIn() { return whatsappOptIn; }
    public void setWhatsappOptIn(Boolean whatsappOptIn) { this.whatsappOptIn = whatsappOptIn; }
}
