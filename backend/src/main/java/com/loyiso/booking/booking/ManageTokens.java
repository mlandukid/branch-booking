package com.loyiso.booking.booking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Creates, hashes and checks the private tokens that let a customer change or cancel a booking. */
public final class ManageTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ManageTokens() {
    }

    /** 256 bits of randomness, in a form that is safe to put in a link. */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    /** Compares in constant time, so the response time does not reveal how much of a guess was right. */
    public static boolean matches(String token, String storedHash) {
        if (token == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}