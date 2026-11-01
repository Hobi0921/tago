package com.passvault.util;

import java.nio.ByteBuffer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 time-based one-time passwords.
 *
 * A caution worth stating plainly: storing the TOTP secret next to the password
 * collapses two factors into one. If the vault is compromised, so is the second
 * factor. It is still better than no 2FA, and much better than SMS, but it is not
 * equivalent to keeping the factors on separate devices.
 */
public final class TotpGenerator {

    public static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    public static String generate(String base32Secret) {
        return generateAt(base32Secret, System.currentTimeMillis() / 1000L);
    }

    public static String generateAt(String base32Secret, long unixSeconds) {
        try {
            byte[] key = Base32.decode(base32Secret);
            long counter = unixSeconds / TIME_STEP_SECONDS;

            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);

            // Dynamic truncation: the low nibble of the last byte picks the offset.
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);

        } catch (Exception e) {
            return null;
        }
    }

    /** Seconds until the current code rolls over, for the countdown ring. */
    public static int secondsRemaining() {
        long now = System.currentTimeMillis() / 1000L;
        return TIME_STEP_SECONDS - (int) (now % TIME_STEP_SECONDS);
    }

    /**
     * Pull the secret out of an otpauth:// URI, which is what QR codes encode.
     * Returns the input unchanged if it is already a bare secret.
     */
    public static String extractSecret(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (!trimmed.toLowerCase().startsWith("otpauth://")) return trimmed;

        for (String param : trimmed.substring(trimmed.indexOf('?') + 1).split("&")) {
            if (param.toLowerCase().startsWith("secret=")) {
                return param.substring(7);
            }
        }
        return trimmed;
    }

    private TotpGenerator() { }
}
