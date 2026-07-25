package com.cleanroute.api.service;

import com.cleanroute.api.config.JwtTokenProvider;
import com.cleanroute.api.dto.AuthResponse;
import com.cleanroute.api.dto.LoginRequest;
import com.cleanroute.api.dto.SignupRequest;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.util.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void signup_Success() {
        SignupRequest request = new SignupRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setName("Test User");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        
        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("test@example.com");
        
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenProvider.generateToken(any(UUID.class), anyString())).thenReturn("mock-token");

        AuthResponse response = authService.signup(request);

        assertNotNull(response);
        assertEquals("mock-token", response.getToken());
        assertEquals("test@example.com", response.getUser().getEmail());
        
        verify(userRepository).save(any(User.class));
    }

    @Test
    void login_Success_Bcrypt() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPasswordHash(SecurityHelper.hashPassword("password123"));
        user.setPasswordVersion(2);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken(any(UUID.class), anyString())).thenReturn("mock-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-token", response.getToken());
    }
}
