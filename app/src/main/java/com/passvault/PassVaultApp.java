package com.passvault;

import android.app.Application;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.passvault.data.AppDatabase;
import com.passvault.data.AppPrefs;
import com.passvault.security.VaultLock;

/**
 * Wires auto-lock to the process lifecycle: the countdown starts when the last
 * activity goes to the background and is cancelled the moment one comes back.
 *
 * When the vault locks, the database instance is closed so the passphrase is not
 * left sitting inside an open SQLCipher handle.
 */
public class PassVaultApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        VaultLock.get().setListener(AppDatabase::closeInstance);
        // The timeout is a user setting now, so load it before anything can lock.
        VaultLock.get().setTimeoutMs(new AppPrefs(this).autoLockMs());

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@androidx.annotation.NonNull LifecycleOwner owner) {
                VaultLock.get().cancelTimer();
            }

            @Override
            public void onStop(@androidx.annotation.NonNull LifecycleOwner owner) {
                VaultLock.get().startTimer();
            }
        });
    }
}
