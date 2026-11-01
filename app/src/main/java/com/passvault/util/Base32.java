package com.passvault.util;

/**
 * RFC 4648 Base32, which is how TOTP shared secrets are exchanged.
 * Not Base64 — authenticator QR codes and manual-entry keys use Base32
 * specifically because it survives being read aloud and typed by hand.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static byte[] decode(String input) {
        if (input == null) throw new IllegalArgumentException("null secret");

        // Users paste secrets with spaces and lowercase; padding is optional.
        String clean = input.trim().replace(" ", "").replace("-", "")
                .toUpperCase().replace("=", "");
        if (clean.isEmpty()) throw new IllegalArgumentException("empty secret");

        int outLength = clean.length() * 5 / 8;
        byte[] out = new byte[outLength];

        int buffer = 0, bitsLeft = 0, index = 0;
        for (char c : clean.toCharArray()) {
            int value = ALPHABET.indexOf(c);
            if (value < 0) throw new IllegalArgumentException("Not valid Base32: " + c);

            buffer = (buffer << 5) | value;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                out[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return out;
    }

    public static boolean isValid(String input) {
        try {
            byte[] decoded = decode(input);
            return decoded.length >= 10; // shorter than 80 bits is not a real secret
        } catch (Exception e) {
            return false;
        }
    }

    private Base32() { }
}
