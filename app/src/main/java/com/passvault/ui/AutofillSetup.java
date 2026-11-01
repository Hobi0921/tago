package com.passvault.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.autofill.AutofillManager;

/**
 * Whether Tago is the device's autofill service, and how to ask Android to make it so.
 * Shared by Settings and the vault's first-run card so both agree on what "on" means.
 */
public final class AutofillSetup {

    private AutofillSetup() { }

    /** False on devices without the autofill framework (some Android Go builds). */
    public static boolean isSupported(Context context) {
        AutofillManager manager = context.getSystemService(AutofillManager.class);
        return manager != null && manager.isAutofillSupported();
    }

    /** True only when Tago itself is the selected service, not just any provider. */
    public static boolean isEnabled(Context context) {
        AutofillManager manager = context.getSystemService(AutofillManager.class);
        return manager != null && manager.isAutofillSupported() && manager.hasEnabledAutofillServices();
    }

    /**
     * ACTION_REQUEST_SET_AUTOFILL_SERVICE asks to make Tago the provider directly. Some OEM
     * builds do not ship that screen, so fall back to the general settings.
     *
     * @return false if neither screen exists, so the caller can say so instead of crashing.
     */
    public static boolean open(Context context) {
        Intent request = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData(Uri.parse("package:" + context.getPackageName()));
        return launch(context, request) || launch(context, new Intent(Settings.ACTION_SETTINGS));
    }

    private static boolean launch(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
