package com.passvault.autofill;

import android.app.Activity;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.os.Bundle;
import android.service.autofill.FillResponse;
import android.view.autofill.AutofillManager;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.passvault.R;
import com.passvault.data.VaultRepository;

/**
 * Launched by the autofill framework when the user taps "Unlock PassVault" in another app.
 * Unlocks, builds the real fill response from the same screen, and hands it back.
 *
 * The framework does not re-request after an unlock: it fills from the response in
 * EXTRA_AUTHENTICATION_RESULT. Returning RESULT_OK without one (as this used to) fills nothing.
 */
public class AutofillAuthActivity extends FragmentActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        @SuppressWarnings("deprecation")
        AssistStructure structure =
                getIntent().getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE);
        if (structure == null) {
            cancel();
            return;
        }
        ParsedStructure parsed = ParsedStructure.parse(structure);

        UnlockPrompt.show(this, R.string.autofill_unlock, R.string.autofill_unlock_subtitle,
                new UnlockPrompt.Result() {
                    @Override public void onUnlocked() { respond(parsed); }
                    @Override public void onFailed() { cancel(); }
                });
    }

    /** The vault query has to run off the main thread; Room refuses it here. */
    private void respond(ParsedStructure parsed) {
        VaultRepository repository = VaultRepository.get(this);
        repository.runOnIo(() -> {
            FillResponse response = FillResponses.build(this, parsed, repository.getAllBlocking());
            runOnUiThread(() -> {
                if (response == null) {
                    cancel();
                    return;
                }
                Intent data = new Intent();
                data.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response);
                setResult(Activity.RESULT_OK, data);
                finish();
            });
        });
    }

    private void cancel() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
