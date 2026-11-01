package com.passvault.security;

import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;

/**
 * Process-wide lock state. Holds the decrypted database passphrase in memory only
 * while the vault is open, and zeroes it on lock.
 *
 * Auto-lock starts counting when the app goes to the background and is cancelled
 * when it returns to the foreground.
 */
public final class VaultLock {

    public interface LockListener {
        void onVaultLocked();
    }

    private static final VaultLock INSTANCE = new VaultLock();
    public static VaultLock get() { return INSTANCE; }

    private static final long DEFAULT_TIMEOUT_MS = 60_000L; // 1 minute backgrounded

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable lockTask = this::lock;

    private byte[] passphrase;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;
    private LockListener listener;

    private VaultLock() { }

    public synchronized boolean isUnlocked() {
        return passphrase != null;
    }

    /** Called after a successful unwrap. Takes ownership of the array. */
    public synchronized void unlock(byte[] rawPassphrase) {
        this.passphrase = rawPassphrase;
        cancelTimer();
    }

    /**
     * SQLCipher wants the passphrase as a char[]. Hex-encode the raw bytes so every
     * byte survives the conversion — new String(bytes) would mangle anything that
     * is not valid UTF-8.
     */
    public synchronized char[] getPassphraseChars() {
        if (passphrase == null) return null;
        char[] out = new char[passphrase.length * 2];
        final char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < passphrase.length; i++) {
            int v = passphrase[i] & 0xFF;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0F];
        }
        return out;
    }

    public void lock() {
        synchronized (this) {
            if (passphrase != null) {
                Arrays.fill(passphrase, (byte) 0);
                passphrase = null;
            }
        }
        cancelTimer();
        // Notified outside the monitor. AppDatabase.get() takes its own lock and
        // then calls back into VaultLock, so holding this one here would invert
        // the lock order and can deadlock.
        LockListener l = listener;
        if (l != null) l.onVaultLocked();
    }

    public void setTimeoutMs(long ms) { this.timeoutMs = ms; }

    public void setListener(LockListener l) { this.listener = l; }

    /** Start counting down — call when the app is backgrounded. */
    public void startTimer() {
        cancelTimer();
        if (isUnlocked()) handler.postDelayed(lockTask, timeoutMs);
    }

    /** Stop counting down — call when the app returns to the foreground. */
    public void cancelTimer() {
        handler.removeCallbacks(lockTask);
    }
}
