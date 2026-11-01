package com.passvault.util;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.passvault.R;

import java.util.ArrayList;
import java.util.List;

/** Outcome of a strength analysis: a label, a 0–100 score, entropy, and advice. */
public class StrengthResult {

    public enum Level {
        VERY_WEAK("Very weak", R.color.strength_very_weak),
        WEAK("Weak", R.color.strength_weak),
        FAIR("Fair", R.color.strength_fair),
        STRONG("Strong", R.color.strength_strong),
        VERY_STRONG("Very strong", R.color.strength_very_strong);

        public final String label;
        @ColorRes public final int colorRes;

        Level(String label, @ColorRes int colorRes) {
            this.label = label;
            this.colorRes = colorRes;
        }

        /**
         * Resolved against the current theme. These used to be fixed ARGB values from the
         * dark design, unreadable as text on the light page.
         */
        @ColorInt
        public int color(Context context) {
            return ContextCompat.getColor(context, colorRes);
        }
    }

    public final Level level;
    public final int score;          // 0–100, drives the progress bar
    public final double entropyBits; // estimated, after pattern penalties
    public final List<String> advice;
    public final String crackTime;   // human-readable estimate

    public StrengthResult(Level level, int score, double entropyBits,
                          List<String> advice, String crackTime) {
        this.level = level;
        this.score = score;
        this.entropyBits = entropyBits;
        this.advice = advice == null ? new ArrayList<>() : advice;
        this.crackTime = crackTime;
    }
}
