package com.passvault.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks passwords against Have I Been Pwned using k-anonymity.
 *
 * Only the first five hex characters of the SHA-1 go over the wire. The server
 * returns every suffix sharing that prefix — typically 400 to 900 of them — and
 * the match happens locally. HIBP therefore never learns which password was
 * checked, or even whether a match occurred.
 *
 * SHA-1 is used because that is the hash HIBP's corpus is built on. It is not a
 * security choice here: the hash never leaves the device in full.
 */
public final class BreachChecker {

    private static final String API = "https://api.pwnedpasswords.com/range/";
    private static final int TIMEOUT_MS = 10_000;

    /** Cache within a session so auditing 50 entries does not mean 50 round trips. */
    private static final Map<String, Integer> CACHE = new HashMap<>();

    /**
     * Returns how many times the password appears in known breaches, 0 if clean,
     * or -1 if the check could not be completed (offline, timeout, rate limit).
     * The three cases are distinct on purpose: "unknown" must never be shown as "safe".
     */
    public static int check(String password) {
        if (password == null || password.isEmpty()) return 0;

        String sha1 = sha1Hex(password);
        if (sha1 == null) return -1;

        String prefix = sha1.substring(0, 5);
        String suffix = sha1.substring(5);

        synchronized (CACHE) {
            Integer hit = CACHE.get(sha1);
            if (hit != null) return hit;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API + prefix).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "Tago-Android");
            // Pads the response with random entries so its size leaks nothing.
            connection.setRequestProperty("Add-Padding", "true");

            if (connection.getResponseCode() != 200) return -1;

            int count = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    int colon = line.indexOf(':');
                    if (colon <= 0) continue;
                    if (line.regionMatches(true, 0, suffix, 0, colon)) {
                        try {
                            count = Integer.parseInt(line.substring(colon + 1).trim());
                        } catch (NumberFormatException ignored) {
                            count = 1;
                        }
                        break;
                    }
                }
            }

            synchronized (CACHE) {
                CACHE.put(sha1, count);
            }
            return count;

        } catch (Exception offline) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Batch check. Runs on the caller's thread, so call it from a background executor. */
    public static Map<String, Integer> checkAll(List<String> passwords) {
        Map<String, Integer> results = new HashMap<>();
        for (String password : passwords) {
            if (!results.containsKey(password)) {
                results.put(password, check(password));
            }
        }
        return results;
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString().toUpperCase();
        } catch (Exception e) {
            return null;
        }
    }

    private BreachChecker() { }
}
