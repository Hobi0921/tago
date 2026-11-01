package com.passvault.ui;

import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;

/**
 * FLAG_SECURE is per window. A dialog or bottom sheet gets its own window and does
 * not inherit the flag from the activity behind it, so without this a screenshot or
 * screen recording captures a revealed password shown in a dialog.
 */
public final class SecureWindows {

    public static <T extends Dialog> T secure(T dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        return dialog;
    }

    private SecureWindows() { }
}
