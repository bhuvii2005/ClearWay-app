package com.cleanroute.api.config;

import com.cleanroute.api.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads "Authorization: Bearer <jwt>", verifies it, and checks the token version
 * against the database so that logged-out or password-changed tokens stop working.
 * The authenticated principal is the user's UUID.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String jwt = resolveToken(request.getHeader("Authorization"));
        if (jwt != null) {
            authenticate(jwt).ifPresent(userId -> {
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Returns the user id when the token is valid and not revoked. Shared with the WebSocket handshake.
     */
    public Optional<UUID> authenticate(String jwt) {
        return tokenProvider.parse(jwt)
                .filter(claims -> userRepository.findTokenVersionById(claims.userId())
                        .map(current -> current == claims.version())
                        .orElse(false))
                .map(JwtTokenProvider.TokenClaims::userId);
    }

    public static String resolveToken(String header) {
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
