package com.cleanroute.api.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class SecurityHelper {

    private static final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

    /**
     * Hashes password using BCrypt.
     */
    public static String hashPassword(String password) {
        return bcrypt.encode(password);
    }

    /**
     * Verifies raw password against BCrypt hash.
     */
    public static boolean verifyBcryptPassword(String password, String hash) {
        if (hash == null) return false;
        return bcrypt.matches(password, hash);
    }

    /**
     * Legacy SHA-256 verification for version 1 passwords.
     */
    public static boolean verifySha256Password(String password, String salt, String hash) {
        if (salt == null || hash == null) {
            return false;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedBytes = md.digest(password.getBytes());
            String computedHash = Base64.getEncoder().encodeToString(hashedBytes);
            return MessageDigest.isEqual(computedHash.getBytes(), hash.getBytes());
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return false;
        }
    }
}
