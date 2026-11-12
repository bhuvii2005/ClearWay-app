package com.cleanroute.api.service;

import com.cleanroute.api.config.JwtTokenProvider;
import com.cleanroute.api.dto.*;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.exception.ApiException;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.util.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Account use cases: signup, login, profile, preferences, password change, logout, deletion.
 *
 * Passwords are hashed with BCrypt (version 2). Accounts created before BCrypt (version 1, salted
 * SHA-256) are upgraded transparently on their next successful login.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_LOGIN = "Invalid email or password";

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final ApplicationEventPublisher events;

    public AuthService(UserRepository userRepository, JwtTokenProvider tokenProvider, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.events = events;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normaliseEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw ApiException.conflict("An account with this email already exists");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone());
        user.setPasswordHash(SecurityHelper.hashPassword(request.getPassword()));
        user.setPasswordVersion(2);

        User savedUser = userRepository.save(user);
        logger.info("Registered user {}", savedUser.getId());
        return issueToken(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normaliseEmail(request.getEmail()))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_LOGIN));

        if (!passwordMatches(user, request.getPassword())) {
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        if (user.getPasswordVersion() != null && user.getPasswordVersion() == 1) {
            user.setPasswordHash(SecurityHelper.hashPassword(request.getPassword()));
            user.setPasswordVersion(2);
            user.setSalt(null);
            userRepository.save(user);
            logger.info("Upgraded password hash to BCrypt for user {}", user.getId());
        }
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public UserDto getProfile(UUID userId) {
        return new UserDto(findUser(userId));
    }

    @Transactional
    public UserDto updatePreferences(UUID userId, AllergyPreferencesDto request) {
        User user = findUser(userId);
        user.setAvoidPm25(request.isAvoidPm25());
        user.setAvoidOzone(request.isAvoidOzone());
        user.setAvoidPm10(request.isAvoidPm10());
        user.setAvoidNo2(request.isAvoidNo2());
        return new UserDto(userRepository.save(user));
    }

    @Transactional
    public UserDto updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = findUser(userId);
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw ApiException.badRequest("Name must not be blank");
            }
            user.setName(request.getName().trim());
        }
        if (request.getPhone() != null) user.setPhone(request.getPhone().trim());
        if (request.getWhatsappOptIn() != null) user.setWhatsappOptIn(request.getWhatsappOptIn());
        return new UserDto(userRepository.save(user));
    }

    /**
     * Changes the password and revokes every existing token, then returns a fresh token
     * so the current session stays signed in.
     */
    @Transactional
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordMatches(user, request.getCurrentPassword())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(SecurityHelper.hashPassword(request.getNewPassword()));
        user.setPasswordVersion(2);
        user.setSalt(null);
        user.revokeTokens();
        userRepository.save(user);
        return issueToken(user);
    }

    /** Revokes every token issued to the user (logs out all devices). */
    @Transactional
    public void logout(UUID userId) {
        User user = findUser(userId);
        user.revokeTokens();
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        userRepository.delete(findUser(userId)); // routes, metrics and notifications cascade in the database
        events.publishEvent(new AccountDeletedEvent(userId)); // drops in-memory state such as Coach history
        logger.info("Deleted account {}", userId);
    }

    private AuthResponse issueToken(User user) {
        int version = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String token = tokenProvider.generateToken(user.getId(), user.getEmail(), version);
        return new AuthResponse(token, new UserDto(user));
    }

    private boolean passwordMatches(User user, String rawPassword) {
        if (user.getPasswordVersion() != null && user.getPasswordVersion() == 1) {
            return SecurityHelper.verifySha256Password(rawPassword, user.getSalt(), user.getPasswordHash());
        }
        return SecurityHelper.verifyBcryptPassword(rawPassword, user.getPasswordHash());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
