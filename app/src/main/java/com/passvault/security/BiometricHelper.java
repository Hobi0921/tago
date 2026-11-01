package com.passvault.security;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

import javax.crypto.Cipher;

/** Thin wrapper over BiometricPrompt that always carries a CryptoObject. */
public final class BiometricHelper {

    public interface Callback {
        void onSuccess(@NonNull Cipher cipher);
        void onError(@NonNull CharSequence message, boolean userCancelled);
    }

    private static final int AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    /** Returns null if auth is available, otherwise a message explaining why not. */
    public static String checkAvailability(FragmentActivity activity) {
        BiometricManager manager = BiometricManager.from(activity);
        switch (manager.canAuthenticate(AUTHENTICATORS)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return null;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "This device has no biometric hardware available.";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "Set a screen lock or enrol a fingerprint to use Tago.";
            default:
                return "Biometric authentication is unavailable right now.";
        }
    }

    public static void authenticate(FragmentActivity activity,
                                    String title,
                                    String subtitle,
                                    Cipher cipher,
                                    Callback callback) {

        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt prompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        BiometricPrompt.CryptoObject crypto = result.getCryptoObject();
                        if (crypto == null || crypto.getCipher() == null) {
                            callback.onError("Authentication returned no cipher.", false);
                            return;
                        }
                        callback.onSuccess(crypto.getCipher());
                    }

                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence msg) {
                        boolean cancelled = code == BiometricPrompt.ERROR_USER_CANCELED
                                || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || code == BiometricPrompt.ERROR_CANCELED;
                        callback.onError(msg, cancelled);
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build();

        prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
    }

    private BiometricHelper() { }
}
