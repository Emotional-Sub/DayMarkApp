package com.example.daymark;

import android.text.TextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing helpers. New credentials are stored as a PBKDF2 hash (a deliberately slow KDF)
 * with a per-user random salt, so the database never holds the plaintext password and offline
 * brute-forcing is expensive.
 *
 * <p>The stored value is self-describing: {@code pbkdf2$<alg>$<iterations>$<hexDigest>}. Encoding
 * the algorithm and iteration count means we can verify hashes produced by older app versions and
 * raise the cost over time without a schema change.
 *
 * <p>Hashes produced before this version were a single salted SHA-256 digest with no prefix. Those
 * are still accepted by {@link #matches} (see {@link #isLegacy}); callers should re-hash them with
 * {@link #hash} on the next successful login to transparently upgrade them.
 */
public final class PasswordUtils {
    private static final int SALT_BYTES = 16;

    // PBKDF2 cost. SHA-256 PBKDF2 needs API 26+, so SHA-1 is the fallback on API 23-25; both are
    // run at the same iteration count, a balance between resistance and login latency on old devices.
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_BITS = 256;
    private static final String PBKDF2_SHA256 = "PBKDF2WithHmacSHA256";
    private static final String PBKDF2_SHA1 = "PBKDF2WithHmacSHA1";
    private static final String PBKDF2_PREFIX = "pbkdf2$";

    private PasswordUtils() {
    }

    /** Generate a new random salt encoded as a hex string. */
    public static String newSalt() {
        byte[] bytes = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(bytes);
        return toHex(bytes);
    }

    /**
     * Hash {@code password} with the given hex {@code salt} using PBKDF2. Prefers HMAC-SHA256 and
     * falls back to HMAC-SHA1 where SHA256 is unavailable (API < 26). Returns a self-describing
     * {@code pbkdf2$<alg>$<iterations>$<hex>} string.
     */
    public static String hash(String password, String salt) {
        byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
        String alg = "sha256";
        byte[] derived = pbkdf2(PBKDF2_SHA256, password, saltBytes, PBKDF2_ITERATIONS);
        if (derived == null) {
            alg = "sha1";
            derived = pbkdf2(PBKDF2_SHA1, password, saltBytes, PBKDF2_ITERATIONS);
        }
        if (derived == null) {
            // No PBKDF2 provider at all (should never happen on Android); degrade to the legacy
            // salted SHA-256 form so an account can still be created and later verified.
            return legacySha256(password, salt);
        }
        return PBKDF2_PREFIX + alg + "$" + PBKDF2_ITERATIONS + "$" + toHex(derived);
    }

    /** Verify {@code password} against a stored salt+hash, supporting both PBKDF2 and legacy hashes. */
    public static boolean matches(String password, String salt, String expectedHash) {
        if (TextUtils.isEmpty(salt) || TextUtils.isEmpty(expectedHash)) {
            return false;
        }
        if (expectedHash.startsWith(PBKDF2_PREFIX)) {
            return verifyPbkdf2(password, salt, expectedHash);
        }
        // Pre-PBKDF2 stored value: a bare salted SHA-256 digest.
        return slowEquals(expectedHash, legacySha256(password, salt));
    }

    /**
     * @return true if {@code expectedHash} was produced by an older app version (salted SHA-256)
     * and should be re-hashed with {@link #hash} after the password is next verified.
     */
    public static boolean isLegacy(String expectedHash) {
        return !TextUtils.isEmpty(expectedHash) && !expectedHash.startsWith(PBKDF2_PREFIX);
    }

    private static boolean verifyPbkdf2(String password, String salt, String expectedHash) {
        // pbkdf2$<alg>$<iterations>$<hex>
        String[] parts = expectedHash.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        String alg = "sha1".equals(parts[1]) ? PBKDF2_SHA1 : PBKDF2_SHA256;
        int iterations;
        try {
            iterations = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] derived = pbkdf2(alg, password, salt.getBytes(StandardCharsets.UTF_8), iterations);
        return derived != null && slowEquals(parts[3], toHex(derived));
    }

    private static byte[] pbkdf2(String algorithm, String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return null;
        } finally {
            spec.clearPassword();
        }
    }

    /** The pre-PBKDF2 scheme: hex(SHA-256(salt || password)). Kept only to verify old hashes. */
    private static String legacySha256(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on Android; fall back to the raw value if not.
            return password;
        }
    }

    /** Length-independent, content constant-time comparison of two hex digests. */
    private static boolean slowEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        int diff = ab.length ^ bb.length;
        for (int i = 0; i < ab.length && i < bb.length; i++) {
            diff |= ab[i] ^ bb[i];
        }
        return diff == 0;
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
