package com.passvault.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.passvault.R;
import com.passvault.data.AppDatabase;
import com.passvault.security.BiometricHelper;
import com.passvault.security.KeystoreManager;
import com.passvault.security.VaultLock;

import javax.crypto.Cipher;

/**
 * Gatekeeper. Nothing in the app is reachable until the Keystore key has released
 * a usable cipher and the passphrase has been unwrapped.
 */
public class UnlockActivity extends FragmentActivity {

    /** Set when the vault was just rebuilt, so MainActivity can open the restore flow. */
    public static final String EXTRA_OPEN_RESTORE = "open_restore";

    private KeystoreManager keystore;
    private TextView statusText;
    private Button unlockButton;
    private Button recoverButton;

    /** Carried into MainActivity after a recovery so the user lands on Settings. */
    private boolean recovered;

    /** "I have a backup" on the welcome screen: after setup, open the restore flow. */
    private boolean restoreAfterSetup;

    /** Guards against onResume firing a second prompt while one is already up. */
    private boolean authInFlight;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Blocks screenshots and keeps the vault out of the recents thumbnail.
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        keystore = new KeystoreManager(this);

        // A new user meets the welcome screen first, once, so the system's biometric sheet
        // never appears unexplained. Anyone who already has a vault never sees it.
        if (OnboardingActivity.shouldShow(this, keystore.isInitialised())) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        restoreAfterSetup = getIntent().getBooleanExtra(OnboardingActivity.EXTRA_RESTORE, false);

        setContentView(R.layout.activity_unlock);
        statusText = findViewById(R.id.unlockStatus);
        unlockButton = findViewById(R.id.unlockButton);
        recoverButton = findViewById(R.id.unlockRecover);

        unlockButton.setOnClickListener(v -> start());
        recoverButton.setOnClickListener(v -> confirmRecovery());

        // First run: the button sets the vault up, it doesn't unlock anything yet.
        if (!keystore.isInitialised()) {
            statusText.setText(R.string.unlock_setup_hint);
            unlockButton.setText(R.string.unlock_setup_button);
        }

        String unavailable = BiometricHelper.checkAvailability(this);
        if (unavailable != null) {
            statusText.setText(unavailable);
            unlockButton.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (VaultLock.get().isUnlocked()) {
            openVault();
        } else if (unlockButton.isEnabled() && !authInFlight) {
            start();
        }
    }

    private void start() {
        if (authInFlight) return;
        authInFlight = true;
        if (keystore.isInitialised()) {
            unlock();
        } else {
            firstRunSetup();
        }
    }

    // ------------------------------------------------------------- first run

    private void firstRunSetup() {
        authInFlight = true;
        statusText.setText(R.string.unlock_setup_hint);
        try {
            Cipher cipher = keystore.createSetupCipher();
            BiometricHelper.authenticate(this,
                    getString(R.string.unlock_setup_title),
                    getString(R.string.unlock_setup_subtitle),
                    cipher,
                    new BiometricHelper.Callback() {
                        @Override
                        public void onSuccess(@NonNull Cipher authenticated) {
                            try {
                                byte[] passphrase =
                                        keystore.createAndStorePassphrase(authenticated);
                                VaultLock.get().unlock(passphrase);
                                openVault();
                            } catch (Exception e) {
                                fail(getString(R.string.unlock_setup_failed), true);
                            }
                        }

                        @Override
                        public void onError(@NonNull CharSequence message, boolean cancelled) {
                            // Cancelling setup isn't a failure: say why it's needed instead.
                            fail(cancelled
                                    ? getString(R.string.unlock_setup_cancelled)
                                    : message.toString(), !cancelled);
                        }
                    });
        } catch (Exception e) {
            fail(getString(R.string.unlock_setup_failed), true);
        }
    }

    // ---------------------------------------------------------- normal unlock

    private void unlock() {
        statusText.setText(R.string.unlock_hint);
        try {
            Cipher cipher = keystore.createUnlockCipher();
            BiometricHelper.authenticate(this,
                    getString(R.string.unlock_title),
                    getString(R.string.unlock_subtitle),
                    cipher,
                    new BiometricHelper.Callback() {
                        @Override
                        public void onSuccess(@NonNull Cipher authenticated) {
                            try {
                                byte[] passphrase = keystore.unwrapPassphrase(authenticated);
                                VaultLock.get().unlock(passphrase);
                                openVault();
                            } catch (Exception e) {
                                fail(getString(R.string.unlock_failed), true);
                            }
                        }

                        @Override
                        public void onError(@NonNull CharSequence message, boolean cancelled) {
                            fail(cancelled
                                    ? getString(R.string.unlock_cancelled)
                                    : message.toString(), !cancelled);
                        }
                    });
        } catch (Exception e) {
            // The key was invalidated by a biometric or screen-lock change. Retrying
            // can never work, so this is the one case that offers the way out.
            fail(getString(R.string.unlock_key_invalidated), true);
            recoverButton.setVisibility(View.VISIBLE);
        }
    }

    // -------------------------------------------------------------- recovery

    /**
     * The escape hatch for an invalidated key. The database is unreadable at this
     * point, so the only thing left is to erase it, build a fresh vault, and let
     * the user restore their backup file. Both halves of that are destructive, so
     * it asks first and says plainly what is lost.
     */
    private void confirmRecovery() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unlock_recover_title)
                .setMessage(R.string.unlock_recover_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.unlock_recover_confirm, (d, w) -> recover())
                .show();
    }

    private void recover() {
        recoverButton.setVisibility(View.GONE);
        try {
            // Order matters: drop the unreadable database first, then the key that
            // used to open it. If setup is interrupted after this, the next launch
            // is an ordinary first run rather than a half-reset state.
            AppDatabase.deleteDatabaseFiles(this);
            keystore.reset();
        } catch (Exception e) {
            fail(getString(R.string.unlock_recover_failed), true);
            recoverButton.setVisibility(View.VISIBLE);
            return;
        }

        recovered = true;
        authInFlight = false;
        statusText.setText(R.string.unlock_setup_hint);
        unlockButton.setVisibility(View.GONE);
        firstRunSetup();
    }

    private void openVault() {
        authInFlight = false;
        Intent intent = new Intent(this, MainActivity.class);
        if (recovered || restoreAfterSetup) intent.putExtra(EXTRA_OPEN_RESTORE, true);
        startActivity(intent);
        finish();
    }

    /**
     * @param rejected true for a real failure, which buzzes in the same frame as the message.
     *                 Cancelling the prompt yourself is not a rejection, so it stays silent.
     */
    private void fail(String message, boolean rejected) {
        authInFlight = false;
        statusText.setText(message);
        if (rejected) {
            statusText.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? HapticFeedbackConstants.REJECT
                    : HapticFeedbackConstants.LONG_PRESS);
        }
        unlockButton.setVisibility(View.VISIBLE);
        // Before the vault exists the button still sets it up, and the cancel message says
        // "Tap Set up", so the label has to match.
        unlockButton.setText(keystore.isInitialised() ? R.string.unlock_retry : R.string.unlock_setup_button);
    }
}
