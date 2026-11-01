package com.passvault.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.passvault.util.PasswordGenerator;

/**
 * Non-secret settings. Nothing here is sensitive: the vault contents live in the
 * encrypted database, and the wrapped passphrase lives in KeystoreManager's own
 * preference file. Keeping them apart means clearing settings can never damage
 * the vault.
 */
public final class AppPrefs {

    private static final String FILE = "passvault_settings";

    private static final String KEY_LAST_BACKUP = "last_backup_at";
    private static final String KEY_AUTO_LOCK = "auto_lock_ms";
    private static final String KEY_BACKUP_NAG_DISMISSED = "backup_nag_dismissed";
    private static final String KEY_AUTOFILL_NUDGE_DISMISSED = "autofill_nudge_dismissed";

    private static final String KEY_GEN_MODE_WORDS = "gen_mode_words";
    private static final String KEY_GEN_LENGTH = "gen_length";
    private static final String KEY_GEN_WORDS = "gen_words";
    private static final String KEY_GEN_NUMBER = "gen_words_number";
    private static final String KEY_GEN_LOWER = "gen_lower";
    private static final String KEY_GEN_UPPER = "gen_upper";
    private static final String KEY_GEN_DIGITS = "gen_digits";
    private static final String KEY_GEN_SYMBOLS = "gen_symbols";
    private static final String KEY_GEN_AMBIGUOUS = "gen_ambiguous";

    /** Matches VaultLock's own default, so a fresh install behaves as before. */
    public static final long DEFAULT_AUTO_LOCK_MS = 60_000L;

    /** Options offered in Settings. 0 means lock the moment the app is backgrounded. */
    public static final long[] AUTO_LOCK_CHOICES = {
            0L, 30_000L, 60_000L, 300_000L, 900_000L
    };

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------- auto-lock

    public long autoLockMs() {
        return prefs.getLong(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK_MS);
    }

    public void setAutoLockMs(long ms) {
        prefs.edit().putLong(KEY_AUTO_LOCK, ms).apply();
    }

    // ----------------------------------------------------------------- backup

    /** Epoch millis of the last successful export, or 0 if there has never been one. */
    public long lastBackupAt() {
        return prefs.getLong(KEY_LAST_BACKUP, 0L);
    }

    public void recordBackup() {
        prefs.edit()
                .putLong(KEY_LAST_BACKUP, System.currentTimeMillis())
                .putBoolean(KEY_BACKUP_NAG_DISMISSED, false)
                .apply();
    }

    public boolean backupReminderDismissed() {
        return prefs.getBoolean(KEY_BACKUP_NAG_DISMISSED, false);
    }

    public void dismissBackupReminder() {
        prefs.edit().putBoolean(KEY_BACKUP_NAG_DISMISSED, true).apply();
    }

    // --------------------------------------------------------------- autofill

    /** "Not now" on the vault's turn-on-autofill card. Settings still offers it after. */
    public boolean autofillNudgeDismissed() {
        return prefs.getBoolean(KEY_AUTOFILL_NUDGE_DISMISSED, false);
    }

    public void dismissAutofillNudge() {
        prefs.edit().putBoolean(KEY_AUTOFILL_NUDGE_DISMISSED, true).apply();
    }

    // -------------------------------------------------------------- generator

    public boolean generatorUsesWords() {
        return prefs.getBoolean(KEY_GEN_MODE_WORDS, false);
    }

    public int wordCount() {
        return prefs.getInt(KEY_GEN_WORDS, 5);
    }

    public boolean appendNumber() {
        return prefs.getBoolean(KEY_GEN_NUMBER, true);
    }

    /**
     * The saved character-password settings. Read by the Generate button in the
     * entry editor too, so it produces the same kind of password the generator
     * tab is set up for instead of silently using library defaults.
     */
    public PasswordGenerator.Options generatorOptions() {
        PasswordGenerator.Options options = new PasswordGenerator.Options();
        options.length = prefs.getInt(KEY_GEN_LENGTH, options.length);
        options.useLower = prefs.getBoolean(KEY_GEN_LOWER, options.useLower);
        options.useUpper = prefs.getBoolean(KEY_GEN_UPPER, options.useUpper);
        options.useDigits = prefs.getBoolean(KEY_GEN_DIGITS, options.useDigits);
        options.useSymbols = prefs.getBoolean(KEY_GEN_SYMBOLS, options.useSymbols);
        options.avoidAmbiguous = prefs.getBoolean(KEY_GEN_AMBIGUOUS, options.avoidAmbiguous);

        // Defensive: every class off would make the generator fall back to
        // lowercase without saying so. Should be unreachable now the UI keeps
        // one box checked, but old saved state could still hold it.
        if (!options.useLower && !options.useUpper
                && !options.useDigits && !options.useSymbols) {
            options.useLower = true;
        }
        return options;
    }

    public void saveGenerator(boolean useWords, PasswordGenerator.Options options,
                              int wordCount, boolean appendNumber) {
        prefs.edit()
                .putBoolean(KEY_GEN_MODE_WORDS, useWords)
                .putInt(KEY_GEN_LENGTH, options.length)
                .putBoolean(KEY_GEN_LOWER, options.useLower)
                .putBoolean(KEY_GEN_UPPER, options.useUpper)
                .putBoolean(KEY_GEN_DIGITS, options.useDigits)
                .putBoolean(KEY_GEN_SYMBOLS, options.useSymbols)
                .putBoolean(KEY_GEN_AMBIGUOUS, options.avoidAmbiguous)
                .putInt(KEY_GEN_WORDS, wordCount)
                .putBoolean(KEY_GEN_NUMBER, appendNumber)
                .apply();
    }
}
