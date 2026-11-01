package com.passvault.ui; // TODO: change to your package

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PersistableBundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.passvault.R;

/**
 * Fix #1 + copy feedback.
 *
 * - Flags secrets as sensitive so Android 13+ masks them in the system clipboard preview
 *   (and keyboards' clipboard suggestions) instead of showing the password in plain text.
 * - Android 13+ already shows its own "Copied" overlay, so only toast on older versions.
 * - Swaps the tapped icon to a check for 1.2s: instant feedback where the thumb already is.
 */
public final class SecureClipboard {

    // The constant is ClipDescription.EXTRA_IS_SENSITIVE on API 33+; the string works on any
    // compileSdk and is simply ignored by older systems.
    private static final String EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE";
    private static final long FEEDBACK_MS = 1200;

    private SecureClipboard() {}

    /**
     * @param sensitive true for passwords and TOTP codes, false for usernames.
     * @param button    the copy button that was tapped, or null for no icon feedback.
     * @param checkIcon a check-mark vector (add one if you don't have it, e.g. ic_check).
     */
    public static void copy(Context context, CharSequence label, CharSequence value,
                            boolean sensitive, @Nullable ImageButton button,
                            @DrawableRes int checkIcon) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        ClipData clip = ClipData.newPlainText(label, value);
        if (sensitive) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        cm.setPrimaryClip(clip);

        if (button != null) showCheck(button, checkIcon);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, label + " copied", Toast.LENGTH_SHORT).show();
        }
    }

    private static void showCheck(ImageButton button, @DrawableRes int checkIcon) {
        Object saved = button.getTag(R.id.tag_copy_original);
        final Drawable original = saved instanceof Drawable ? (Drawable) saved : button.getDrawable();
        button.setTag(R.id.tag_copy_original, original);

        // Repeated taps restart the 1.2s window instead of flickering back early.
        Object pending = button.getTag(R.id.tag_copy_restore);
        if (pending instanceof Runnable) button.removeCallbacks((Runnable) pending);

        button.setImageResource(checkIcon);
        Runnable restore = () -> {
            button.setImageDrawable(original);
            button.setTag(R.id.tag_copy_original, null);
            button.setTag(R.id.tag_copy_restore, null);
        };
        button.setTag(R.id.tag_copy_restore, restore);
        button.postDelayed(restore, FEEDBACK_MS);
    }
}
