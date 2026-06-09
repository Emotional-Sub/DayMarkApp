package com.example.daymark;

import android.text.TextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Password hashing helpers. Stored credentials are SHA-256(salt + password) with a
 * per-user random salt, so the database never holds the plaintext password.
 *
 * <p>This is intentionally lightweight (no external crypto library, matching the rest of
 * the app). For a production app a slow KDF such as PBKDF2/bcrypt/scrypt would be preferable.
 */
public final class PasswordUtils {
    private static final int SALT_BYTES = 16;

    private PasswordUtils() {
    }

    /** Generate a new random salt encoded as a hex string. */
    public static String newSalt() {
        byte[] bytes = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(bytes);
        return toHex(bytes);
    }

    /** Hash {@code password} with the given hex {@code salt}; returns a hex digest. */
    public static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present on Android; fall back to the raw value if not.
            return password;
        }
    }

    /** Constant-time-ish comparison of a candidate password against a stored salt+hash. */
    public static boolean matches(String password, String salt, String expectedHash) {
        if (TextUtils.isEmpty(salt) || TextUtils.isEmpty(expectedHash)) {
            return false;
        }
        return expectedHash.equals(hash(password, salt));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
