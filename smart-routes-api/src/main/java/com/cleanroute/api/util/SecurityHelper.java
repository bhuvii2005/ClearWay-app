package com.cleanroute.api.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class SecurityHelper {

    private static final SecureRandom sr = new SecureRandom();

    /**
     * Generates a secure random salt encoded in Base64.
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes the password with the salt using SHA-256.
     */
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedBytes = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            throw new RuntimeException("Error hashing password: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies if the raw password matches the stored hash and salt.
     */
    public static boolean verifyPassword(String password, String salt, String hash) {
        if (salt == null || hash == null) {
            return false;
        }
        String computedHash = hashPassword(password, salt);
        return computedHash.equals(hash);
    }
}
