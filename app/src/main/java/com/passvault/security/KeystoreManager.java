package com.passvault.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Owns the hardware-backed AES key that wraps the database passphrase.
 *
 * The passphrase itself is 32 random bytes generated once. It is never written to
 * disk in the clear — only its AES/GCM ciphertext is stored in SharedPreferences,
 * and the key that decrypts it lives in the Android Keystore with
 * setUserAuthenticationRequired(true). The OS will not hand back a usable Cipher
 * until the user has passed biometric or device-credential auth.
 *
 * On hardware-backed devices the key never leaves the secure element, so rooting
 * the phone and reading /data does not expose it.
 */
public final class KeystoreManager {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "passvault_master_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int PASSPHRASE_BYTES = 32;

    private static final String PREFS = "passvault_secure_prefs";
    private static final String KEY_WRAPPED = "wrapped_passphrase";
    private static final String KEY_IV = "wrapped_iv";

    private final SharedPreferences prefs;

    public KeystoreManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True once the vault has been set up on this device. */
    public boolean isInitialised() {
        return prefs.contains(KEY_WRAPPED);
    }

    // ---------------------------------------------------------------- key setup

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);

        if (ks.containsAlias(KEY_ALIAS)) {
            return (SecretKey) ks.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Invalidate the key if a new fingerprint or face is enrolled.
                // This deliberately locks the vault after a biometric change.
                .setInvalidatedByBiometricEnrollment(true)
                .setUserAuthenticationRequired(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                    0, // 0 = auth required for every single use
                    KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
        } else {
            //noinspection deprecation
            builder.setUserAuthenticationValidityDurationSeconds(-1);
        }

        generator.init(builder.build());
        return generator.generateKey();
    }

    // ------------------------------------------------- ciphers for BiometricPrompt

    /**
     * Encryption Cipher for first-run setup. Hand this to BiometricPrompt inside a
     * CryptoObject; the framework unlocks it on successful auth.
     */
    public Cipher createSetupCipher() throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        return cipher;
    }

    /** Decryption Cipher pre-loaded with the stored IV, for normal unlock. */
    public Cipher createUnlockCipher() throws Exception {
        String ivB64 = prefs.getString(KEY_IV, null);
        if (ivB64 == null) throw new IllegalStateException("Vault is not initialised");

        byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher;
    }

    // ------------------------------------------------------- passphrase handling

    /**
     * First run: generate a fresh random passphrase, wrap it with the authenticated
     * cipher, persist the ciphertext, and return the plaintext for immediate use.
     */
    public byte[] createAndStorePassphrase(Cipher authenticatedCipher) throws Exception {
        byte[] passphrase = new byte[PASSPHRASE_BYTES];
        new SecureRandom().nextBytes(passphrase);

        byte[] wrapped = authenticatedCipher.doFinal(passphrase);
        byte[] iv = authenticatedCipher.getIV();

        prefs.edit()
                .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply();

        return passphrase;
    }

    /** Normal unlock: unwrap the stored passphrase with the authenticated cipher. */
    public byte[] unwrapPassphrase(Cipher authenticatedCipher) throws Exception {
        String wrappedB64 = prefs.getString(KEY_WRAPPED, null);
        if (wrappedB64 == null) throw new IllegalStateException("Vault is not initialised");
        return authenticatedCipher.doFinal(Base64.decode(wrappedB64, Base64.NO_WRAP));
    }

    /** Drops the key and the wrapped passphrase. The database becomes unreadable. */
    public void reset() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
        prefs.edit().clear().apply();
    }
}
