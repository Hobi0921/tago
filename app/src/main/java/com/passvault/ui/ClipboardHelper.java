package com.passvault.ui;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;
import com.passvault.R;

/**
 * The one way secrets reach the clipboard. Previously the vault and the generator
 * each had their own copy code, and only the vault's marked the clip sensitive and
 * cleared it, even though both showed the "clears in 45 seconds" message.
 */
public final class ClipboardHelper {

    private static final long CLEAR_AFTER_MS = 45_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Copies a secret, confirms with a haptic tick and a snackbar, and schedules a clear. */
    public static void copySecret(@NonNull View anchor, @NonNull String value,
                                  @StringRes int message) {
        Context context = anchor.getContext().getApplicationContext();
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;

        ClipData clip = ClipData.newPlainText("PassVault", value);

        // Keeps the value out of the Android 13+ clipboard preview and, on
        // keyboards that honour it, out of clipboard history.
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        clip.getDescription().setExtras(extras);

        clipboard.setPrimaryClip(clip);

        anchor.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY);

        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show();

        MAIN.postDelayed(() -> clearIfStill(clipboard, value), CLEAR_AFTER_MS);
    }

    /** For non-secret values such as a username: no sensitive flag, no timed clear. */
    public static void copyPlain(@NonNull View anchor, @NonNull String value,
                                 @StringRes int message) {
        ClipboardManager clipboard = (ClipboardManager) anchor.getContext()
                .getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("PassVault", value));
        anchor.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show();
    }

    /** Only clears if our value is still there, so we never wipe something copied since. */
    private static void clearIfStill(ClipboardManager clipboard, String value) {
        try {
            ClipData current = clipboard.getPrimaryClip();
            if (current == null || current.getItemCount() == 0) return;
            CharSequence text = current.getItemAt(0).getText();
            if (text == null || !text.toString().equals(value)) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip();
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
            }
        } catch (SecurityException ignored) {
            // Android 10+ denies clipboard reads while in the background. The
            // system also clears sensitive clips on its own schedule there.
        }
    }

    private ClipboardHelper() { }
}
