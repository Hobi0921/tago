package com.passvault.autofill;

import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillEventHistory;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;

import androidx.annotation.NonNull;

import com.passvault.R;
import com.passvault.data.VaultRepository;
import com.passvault.security.VaultLock;

import java.util.List;

/**
 * Fills logins into other apps and browsers, and saves new or changed passwords.
 *
 * Fill:
 *   - Unlocked → logins for this app or site (or the whole vault to pick from), plus a
 *                generated password on sign-up and change-password forms.
 *   - Locked   → one "Unlock PassVault" row; AutofillAuthActivity unlocks and returns the
 *                real response. Credentials never leave the vault while it is locked.
 *
 * Save: after a form is submitted and the user taps Save, the typed credentials become a new
 * login, an updated password on the matching login (old one kept in history), or nothing if
 * already saved. When locked, SaveAuthActivity unlocks first (Android 9+).
 *
 * Both callbacks arrive on the main thread, and Room refuses queries there, so all vault work
 * runs on the repository's IO thread.
 */
public class PassVaultAutofillService extends AutofillService {

    @Override
    public void onFillRequest(@NonNull FillRequest request,
                              @NonNull CancellationSignal cancellationSignal,
                              @NonNull FillCallback callback) {
        List<FillContext> contexts = request.getFillContexts();
        if (contexts.isEmpty()) {
            callback.onSuccess(null);
            return;
        }

        AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();
        ParsedStructure parsed = ParsedStructure.parse(structure);

        // Never offer the vault to PassVault's own fields.
        if (!parsed.isUsable() || getPackageName().equals(parsed.packageName)) {
            callback.onSuccess(null);
            return;
        }

        if (!VaultLock.get().isUnlocked()) {
            callback.onSuccess(FillResponses.locked(this, parsed));
            return;
        }

        VaultRepository repository = VaultRepository.get(this);
        repository.runOnIo(() -> {
            learnFromLastPick(repository);
            FillResponse response = FillResponses.build(this, parsed, repository.getAllBlocking());
            if (cancellationSignal.isCanceled()) return;
            callback.onSuccess(response);
        });
    }

    /**
     * If the user picked a login from the full-vault list in an app last time, link it to that
     * app, so from now on it is offered first there (and found when that app changes it).
     */
    private void learnFromLastPick(VaultRepository repository) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        FillEventHistory history = getFillEventHistory();
        if (history == null || history.getEvents() == null) return;

        for (FillEventHistory.Event event : history.getEvents()) {
            if (event.getType() != FillEventHistory.Event.TYPE_DATASET_SELECTED) continue;
            Bundle state = event.getClientState();
            String datasetId = event.getDatasetId();
            if (state == null || !state.getBoolean(FillResponses.STATE_LEARN)
                    || datasetId == null || !datasetId.startsWith(FillResponses.DATASET_ENTRY)) {
                continue;
            }
            String pkg = state.getString(FillResponses.STATE_PACKAGE);
            if (pkg == null) continue;
            try {
                long id = Long.parseLong(datasetId.substring(FillResponses.DATASET_ENTRY.length()));
                repository.linkPackageBlocking(id, pkg);
            } catch (NumberFormatException | IllegalStateException ignored) {
                // Malformed id, or locked in the meantime; it will be learned next time.
            }
        }
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {
        AutofillSaver.Credentials credentials = AutofillSaver.extract(request.getFillContexts());
        if (credentials == null) {
            callback.onFailure(getString(R.string.autofill_save_failed));
            return;
        }

        if (VaultLock.get().isUnlocked()) {
            callback.onSuccess();
            VaultRepository.get(this).runOnIo(() ->
                    AutofillSaver.announce(this, AutofillSaver.apply(this, credentials)));
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AutofillSaver.hold(credentials);
            callback.onSuccess(saveAfterUnlock());
        } else {
            callback.onFailure(getString(R.string.autofill_save_locked));
        }
    }

    /** Started by the framework straight after Save; nothing secret travels in the intent. */
    private IntentSender saveAfterUnlock() {
        PendingIntent pending = PendingIntent.getActivity(this, 1002,
                new Intent(this, SaveAuthActivity.class),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return pending.getIntentSender();
    }
}
