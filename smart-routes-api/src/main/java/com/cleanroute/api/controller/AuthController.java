package com.cleanroute.api.controller;

import com.cleanroute.api.dto.*;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.util.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        logger.info("Received signup request for email: {}", request.getEmail());
        try {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            if (request.getPassword() == null || request.getPassword().length() < 6) {
                return ResponseEntity.badRequest().body("Password must be at least 6 characters");
            }

            Optional<User> existingUser = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
            if (existingUser.isPresent()) {
                return ResponseEntity.badRequest().body("User with this email already exists");
            }

            User user = new User();
            user.setName(request.getName() != null ? request.getName().trim() : "New User");
            user.setEmail(request.getEmail().trim().toLowerCase());
            user.setPhone(request.getPhone());
            
            String salt = SecurityHelper.generateSalt();
            String hash = SecurityHelper.hashPassword(request.getPassword(), salt);
            user.setSalt(salt);
            user.setPasswordHash(hash);

            User savedUser = userRepository.save(user);
            logger.info("Successfully registered user with ID: {}", savedUser.getId());
            return ResponseEntity.ok(new UserDto(savedUser));
        } catch (Exception e) {
            logger.error("Signup error", e);
            return ResponseEntity.internalServerError().body("An error occurred during signup: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        logger.info("Received login request for email: {}", request.getEmail());
        try {
            if (request.getEmail() == null || request.getPassword() == null) {
                return ResponseEntity.badRequest().body("Email and password are required");
            }

            Optional<User> userOpt = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401).body("Invalid email or password");
            }

            User user = userOpt.get();
            boolean matches = SecurityHelper.verifyPassword(request.getPassword(), user.getSalt(), user.getPasswordHash());
            if (!matches) {
                return ResponseEntity.status(401).body("Invalid email or password");
            }

            logger.info("Successful login for user: {}", user.getEmail());
            return ResponseEntity.ok(new UserDto(user));
        } catch (Exception e) {
            logger.error("Login error", e);
            return ResponseEntity.internalServerError().body("An error occurred during login: " + e.getMessage());
        }
    }

    @PostMapping("/preferences")
    public ResponseEntity<?> updatePreferences(@RequestBody AllergyPreferencesDto request) {
        logger.info("Updating preferences for user ID: {}", request.getUserId());
        try {
            if (request.getUserId() == null) {
                return ResponseEntity.badRequest().body("User ID is required");
            }

            Optional<User> userOpt = userRepository.findById(request.getUserId());
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body("User not found");
            }

            User user = userOpt.get();
            user.setAvoidPm25(request.isAvoidPm25());
            user.setAvoidOzone(request.isAvoidOzone());
            user.setAvoidPm10(request.isAvoidPm10());
            user.setAvoidNo2(request.isAvoidNo2());

            User updatedUser = userRepository.save(user);
            logger.info("Successfully updated preferences for user: {}", updatedUser.getEmail());
            return ResponseEntity.ok(new UserDto(updatedUser));
        } catch (Exception e) {
            logger.error("Preferences update error", e);
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }
}
