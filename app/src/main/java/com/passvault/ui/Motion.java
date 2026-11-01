package com.passvault.ui; // TODO: change to your package

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.passvault.R;

import java.util.function.IntConsumer;

/**
 * Motion fixes, translated from Emil Kowalski's rules to Android:
 * ease-out for anything responding to the user, linear for constant motion,
 * UI animations under 300ms, nothing on frequently repeated/typed actions,
 * and everything respects the system "Remove animations" setting.
 */
public final class Motion {

    /** cubic-bezier(0.23, 1, 0.32, 1) — same curve as res/interpolator/ease_out_strong.xml. */
    public static final PathInterpolator EASE_OUT = new PathInterpolator(0.23f, 1f, 0.32f, 1f);

    /**
     * Material's standard curve, for a line being drawn (the Tago pattern traces). A strong
     * ease-out puts most of the line down in the first frames, so it never reads as traced.
     */
    public static final PathInterpolator TRACE = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private Motion() {}

    /** False when the user turned animations off (animator duration scale = 0). */
    public static boolean enabled() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    // ---------------------------------------------------------------- strength bar

    /**
     * Checker strength bar. 150ms ease-out, and each keystroke retargets from wherever the
     * bar currently is instead of restarting — so it never lags behind typing.
     * Replace `checkerBar.setProgress(score)` with `Motion.tweenProgress(checkerBar, score)`.
     */
    public static void tweenProgress(ProgressBar bar, int target) {
        Object running = bar.getTag(R.id.tag_progress_animator);
        if (running instanceof Animator) ((Animator) running).cancel();
        if (!enabled()) {
            bar.setProgress(target);
            return;
        }
        ObjectAnimator a = ObjectAnimator.ofInt(bar, "progress", bar.getProgress(), target);
        a.setDuration(150);
        a.setInterpolator(EASE_OUT);
        bar.setTag(R.id.tag_progress_animator, a);
        a.start();
    }

    // ---------------------------------------------------------------- layout changes

    /**
     * Call immediately BEFORE toggling visibility (generator mode switch, checker advice
     * rows, backup banner dismiss, health offline notice). 200ms, no-op if motion is off.
     */
    public static void beginLayoutChange(ViewGroup sceneRoot) {
        if (!enabled()) return;
        AutoTransition t = new AutoTransition();
        t.setDuration(200);
        t.setInterpolator(EASE_OUT);
        TransitionManager.beginDelayedTransition(sceneRoot, t);
    }

    // ---------------------------------------------------------------- search

    /**
     * Search filters on every keystroke; animating each diff makes search feel slow.
     * Call with the query whenever it changes.
     */
    public static void setSearching(RecyclerView list, CharSequence query) {
        boolean searching = query != null && query.length() > 0;
        if (searching && list.getItemAnimator() != null) {
            list.setItemAnimator(null);
        } else if (!searching && list.getItemAnimator() == null) {
            list.setItemAnimator(new DefaultItemAnimator());
        }
    }

    // ---------------------------------------------------------------- health score

    /**
     * Count the health score up from 0. Rarely seen screen, so a little delight is fine —
     * but call it only the first time the screen shows per unlock, not on every resume.
     */
    public static void countUp(TextView view, int score) {
        if (!enabled() || score <= 0) {
            view.setText(String.valueOf(score));
            return;
        }
        ValueAnimator a = ValueAnimator.ofInt(0, score);
        a.setDuration(600);
        a.setInterpolator(EASE_OUT);
        a.addUpdateListener(v -> view.setText(String.valueOf((int) v.getAnimatedValue())));
        a.start();
    }

    // ---------------------------------------------------------------- TOTP ring

    /**
     * Smooth, linear TOTP countdown aligned to the real 30s window (constant motion → linear).
     * Usage in the detail sheet:
     *
     *   totpRing = new Motion.TotpRing(ring, secondsView, 30, () -> showCode(generateTotp()));
     *   totpRing.setOnSecond(left -> ...);   // optional, e.g. warn in the last seconds
     *   totpRing.start();      // in onStart / when the sheet opens; safe to call again
     *   totpRing.stop();       // in onStop / onDismiss — always, or it leaks
     *
     * Every frame reads the wall clock. The animator is only a frame source: its own value
     * follows the developer "Animator duration scale", so at 0.5x a ring driven by it emptied
     * in 15 seconds while the code was still valid.
     */
    public static final class TotpRing {
        public interface OnPeriodEnd { void onNewPeriod(); }

        private static final int SCALE = 1000; // progress units per second, for smoothness

        private final CircularProgressIndicator ring;
        private final TextView seconds;
        private final int periodSec;
        private final OnPeriodEnd onPeriodEnd;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private IntConsumer onSecond;
        private ValueAnimator animator;
        private boolean running;
        private long lastRemaining;
        private int lastSecond;

        public TotpRing(CircularProgressIndicator ring, TextView seconds, int periodSec,
                        OnPeriodEnd onPeriodEnd) {
            this.ring = ring;
            this.seconds = seconds;
            this.periodSec = periodSec;
            this.onPeriodEnd = onPeriodEnd;
            ring.setMax(periodSec * SCALE);
        }

        /** Called with the whole seconds left, each time that number changes (and on start). */
        public void setOnSecond(IntConsumer onSecond) {
            this.onSecond = onSecond;
        }

        public void start() {
            if (running) return;
            running = true;
            lastRemaining = remainingMs();
            lastSecond = -1;
            render(lastRemaining);

            if (!enabled()) {
                // With animations off, a ValueAnimator jumps straight to its end. Tick once a
                // second, on the second, instead.
                tickEachSecond();
                return;
            }
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> frame());
            animator.start();
        }

        public void stop() {
            running = false;
            if (animator != null) {
                animator.removeAllUpdateListeners();
                animator.cancel();
                animator = null;
            }
            handler.removeCallbacksAndMessages(null);
        }

        private long remainingMs() {
            long periodMs = periodSec * 1000L;
            return periodMs - (System.currentTimeMillis() % periodMs);
        }

        private void tickEachSecond() {
            handler.postDelayed(() -> {
                frame();
                if (running) tickEachSecond();
            }, 1000 - (System.currentTimeMillis() % 1000));
        }

        private void frame() {
            if (!running) return;
            long remaining = remainingMs();
            // Remaining time only grows when a new window has begun.
            if (remaining > lastRemaining) onPeriodEnd.onNewPeriod();
            lastRemaining = remaining;
            render(remaining);
        }

        private void render(long remainingMs) {
            ring.setProgressCompat((int) (remainingMs * SCALE / 1000), false);
            int left = (int) Math.ceil(remainingMs / 1000.0);
            if (left != lastSecond) {
                lastSecond = left;
                seconds.setText(String.valueOf(left));
                if (onSecond != null) onSecond.accept(left);
            }
        }
    }
}
