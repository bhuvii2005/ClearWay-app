package com.cleanroute.api.controller;

import com.cleanroute.api.dto.*;
import com.cleanroute.api.service.AuthService;
import com.cleanroute.api.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Account endpoints. Signup and login are public; everything else requires a Bearer token
 * and always acts on the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** The signed-in user's profile; also used by the frontend to check a stored token. */
    @GetMapping("/me")
    public UserDto me() {
        return authService.getProfile(CurrentUser.id());
    }

    @PostMapping("/preferences")
    public UserDto updatePreferences(@RequestBody AllergyPreferencesDto request) {
        return authService.updatePreferences(CurrentUser.id(), request);
    }

    @PutMapping("/profile")
    public UserDto updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return authService.updateProfile(CurrentUser.id(), request);
    }

    /** Changes the password, signs out all other sessions, and returns a new token for this one. */
    @PostMapping("/password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(CurrentUser.id(), request);
    }

    /** Revokes every token issued to the user. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout(CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount() {
        authService.deleteAccount(CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
