package com.passvault.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveInfo;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

import com.passvault.R;
import com.passvault.data.AppPrefs;
import com.passvault.data.PasswordEntry;
import com.passvault.util.PasswordGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds what the autofill popup offers. Shared by the service (vault already unlocked) and
 * AutofillAuthActivity (just unlocked), so both paths offer exactly the same thing.
 */
final class FillResponses {

    /** Dataset ids: "entry:<id>" for a saved login. Read back from the fill history. */
    static final String DATASET_ENTRY = "entry:";
    static final String DATASET_GENERATED = "generated";

    /** Client state: which app asked, and whether this was the full-vault fallback list. */
    static final String STATE_PACKAGE = "package";
    static final String STATE_LEARN = "learn";

    /** The full-vault fallback can be the whole vault; keep the popup (and the parcel) sane. */
    private static final int MAX_DATASETS = 40;

    private FillResponses() { }

    /**
     * Vault unlocked. Matching logins (or, if none match, the whole vault so the user can pick),
     * a generated password for new-password fields, and save info so new or changed passwords
     * are offered for saving. Null when there is nothing to offer or save.
     */
    @Nullable
    static FillResponse build(Context context, ParsedStructure parsed, List<PasswordEntry> all) {
        List<PasswordEntry> matches = CredentialMatcher.forSite(all, parsed.packageName,
                parsed.webDomain);
        boolean fallback = matches.isEmpty();
        List<PasswordEntry> offered = fallback ? all : matches;

        FillResponse.Builder response = new FillResponse.Builder();
        boolean any = false;

        // Saved logins go into a login form, a change form's "current" field, or the username
        // step of a multi-step login. A sign-up form only gets the generated password.
        boolean takesLogin = parsed.fillPasswordId() != null
                || (parsed.passwords.isEmpty() && parsed.usernameId != null);
        if (takesLogin) {
            int count = 0;
            for (PasswordEntry entry : offered) {
                if (count == MAX_DATASETS) break;
                Dataset dataset = loginDataset(context, parsed, entry);
                if (dataset != null) {
                    response.addDataset(dataset);
                    any = true;
                    count++;
                }
            }
        }

        if (parsed.hasNewPasswordField()) {
            response.addDataset(generatedDataset(context, parsed));
            any = true;
        }

        SaveInfo save = saveInfo(parsed);
        if (save != null) response.setSaveInfo(save);
        if (!any && save == null) return null;

        // Only a native app screen learns links. A web page is matched by its domain, and
        // linking a login to the browser's package would offer it on every site.
        Bundle state = new Bundle();
        state.putString(STATE_PACKAGE, parsed.packageName);
        state.putBoolean(STATE_LEARN, fallback && parsed.webDomain == null);
        response.setClientState(state);
        return response.build();
    }

    /**
     * Vault locked: a single "Unlock PassVault" row. Picking it runs AutofillAuthActivity,
     * which returns the real response. Save info is attached so saving still works.
     */
    static FillResponse locked(Context context, ParsedStructure parsed) {
        RemoteViews presentation = row(context,
                context.getString(R.string.autofill_unlock),
                context.getString(R.string.autofill_unlock_subtitle));

        // The framework writes the assist structure into this intent, so it must be mutable.
        int flags = PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, 1001,
                new Intent(context, AutofillAuthActivity.class), flags);

        FillResponse.Builder builder = new FillResponse.Builder();
        builder.setAuthentication(parsed.allIds(), pending.getIntentSender(), presentation);
        SaveInfo save = saveInfo(parsed);
        if (save != null) builder.setSaveInfo(save);
        return builder.build();
    }

    // ------------------------------------------------------------------ datasets

    @Nullable
    private static Dataset loginDataset(Context context, ParsedStructure parsed,
                                        PasswordEntry entry) {
        String subtitle = entry.username.isEmpty()
                ? context.getString(R.string.autofill_no_username) : entry.username;
        Dataset.Builder dataset = new Dataset.Builder(row(context, entry.displayLabel(), subtitle));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dataset.setId(DATASET_ENTRY + entry.id);
        }

        boolean any = false;
        if (parsed.usernameId != null && !entry.username.isEmpty()) {
            dataset.setValue(parsed.usernameId, AutofillValue.forText(entry.username));
            any = true;
        }
        AutofillId passwordId = parsed.fillPasswordId();
        if (passwordId != null) {
            dataset.setValue(passwordId, AutofillValue.forText(entry.password));
            any = true;
        }
        return any ? dataset.build() : null;
    }

    /**
     * Fills the new and confirm fields with one generated password, using the generator tab's
     * settings. Its value is deliberately not shown in the popup.
     *
     * Saving it relies on the form's username field: Android skips the save prompt when every
     * field still holds exactly what a dataset filled, and the typed username is what differs.
     */
    private static Dataset generatedDataset(Context context, ParsedStructure parsed) {
        AppPrefs prefs = new AppPrefs(context);
        String password = prefs.generatorUsesWords()
                ? PasswordGenerator.generatePassphrase(prefs.wordCount(), prefs.appendNumber())
                : PasswordGenerator.generate(prefs.generatorOptions());

        Dataset.Builder dataset = new Dataset.Builder(row(context,
                context.getString(R.string.autofill_generated_title),
                context.getString(R.string.autofill_generated_subtitle)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dataset.setId(DATASET_GENERATED);
        for (AutofillId id : parsed.newPasswordIds()) {
            dataset.setValue(id, AutofillValue.forText(password));
        }
        return dataset.build();
    }

    // ------------------------------------------------------------------ saving

    /**
     * Offers to save once the form is submitted. Required is the password that will be stored
     * (the new one on sign-up and change forms); everything else is optional.
     * FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE covers apps that never finish the login activity
     * (single-activity apps swapping fragments). A screen with only a username is the first
     * step of a multi-step login: on API 28+ the save waits for the password step.
     */
    @Nullable
    private static SaveInfo saveInfo(ParsedStructure parsed) {
        AutofillId primary = null;
        List<AutofillId> optional = new ArrayList<>();
        for (ParsedStructure.PasswordField f : parsed.passwords) {
            boolean stored = parsed.hasNewPasswordField()
                    ? f.role() == ParsedStructure.Role.NEW
                    : f.role() == ParsedStructure.Role.CURRENT;
            if (stored && primary == null) {
                primary = f.id;
            } else {
                optional.add(f.id);
            }
        }

        if (primary == null) {
            if (parsed.usernameId == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return null;
            }
            return new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_USERNAME,
                    new AutofillId[]{parsed.usernameId})
                    .setFlags(SaveInfo.FLAG_DELAY_SAVE)
                    .build();
        }

        int type = SaveInfo.SAVE_DATA_TYPE_PASSWORD;
        if (parsed.usernameId != null) {
            type |= SaveInfo.SAVE_DATA_TYPE_USERNAME;
            optional.add(parsed.usernameId);
        }
        SaveInfo.Builder save = new SaveInfo.Builder(type, new AutofillId[]{primary})
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE);
        if (!optional.isEmpty()) save.setOptionalIds(optional.toArray(new AutofillId[0]));
        return save.build();
    }

    // ------------------------------------------------------------------ views

    private static RemoteViews row(Context context, CharSequence title, CharSequence subtitle) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.autofill_item);
        views.setTextViewText(R.id.autofillTitle, title);
        views.setTextViewText(R.id.autofillSubtitle, subtitle);
        return views;
    }
}
