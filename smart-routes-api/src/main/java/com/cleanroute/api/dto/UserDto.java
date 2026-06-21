package com.cleanroute.api.dto;

import com.cleanroute.api.entity.User;
import java.util.UUID;

public class UserDto {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private boolean avoidPm25;
    private boolean avoidOzone;
    private boolean avoidPm10;
    private boolean avoidNo2;

    public UserDto() {}

    public UserDto(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.avoidPm25 = Boolean.TRUE.equals(user.getAvoidPm25());
        this.avoidOzone = Boolean.TRUE.equals(user.getAvoidOzone());
        this.avoidPm10 = Boolean.TRUE.equals(user.getAvoidPm10());
        this.avoidNo2 = Boolean.TRUE.equals(user.getAvoidNo2());
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isAvoidPm25() { return avoidPm25; }
    public void setAvoidPm25(boolean avoidPm25) { this.avoidPm25 = avoidPm25; }

    public boolean isAvoidOzone() { return avoidOzone; }
    public void setAvoidOzone(boolean avoidOzone) { this.avoidOzone = avoidOzone; }

    public boolean isAvoidPm10() { return avoidPm10; }
    public void setAvoidPm10(boolean avoidPm10) { this.avoidPm10 = avoidPm10; }

    public boolean isAvoidNo2() { return avoidNo2; }
    public void setAvoidNo2(boolean avoidNo2) { this.avoidNo2 = avoidNo2; }
}
