package com.passvault.autofill;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.passvault.R;
import com.passvault.security.BiometricHelper;
import com.passvault.security.KeystoreManager;
import com.passvault.security.VaultLock;

import javax.crypto.Cipher;

/** The biometric unlock both autofill activities show over the other app. */
final class UnlockPrompt {

    interface Result {
        void onUnlocked();
        void onFailed();
    }

    private UnlockPrompt() { }

    static void show(FragmentActivity activity, int titleRes, int subtitleRes, Result result) {
        if (VaultLock.get().isUnlocked()) {
            result.onUnlocked();
            return;
        }
        KeystoreManager keystore = new KeystoreManager(activity);
        if (!keystore.isInitialised()) {
            Toast.makeText(activity, R.string.autofill_setup_first, Toast.LENGTH_SHORT).show();
            result.onFailed();
            return;
        }
        try {
            Cipher cipher = keystore.createUnlockCipher();
            BiometricHelper.authenticate(activity,
                    activity.getString(titleRes),
                    activity.getString(subtitleRes),
                    cipher,
                    new BiometricHelper.Callback() {
                        @Override
                        public void onSuccess(@NonNull Cipher authenticated) {
                            try {
                                VaultLock.get().unlock(keystore.unwrapPassphrase(authenticated));
                                result.onUnlocked();
                            } catch (Exception e) {
                                result.onFailed();
                            }
                        }

                        @Override
                        public void onError(@NonNull CharSequence message, boolean cancelled) {
                            result.onFailed();
                        }
                    });
        } catch (Exception e) {
            // The key was invalidated (new fingerprint or screen lock). Recovery lives in the
            // app itself, where it can explain what happens to the vault.
            Toast.makeText(activity, R.string.autofill_open_app, Toast.LENGTH_LONG).show();
            result.onFailed();
        }
    }
}
