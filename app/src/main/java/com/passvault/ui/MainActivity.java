package com.passvault.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.passvault.R;
import com.passvault.security.VaultLock;

/**
 * Host for the four tabs.
 *
 * Tabs are created once and then hidden/shown. Replacing them on every switch
 * threw away the search query, scroll position, generator output and breach
 * results, and re-ran the audit each time Health was opened.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_VAULT = "tab_vault";
    private static final String TAG_HEALTH = "tab_health";
    private static final String TAG_TOOLS = "tab_tools";
    private static final String TAG_SETTINGS = "tab_settings";
    private static final String[] TAB_TAGS = {TAG_VAULT, TAG_HEALTH, TAG_TOOLS, TAG_SETTINGS};

    private BottomNavigationView bottomNav;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Blocks screenshots and keeps the vault out of the recents thumbnail.
        // Dialogs and sheets need their own flag: see SecureWindows.
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);

        if (!VaultLock.get().isUnlocked()) {
            bounceToUnlock();
            return;
        }

        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_lock) {
                lockNow();
                return true;
            }
            return false;
        });

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        // Re-tapping the current tab should not rebuild it.
        bottomNav.setOnItemReselectedListener(item -> { });

        if (savedInstanceState == null) {
            // Straight after a recovery the vault is empty and the only useful
            // next step is restoring a backup, so land on Settings instead.
            boolean restoring = getIntent()
                    .getBooleanExtra(UnlockActivity.EXTRA_OPEN_RESTORE, false);
            if (restoring) {
                bottomNav.setSelectedItemId(R.id.nav_settings);
            } else {
                // Called directly: with a reselect listener set, setSelectedItemId
                // on the already-checked first item goes to the reselect path.
                showTab(R.id.nav_vault);
            }
        }
        // Otherwise the FragmentManager restores the tabs with their hidden state.
    }

    /** After a recreate the nav restores its selection only now, after onCreate. */
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (bottomNav != null) updateTitle(bottomNav.getSelectedItemId());
    }

    /** Lets a tab send the user elsewhere, e.g. the empty vault's "Restore from backup". */
    public void selectTab(int itemId) {
        if (bottomNav != null) bottomNav.setSelectedItemId(itemId);
    }

    private void showTab(int itemId) {
        FragmentManager fm = getSupportFragmentManager();
        String tag = tagFor(itemId);

        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        for (String other : TAB_TAGS) {
            if (other.equals(tag)) continue;
            Fragment f = fm.findFragmentByTag(other);
            if (f != null && !f.isHidden()) tx.hide(f);
        }

        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            tx.add(R.id.content, fragmentFor(itemId), tag);
        } else {
            tx.show(target);
        }
        tx.commit();
        updateTitle(itemId);
    }

    /** The big title answers "where am I?"; it used to say Passwords on every tab. */
    private void updateTitle(int itemId) {
        if (toolbar != null) toolbar.setTitle(titleFor(itemId));
    }

    private static int titleFor(int itemId) {
        if (itemId == R.id.nav_health) return R.string.title_health;
        if (itemId == R.id.nav_tools) return R.string.title_tools;
        if (itemId == R.id.nav_settings) return R.string.title_settings;
        return R.string.title_vault;
    }

    private static String tagFor(int itemId) {
        if (itemId == R.id.nav_health) return TAG_HEALTH;
        if (itemId == R.id.nav_tools) return TAG_TOOLS;
        if (itemId == R.id.nav_settings) return TAG_SETTINGS;
        return TAG_VAULT;
    }

    private static Fragment fragmentFor(int itemId) {
        if (itemId == R.id.nav_health) return new HealthFragment();
        if (itemId == R.id.nav_tools) return new ToolsFragment();
        if (itemId == R.id.nav_settings) return new SettingsFragment();
        return new VaultFragment();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!VaultLock.get().isUnlocked()) bounceToUnlock();
    }

    /** Locks and leaves. Settings calls this too, so its button actually leaves the vault. */
    public void lockNow() {
        VaultLock.get().lock();
        bounceToUnlock();
    }

    private void bounceToUnlock() {
        Intent intent = new Intent(this, UnlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
