package com.cleanroute.api.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==";
    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhzMjU2LXNpZ25pbmc=";

    @Test
    void roundTrip_ReturnsUserIdAndVersion() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        UUID userId = UUID.randomUUID();

        String token = provider.generateToken(userId, "a@b.com", 7);
        JwtTokenProvider.TokenClaims claims = provider.parse(token).orElseThrow();

        assertEquals(userId, claims.userId());
        assertEquals(7, claims.version());
    }

    @Test
    void foreignOrMalformedToken_IsRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        JwtTokenProvider other = new JwtTokenProvider(OTHER_SECRET, 60_000);
        String token = other.generateToken(UUID.randomUUID(), "a@b.com", 0);

        assertTrue(provider.parse(token).isEmpty());
        assertTrue(provider.parse("not-a-jwt").isEmpty());
    }

    @Test
    void expiredToken_IsRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, -1_000);
        String token = provider.generateToken(UUID.randomUUID(), "a@b.com", 0);
        assertTrue(provider.parse(token).isEmpty());
    }

    @Test
    void shortSecret_FailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("too-short", 60_000));
    }
}
