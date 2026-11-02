package com.cleanroute.api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies HMAC-signed JWTs.
 * Each token carries the user's id (subject) and token version ("ver");
 * a token whose version is older than the user's current one is rejected.
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    static final String VERSION_CLAIM = "ver";

    private final SecretKey key;
    private final long jwtExpirationMs;

    /** The verified contents of a token. */
    public record TokenClaims(UUID userId, int version) {}

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long jwtExpirationMs) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException e) {
            keyBytes = secret.getBytes();
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 256 bits (32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public String generateToken(UUID userId, String email, int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(VERSION_CLAIM, tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Verifies the signature and expiry. Returns empty for any invalid token.
     */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Integer version = claims.get(VERSION_CLAIM, Integer.class);
            return Optional.of(new TokenClaims(UUID.fromString(claims.getSubject()), version == null ? 0 : version));
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
