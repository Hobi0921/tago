package com.passvault.security;

import android.util.Base64;

import com.passvault.data.PasswordEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted backup, and the answer to the vault's worst failure mode.
 *
 * The Keystore key is created with setInvalidatedByBiometricEnrollment(true), so
 * enrolling a new fingerprint destroys it and the database becomes permanently
 * unreadable. Without an export path that is silent, unrecoverable data loss.
 *
 * The export deliberately does NOT use the Keystore. It is sealed with a key
 * derived from a passphrase the user chooses and remembers, so the file can be
 * restored on a different device, or on this one after a key wipe.
 *
 * Format is a JSON envelope:
 *   { format, version, kdf, iterations, salt, iv, ciphertext }
 * The salt and IV are public by design; only the passphrase is secret.
 */
public final class VaultExporter {

    public static final String FORMAT = "pvault";
    public static final int VERSION = 1;

    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000; // OWASP guidance for PBKDF2-SHA256
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    public static class ImportResult {
        public final List<PasswordEntry> entries;
        public final int version;

        ImportResult(List<PasswordEntry> entries, int version) {
            this.entries = entries;
            this.version = version;
        }
    }

    /** Thrown when the passphrase is wrong, or the file has been tampered with. */
    public static class WrongPassphraseException extends Exception {
        public WrongPassphraseException() {
            super("Wrong passphrase, or the file has been modified.");
        }
    }

    // ------------------------------------------------------------------ export

    public static void export(List<PasswordEntry> entries, char[] passphrase,
                              OutputStream out) throws Exception {

        JSONArray array = new JSONArray();
        for (PasswordEntry entry : entries) {
            JSONObject o = new JSONObject();
            o.put("siteName", entry.siteName);
            o.put("username", entry.username);
            o.put("password", entry.password);
            o.put("webDomain", entry.webDomain == null ? JSONObject.NULL : entry.webDomain);
            o.put("packageName", entry.packageName == null ? JSONObject.NULL : entry.packageName);
            o.put("totpSecret", entry.totpSecret == null ? JSONObject.NULL : entry.totpSecret);
            o.put("notes", entry.notes == null ? JSONObject.NULL : entry.notes);
            o.put("createdAt", entry.createdAt);
            o.put("updatedAt", entry.updatedAt);
            o.put("passwordUpdatedAt", entry.passwordUpdatedAt);
            array.put(o);
        }

        byte[] plaintext = array.toString().getBytes(StandardCharsets.UTF_8);

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);

        SecretKey key = deriveKey(passphrase, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        Arrays.fill(plaintext, (byte) 0);

        JSONObject envelope = new JSONObject();
        envelope.put("format", FORMAT);
        envelope.put("version", VERSION);
        envelope.put("kdf", KDF);
        envelope.put("iterations", ITERATIONS);
        envelope.put("entryCount", entries.size());
        envelope.put("exportedAt", System.currentTimeMillis());
        envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        envelope.put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP));

        out.write(envelope.toString(2).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    // ------------------------------------------------------------------ import

    public static ImportResult importVault(InputStream in, char[] passphrase)
            throws Exception {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);

        JSONObject envelope = new JSONObject(
                new String(buffer.toByteArray(), StandardCharsets.UTF_8));

        if (!FORMAT.equals(envelope.optString("format"))) {
            throw new IllegalArgumentException("Not a Tago backup file.");
        }
        int version = envelope.optInt("version", 0);
        if (version > VERSION) {
            throw new IllegalArgumentException(
                    "This file was made by a newer version of Tago.");
        }

        byte[] salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP);
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP);
        int iterations = envelope.optInt("iterations", ITERATIONS);

        SecretKey key = deriveKey(passphrase, salt, iterations);

        byte[] plaintext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            plaintext = cipher.doFinal(ciphertext);
        } catch (Exception authFailed) {
            // GCM's tag check fails identically for a wrong passphrase and for a
            // modified file. That is the point — there is nothing to distinguish.
            throw new WrongPassphraseException();
        }

        JSONArray array = new JSONArray(new String(plaintext, StandardCharsets.UTF_8));
        Arrays.fill(plaintext, (byte) 0);

        List<PasswordEntry> entries = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            PasswordEntry entry = new PasswordEntry();
            entry.siteName = o.optString("siteName", "");
            entry.username = o.optString("username", "");
            entry.password = o.optString("password", "");
            entry.webDomain = o.isNull("webDomain") ? null : o.optString("webDomain");
            entry.packageName = o.isNull("packageName") ? null : o.optString("packageName");
            entry.totpSecret = o.isNull("totpSecret") ? null : o.optString("totpSecret");
            entry.notes = o.isNull("notes") ? null : o.optString("notes");
            entry.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            entry.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
            entry.passwordUpdatedAt = o.optLong("passwordUpdatedAt", entry.updatedAt);
            entries.add(entry);
        }

        return new ImportResult(entries, version);
    }

    // --------------------------------------------------------------------- KDF

    private static SecretKey deriveKey(char[] passphrase, byte[] salt) throws Exception {
        return deriveKey(passphrase, salt, ITERATIONS);
    }

    /**
     * PBKDF2-HMAC-SHA256 at 600k iterations.
     *
     * Argon2id would be the better choice — it is memory-hard, so GPU and ASIC
     * attackers gain far less. It needs a native library though, and PBKDF2 ships
     * with Android. If you add the Argon2 JVM binding later, bump the envelope
     * version and keep this branch for reading old files.
     */
    private static SecretKey deriveKey(char[] passphrase, byte[] salt, int iterations)
            throws Exception {

        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF);
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private VaultExporter() { }
}
