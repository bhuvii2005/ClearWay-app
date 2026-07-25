package com.cleanroute.api.service;

import com.cleanroute.api.config.JwtTokenProvider;
import com.cleanroute.api.dto.*;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.util.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        logger.info("Registering new user for email: {}", request.getEmail());

        Optional<User> existingUser = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        User user = new User();
        user.setName(request.getName() != null ? request.getName().trim() : "New User");
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPhone(request.getPhone());
        user.setPasswordHash(SecurityHelper.hashPassword(request.getPassword()));
        user.setPasswordVersion(2);

        User savedUser = userRepository.save(user);
        logger.info("Successfully registered user with ID: {}", savedUser.getId());

        String token = tokenProvider.generateToken(savedUser.getId(), savedUser.getEmail());
        return new AuthResponse(token, new UserDto(savedUser));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        logger.info("Authenticating user for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        boolean matches = false;
        if (user.getPasswordVersion() != null && user.getPasswordVersion() == 1) {
            matches = SecurityHelper.verifySha256Password(request.getPassword(), user.getSalt(), user.getPasswordHash());
            if (matches) {
                user.setPasswordHash(SecurityHelper.hashPassword(request.getPassword()));
                user.setPasswordVersion(2);
                userRepository.save(user);
                logger.info("Upgraded password hash to BCrypt v2 for user: {}", user.getEmail());
            }
        } else {
            matches = SecurityHelper.verifyBcryptPassword(request.getPassword(), user.getPasswordHash());
        }

        if (!matches) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        logger.info("Successful authentication for user: {}", user.getEmail());
        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, new UserDto(user));
    }

    @Transactional
    public UserDto updatePreferences(UUID userId, AllergyPreferencesDto request) {
        logger.info("Updating allergy preferences for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setAvoidPm25(request.isAvoidPm25());
        user.setAvoidOzone(request.isAvoidOzone());
        user.setAvoidPm10(request.isAvoidPm10());
        user.setAvoidNo2(request.isAvoidNo2());

        User updatedUser = userRepository.save(user);
        logger.info("Successfully updated preferences for user: {}", updatedUser.getEmail());
        return new UserDto(updatedUser);
    }

    @Transactional
    public UserDto updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getName() != null) user.setName(request.getName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getWhatsappOptIn() != null) user.setWhatsappOptIn(request.getWhatsappOptIn());

        User updatedUser = userRepository.save(user);
        return new UserDto(updatedUser);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean matches;
        if (user.getPasswordVersion() != null && user.getPasswordVersion() == 1) {
            matches = SecurityHelper.verifySha256Password(request.getCurrentPassword(), user.getPasswordSalt(), user.getPasswordHash());
        } else {
            matches = SecurityHelper.verifyBcryptPassword(request.getCurrentPassword(), user.getPasswordHash());
        }

        if (!matches) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(SecurityHelper.hashPassword(request.getNewPassword()));
        user.setPasswordVersion(2);
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        userRepository.delete(user);
    }
}
