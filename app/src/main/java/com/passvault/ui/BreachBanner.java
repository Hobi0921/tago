package com.passvault.ui; // TODO: change to your package

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.passvault.R;

/**
 * The vault's breach alert. Hidden at 0 (the shot never shows that state),
 * correct plural at 1, and the whole card opens the details.
 */
public final class BreachBanner {

    private BreachBanner() {}

    /**
     * @param breachedCount result of the last breach check; pass 0 if no check has run yet.
     * @param onOpen        typically: bottomNav.setSelectedItemId(R.id.<your health tab id>)
     */
    public static void bind(View fragmentRoot, int breachedCount, View.OnClickListener onOpen) {
        View banner = fragmentRoot.findViewById(R.id.vaultBreachBanner);
        boolean show = breachedCount > 0;
        if ((banner.getVisibility() == View.VISIBLE) != show) {
            Motion.beginLayoutChange((ViewGroup) banner.getParent());
        }
        banner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        String title = fragmentRoot.getResources()
                .getQuantityString(R.plurals.vault_breach_title, breachedCount, breachedCount);
        ((TextView) fragmentRoot.findViewById(R.id.vaultBreachTitle)).setText(title);
        banner.setContentDescription(title + ". "
                + fragmentRoot.getContext().getString(R.string.vault_breach_details));
        banner.setOnClickListener(onOpen);
    }
}
