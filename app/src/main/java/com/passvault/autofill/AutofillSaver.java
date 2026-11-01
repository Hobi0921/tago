package com.passvault.autofill;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.service.autofill.FillContext;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.passvault.R;
import com.passvault.data.PasswordEntry;
import com.passvault.data.VaultRepository;

import java.util.List;

/**
 * Turns a submitted form into a vault change: a new login, an updated password on an existing
 * one, or nothing when it is already saved. Updates go through the normal update path, so the
 * replaced password is kept in the entry's history.
 */
final class AutofillSaver {

    enum Outcome { SAVED, UPDATED, UNCHANGED, FAILED }

    static final class Result {
        final Outcome outcome;
        /** The entry saved or updated, for the confirmation message. */
        final String label;

        Result(Outcome outcome, String label) {
            this.outcome = outcome;
            this.label = label;
        }
    }

    /** What was typed, and where. Lives in memory only, never in an Intent or on disk. */
    static final class Credentials {
        final String packageName;
        @Nullable final String webDomain;
        @Nullable final String username;
        /** The login password, or a change form's current one. */
        @Nullable final String currentPassword;
        /** Set on sign-up, change and reset forms. */
        @Nullable final String newPassword;

        Credentials(String packageName, @Nullable String webDomain, @Nullable String username,
                    @Nullable String currentPassword, @Nullable String newPassword) {
            this.packageName = packageName;
            this.webDomain = webDomain;
            this.username = username;
            this.currentPassword = currentPassword;
            this.newPassword = newPassword;
        }

        String passwordToStore() {
            return newPassword != null ? newPassword : currentPassword;
        }
    }

    private static Credentials pending;

    private AutofillSaver() { }

    /**
     * Reads every screen of the save session: multi-step logins put the username on one screen
     * and the password on the next. Null if no password was typed.
     */
    @Nullable
    static Credentials extract(List<FillContext> contexts) {
        String username = null;
        ParsedStructure withPassword = null;
        for (FillContext context : contexts) {
            ParsedStructure parsed = ParsedStructure.parse(context.getStructure());
            if (parsed.usernameValue() != null) username = parsed.usernameValue().trim();
            if (parsed.currentPasswordValue() != null || parsed.newPasswordValue() != null) {
                withPassword = parsed;
            }
        }
        if (withPassword == null) return null;
        return new Credentials(withPassword.packageName, withPassword.webDomain, username,
                withPassword.currentPasswordValue(), withPassword.newPasswordValue());
    }

    /** Held while the user unlocks to save (SaveAuthActivity). One at a time is enough. */
    static synchronized void hold(Credentials credentials) {
        pending = credentials;
    }

    @Nullable
    static synchronized Credentials take() {
        Credentials c = pending;
        pending = null;
        return c;
    }

    /**
     * Applies the save. Matching, in order:
     *   1. A change form's current password identifies the entry being changed.
     *   2. The same username on this site or app.
     *   3. No username at all (change and reset forms rarely show one) and exactly one login
     *      for this site or app: that one.
     *   4. The password is already saved here: nothing to do.
     *   5. Otherwise it is a new login.
     */
    @WorkerThread
    static Result apply(Context context, Credentials c) {
        VaultRepository repository = VaultRepository.get(context);
        try {
            List<PasswordEntry> candidates = CredentialMatcher.forSite(
                    repository.getAllBlocking(), c.packageName, c.webDomain);
            String password = c.passwordToStore();

            if (c.newPassword != null && c.currentPassword != null) {
                PasswordEntry byCurrent = null;
                for (PasswordEntry e : candidates) {
                    if (!e.password.equals(c.currentPassword)) continue;
                    if (byCurrent == null || sameUser(e, c.username)) byCurrent = e;
                }
                if (byCurrent != null) return change(repository, byCurrent, c);
            }

            if (c.username != null) {
                for (PasswordEntry e : candidates) {
                    if (sameUser(e, c.username)) return changeIfDifferent(repository, e, c);
                }
            } else if (candidates.size() == 1) {
                return changeIfDifferent(repository, candidates.get(0), c);
            }

            for (PasswordEntry e : candidates) {
                if (e.password.equals(password) && (c.username == null || e.username.isEmpty())) {
                    return new Result(Outcome.UNCHANGED, e.displayLabel());
                }
            }

            PasswordEntry entry = new PasswordEntry();
            entry.siteName = siteName(context, c);
            entry.username = c.username == null ? "" : c.username;
            entry.password = password;
            entry.webDomain = c.webDomain;
            // A page saved from a browser is tied to its domain; tying it to the browser's
            // package would offer it on every site.
            entry.packageName = c.webDomain == null ? c.packageName : null;
            repository.insertBlocking(entry);
            return new Result(Outcome.SAVED, entry.displayLabel());
        } catch (IllegalStateException locked) {
            return new Result(Outcome.FAILED, "");
        }
    }

    private static Result changeIfDifferent(VaultRepository repository, PasswordEntry entry,
                                            Credentials c) {
        return entry.password.equals(c.passwordToStore())
                ? new Result(Outcome.UNCHANGED, entry.displayLabel())
                : change(repository, entry, c);
    }

    private static Result change(VaultRepository repository, PasswordEntry entry, Credentials c) {
        repository.changePasswordBlocking(entry.id, c.passwordToStore(),
                c.webDomain == null ? c.packageName : null, c.webDomain);
        return new Result(Outcome.UPDATED, entry.displayLabel());
    }

    private static boolean sameUser(PasswordEntry entry, @Nullable String username) {
        return username != null && entry.username.trim().equalsIgnoreCase(username);
    }

    /** "netflix.com" on the web; the app's own name ("Netflix") for a native app. */
    private static String siteName(Context context, Credentials c) {
        if (c.webDomain != null) return c.webDomain;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(c.packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null && label.length() > 0) return label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            // Not visible to us (see <queries> in the manifest); fall back to the package.
        }
        return c.packageName;
    }

    /** Tells the user what happened, since the system shows nothing after Save. */
    static void announce(Context context, Result result) {
        Context app = context.getApplicationContext();
        String message;
        switch (result.outcome) {
            case SAVED: message = app.getString(R.string.autofill_saved, result.label); break;
            case UPDATED: message = app.getString(R.string.autofill_updated, result.label); break;
            case UNCHANGED: message = app.getString(R.string.autofill_unchanged); break;
            default: message = app.getString(R.string.autofill_save_not_done); break;
        }
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show());
    }
}
