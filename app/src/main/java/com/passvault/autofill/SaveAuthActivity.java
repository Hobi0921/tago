package com.passvault.autofill;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.passvault.R;
import com.passvault.data.VaultRepository;

/**
 * Finishes a save the user confirmed while the vault was locked (Android 9+). The service
 * holds the typed credentials in memory and has the framework start this activity; it unlocks,
 * saves or updates, says which, and closes. Without it, a save from another app failed
 * whenever auto-lock had already run, which is most of the time.
 */
public class SaveAuthActivity extends FragmentActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // Recreated despite configChanges (e.g. process trim): the credentials were already
            // taken by the first instance, so there is nothing left to do. Never sit here as an
            // invisible window over the other app.
            finish();
            return;
        }

        AutofillSaver.Credentials credentials = AutofillSaver.take();
        if (credentials == null) {
            // Process restarted since the save was requested; the typed values are gone.
            AutofillSaver.announce(this, new AutofillSaver.Result(
                    AutofillSaver.Outcome.FAILED, ""));
            finish();
            return;
        }

        UnlockPrompt.show(this, R.string.autofill_save_unlock_title,
                R.string.autofill_save_unlock_subtitle, new UnlockPrompt.Result() {
                    @Override public void onUnlocked() { save(credentials); }
                    @Override public void onFailed() { finish(); }
                });
    }

    private void save(AutofillSaver.Credentials credentials) {
        VaultRepository.get(this).runOnIo(() -> {
            AutofillSaver.announce(this, AutofillSaver.apply(this, credentials));
            runOnUiThread(this::finish);
        });
    }
}
