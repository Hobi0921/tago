package com.passvault.ui; // TODO: change to your package

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.passvault.R; // TODO: your R

import java.util.Locale;

/**
 * Entry avatars, like the shot: the site's real logo on a soft circle tinted with the logo's
 * own brand colour (Keep → pale yellow, PayPal → pale blue). Until the logo arrives, or when a
 * site has none, it shows a coloured initial instead, so a row never looks empty.
 */
public final class Avatars {

    private static final String[][] LIGHT = {
            {"#FFF0C2", "#6E4A00"}, {"#FDE1EA", "#9C1F4B"}, {"#E2F2D7", "#2C6420"},
            {"#DCEBFF", "#1B5592"}, {"#ECE8FF", "#4A44A0"}, {"#FFE3D6", "#9E3B19"},
            {"#DDF3F1", "#1D6660"}, {"#F1E6DA", "#6B4A2B"},
    };
    private static final String[][] DARK = {
            {"#3D3218", "#F5D47E"}, {"#45202D", "#FFB3CB"}, {"#233A1E", "#A8E29A"},
            {"#1C3350", "#A9CCFF"}, {"#2E2A55", "#C7C2FF"}, {"#47271C", "#FFB89B"},
            {"#173A37", "#8FDCD4"}, {"#3A2F25", "#E3C6A6"},
    };

    private Avatars() {}

    /**
     * @param frame   the 48dp avatar container (gets the circle background)
     * @param initial the initial TextView (fallback)
     * @param icon    the logo ImageView inside the circle
     * @param domain  the entry's domain field; may be empty
     * @param name    the entry's site name
     */
    public static void bind(View frame, TextView initial, ImageView icon, String domain, String name) {
        Context ctx = frame.getContext();
        boolean night = isNight(ctx);

        // Fallback state first, every bind (rows are recycled).
        initial.setText(UiText.initialOf(name));
        String[] pair = pick(night, domain == null || domain.isEmpty() ? name : domain);
        frame.setBackground(circle(Color.parseColor(pair[0])));
        initial.setTextColor(Color.parseColor(pair[1]));
        initial.setVisibility(View.VISIBLE);
        icon.animate().cancel();
        icon.setAlpha(1f);
        icon.setImageDrawable(null);
        icon.setVisibility(View.GONE);

        // Entries named like a domain ("paypal.com") get a logo even with no domain field.
        String lookup = domain != null && !domain.trim().isEmpty() ? domain : name;
        SiteIcons.load(icon, lookup, (bitmap, brand, arrivedLater) -> {
            int surface = ContextCompat.getColor(ctx, R.color.surface);
            // Soft brand tint: ~16% of the logo colour over the surface (stronger in dark mode).
            int tint = ColorUtils.blendARGB(surface, brand, night ? 0.30f : 0.16f);
            frame.setBackground(circle(tint));
            icon.setImageBitmap(bitmap);
            initial.setVisibility(View.INVISIBLE);
            icon.setVisibility(View.VISIBLE);
            if (arrivedLater && Motion.enabled()) {
                icon.setAlpha(0f);
                icon.animate().alpha(1f).setDuration(150).setInterpolator(Motion.EASE_OUT).start();
            }
        });
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private static boolean isNight(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static String[] pick(boolean night, String key) {
        String[][] palette = night ? DARK : LIGHT;
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (k.startsWith("www.")) k = k.substring(4);
        return palette[Math.floorMod(k.hashCode(), palette.length)];
    }
}
