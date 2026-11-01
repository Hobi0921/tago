package com.passvault.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.passvault.R;

/**
 * First-run screen. Shown once, before the unlock/setup flow, then never again.
 * UnlockActivity (the launcher) routes here when no vault exists and it has not been seen.
 *
 * "Set up Tago" goes to normal setup; "I have a backup" goes to setup with EXTRA_RESTORE, and
 * once the vault exists the restore flow opens straight away.
 */
public class OnboardingActivity extends AppCompatActivity {

    public static final String EXTRA_RESTORE = "com.passvault.extra.RESTORE";
    private static final String PREFS = "onboarding";
    private static final String KEY_SEEN = "seen";

    /** Runs next: the unlock screen, which does first-time setup when no vault exists. */
    private static final Class<? extends Activity> NEXT = UnlockActivity.class;

    /**
     * @param vaultExists true if a vault is already set up on this device. Existing users who
     *                    update the app must never be sent back through onboarding.
     */
    public static boolean shouldShow(Context ctx, boolean vaultExists) {
        if (vaultExists) {
            prefs(ctx).edit().putBoolean(KEY_SEEN, true).apply();
            return false;
        }
        return !prefs(ctx).getBoolean(KEY_SEEN, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        findViewById(R.id.onboardingContinue).setOnClickListener(v -> finishOnboarding(false));
        findViewById(R.id.onboardingRestore).setOnClickListener(v -> finishOnboarding(true));

        if (savedInstanceState == null) playEntrance();
    }

    /**
     * The card rises 24dp and fades in once, 360ms ease-out, art a beat earlier. Then the T in
     * the art traces itself from its coral start node (avd_onboarding): the one authored moment,
     * first run only. Input is never blocked: the buttons work mid-animation. With animations
     * off nothing starts, and the art simply shows the finished T.
     */
    private void playEntrance() {
        if (!Motion.enabled()) return;
        ImageView art = findViewById(R.id.onboardingArt);
        View card = findViewById(R.id.onboardingCard);
        float rise = 24 * getResources().getDisplayMetrics().density;

        Drawable drawing = art.getDrawable();
        if (drawing instanceof Animatable) ((Animatable) drawing).start();

        art.setAlpha(0f);
        art.setScaleX(0.96f);
        art.setScaleY(0.96f);
        art.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(360).setInterpolator(Motion.EASE_OUT).start();

        card.setAlpha(0f);
        card.setTranslationY(rise);
        card.animate().alpha(1f).translationY(0f)
                .setStartDelay(80).setDuration(360).setInterpolator(Motion.EASE_OUT).start();
    }

    private void finishOnboarding(boolean restore) {
        prefs(this).edit().putBoolean(KEY_SEEN, true).apply();
        Intent next = new Intent(this, NEXT).putExtra(EXTRA_RESTORE, restore);
        startActivity(next);
        finish();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
