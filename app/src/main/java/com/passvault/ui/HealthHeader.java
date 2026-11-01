package com.passvault.ui; // TODO: change to your package

import android.animation.ValueAnimator;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.passvault.R; // TODO: your R

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Binds the redesigned health header: score ring, number, and the four stats.
 *
 * The ring sweeps and the number counts up together (one animator, so they can't drift),
 * 700ms strong ease-out. It's a rarely seen screen, so this is the one place we spend motion
 * on delight. Pass animate=true only on the first show per unlock; every later bind is instant.
 */
public final class HealthHeader {

    private HealthHeader() {}

    /**
     * @param compromised breached entries, or -1 if no breach check has run yet (shows "–")
     * @param ringColor   colour for the ring's progress arc (e.g. by score band)
     */
    public static void bind(View root, int score, int compromised, int weak, int reused, int safe,
                            int ringColor, boolean animate) {
        CircularProgressIndicator ring = root.findViewById(R.id.healthRing);
        TextView number = root.findViewById(R.id.healthScore);
        View frame = root.findViewById(R.id.healthRingFrame);

        int clamped = Math.max(0, Math.min(100, score));
        ring.setIndicatorColor(ringColor);
        frame.setContentDescription(root.getContext().getString(R.string.health_score_a11y, clamped));

        if (animate && Motion.enabled() && clamped > 0) {
            ValueAnimator a = ValueAnimator.ofInt(0, clamped);
            a.setDuration(700);
            a.setInterpolator(Motion.EASE_OUT);
            a.addUpdateListener(v -> {
                int value = (int) v.getAnimatedValue();
                ring.setProgressCompat(value, false);
                number.setText(String.valueOf(value));
            });
            a.start();
        } else {
            ring.setProgressCompat(clamped, false);
            number.setText(String.valueOf(clamped));
        }

        NumberFormat nf = NumberFormat.getIntegerInstance();
        if (compromised < 0) {
            // No breach check has run yet: "0" would falsely say nothing is breached.
            ((TextView) root.findViewById(R.id.healthStatCompromised)).setText("–");
            root.findViewById(R.id.healthCellCompromised).setContentDescription(
                    root.getContext().getString(R.string.health_stat_not_checked));
        } else {
            stat(root, R.id.healthCellCompromised, R.id.healthStatCompromised,
                    R.string.health_stat_compromised, compromised, nf);
        }
        stat(root, R.id.healthCellWeak, R.id.healthStatWeak, R.string.health_stat_weak, weak, nf);
        stat(root, R.id.healthCellReused, R.id.healthStatReused, R.string.health_stat_reused, reused, nf);
        stat(root, R.id.healthCellSafe, R.id.healthStatSafe, R.string.health_stat_safe, safe, nf);
    }

    /** Wire a stat cell to whatever should happen on tap (e.g. scroll to that finding). */
    public static void onStatClick(View root, int cellId, View.OnClickListener listener) {
        root.findViewById(cellId).setOnClickListener(listener);
    }

    private static void stat(View root, int cellId, int numberId, int labelRes, int count,
                             NumberFormat nf) {
        TextView number = root.findViewById(numberId);
        number.setText(nf.format(count));          // "1,284", not "1284"
        View cell = root.findViewById(cellId);
        String label = root.getContext().getString(labelRes).toLowerCase(Locale.getDefault());
        cell.setContentDescription(root.getResources()
                .getQuantityString(R.plurals.health_stat_a11y, count, count, label));
    }
}
