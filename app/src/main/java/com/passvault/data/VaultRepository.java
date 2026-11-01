package com.passvault.data;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single entry point for vault data. Keeps database work off the main thread and
 * fails soft when the vault is locked rather than crashing the UI.
 */
public class VaultRepository {

    public interface Callback<T> {
        void onResult(T result);
    }

    private static VaultRepository instance;

    private final Context appContext;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** Previous passwords kept per entry. */
    private static final int HISTORY_KEEP = 5;

    private VaultRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized VaultRepository get(Context context) {
        if (instance == null) instance = new VaultRepository(context);
        return instance;
    }

    private PasswordDao dao() {
        return AppDatabase.get(appContext).passwordDao();
    }

    // ------------------------------------------------------------ observation

    public LiveData<List<PasswordEntry>> observeAll() {
        return dao().observeAll();
    }

    public LiveData<List<PasswordEntry>> search(String query) {
        return dao().search(query);
    }

    public LiveData<PasswordEntry> observeEntry(long id) {
        return dao().observeById(id);
    }

    public LiveData<List<PasswordHistory>> observeHistory(long entryId) {
        return AppDatabase.get(appContext).historyDao().observeFor(entryId);
    }

    // ------------------------------------------------------------- mutation

    public void insert(PasswordEntry entry, Runnable onDone) {
        io.execute(() -> {
            entry.createdAt = entry.updatedAt = System.currentTimeMillis();
            dao().insert(entry);
            if (onDone != null) onDone.run();
        });
    }

    /**
     * Updates an entry, recording the old password to history first if it changed.
     * passwordUpdatedAt only moves when the password itself moves, so editing a
     * label does not make a stale credential look freshly rotated.
     *
     * Room's @Update rewrites every column, so {@code entry} must be a complete
     * row. UI code should call {@link #applyEdit} instead, which loads the row
     * first and only touches the fields the editor shows.
     */
    public void update(PasswordEntry entry, Runnable onDone) {
        io.execute(() -> {
            updateBlocking(entry);
            if (onDone != null) onDone.run();
        });
    }

    private void updateBlocking(PasswordEntry entry) {
        PasswordEntry existing = dao().findById(entry.id);

        boolean passwordChanged =
                existing != null && !existing.password.equals(entry.password);

        if (passwordChanged) {
            AppDatabase.get(appContext).historyDao()
                    .insert(new PasswordHistory(entry.id, existing.password));
            AppDatabase.get(appContext).historyDao().trim(entry.id, HISTORY_KEEP);
            entry.passwordUpdatedAt = System.currentTimeMillis();
        } else if (existing != null) {
            entry.passwordUpdatedAt = existing.passwordUpdatedAt;
        }

        entry.updatedAt = System.currentTimeMillis();
        dao().update(entry);
    }

    /** The fields the entry editor owns. Everything else on the row is preserved. */
    public static final class Edit {
        public String siteName = "";
        public String username = "";
        public String password = "";
        public String webDomain;   // null clears it
        public String totpSecret;  // null clears it
        public String notes;       // null clears it
    }

    /**
     * Load-modify-save, so fields the editor does not show (packageName from an
     * autofill save, favorite, createdAt) survive an edit. Building a fresh
     * PasswordEntry and passing it to update() silently wiped them.
     */
    public void applyEdit(long id, Edit edit, Runnable onDone) {
        io.execute(() -> {
            try {
                PasswordEntry entry = dao().findById(id);
                if (entry == null) return;
                entry.siteName = edit.siteName;
                entry.username = edit.username;
                entry.password = edit.password;
                entry.webDomain = edit.webDomain;
                entry.totpSecret = edit.totpSecret;
                entry.notes = edit.notes;
                updateBlocking(entry);
                if (onDone != null) onDone.run();
            } catch (IllegalStateException locked) {
                // Vault locked mid-edit; nothing to write to.
            }
        });
    }

    public void insertEdit(Edit edit, Runnable onDone) {
        PasswordEntry entry = new PasswordEntry();
        entry.siteName = edit.siteName;
        entry.username = edit.username;
        entry.password = edit.password;
        entry.webDomain = edit.webDomain;
        entry.totpSecret = edit.totpSecret;
        entry.notes = edit.notes;
        insert(entry, onDone);
    }

    public void setFavorite(long id, boolean favorite) {
        io.execute(() -> {
            try {
                dao().setFavorite(id, favorite);
            } catch (IllegalStateException ignored) { }
        });
    }

    /** Bulk insert for restore. Returns the number written. */
    public void importAll(List<PasswordEntry> entries, Callback<Integer> onDone) {
        io.execute(() -> {
            int written = 0;
            for (PasswordEntry entry : entries) {
                entry.id = 0;               // let Room assign fresh ids
                dao().insert(entry);
                written++;
            }
            if (onDone != null) onDone.onResult(written);
        });
    }

    /** One-shot load for the editor. Delivers null if the entry is gone or the vault locked. */
    public void getEntry(long id, Callback<PasswordEntry> onLoaded) {
        io.execute(() -> {
            PasswordEntry entry;
            try {
                entry = dao().findById(id);
            } catch (IllegalStateException locked) {
                entry = null;
            }
            onLoaded.onResult(entry);
        });
    }

    public void runOnIo(Runnable task) {
        io.execute(task);
    }

    public List<PasswordHistory> historyFor(long entryId) {
        try {
            return AppDatabase.get(appContext).historyDao().getFor(entryId);
        } catch (IllegalStateException locked) {
            return Collections.emptyList();
        }
    }

    public void delete(PasswordEntry entry, Runnable onDone) {
        io.execute(() -> {
            dao().delete(entry);
            if (onDone != null) onDone.run();
        });
    }

    /** Everything needed to put a deleted entry back exactly as it was. */
    public static final class DeletedEntry {
        public final PasswordEntry entry;
        final List<PasswordHistory> history;

        DeletedEntry(PasswordEntry entry, List<PasswordHistory> history) {
            this.entry = entry;
            this.history = history;
        }
    }

    /**
     * Deletes immediately and hands back a snapshot for Undo. Deleting straight
     * away (rather than waiting for the snackbar to time out) means the entry
     * cannot linger if the fragment goes away mid-snackbar. History is captured
     * first because the foreign key cascades it away with the entry.
     */
    public void deleteWithUndo(long id, Callback<DeletedEntry> onDeleted) {
        io.execute(() -> {
            try {
                PasswordEntry entry = dao().findById(id);
                if (entry == null) return;
                List<PasswordHistory> history =
                        AppDatabase.get(appContext).historyDao().getFor(id);
                dao().delete(entry);
                if (onDeleted != null) onDeleted.onResult(new DeletedEntry(entry, history));
            } catch (IllegalStateException ignored) { }
        });
    }

    /** Re-inserts with the original ids, so history rows reattach to the entry. */
    public void restore(DeletedEntry deleted) {
        io.execute(() -> {
            try {
                dao().insert(deleted.entry);
                HistoryDao historyDao = AppDatabase.get(appContext).historyDao();
                for (PasswordHistory h : deleted.history) historyDao.insert(h);
            } catch (IllegalStateException ignored) {
                // Locked before Undo was tapped; the entry stays deleted.
            }
        });
    }

    // -------------------------------------------------- synchronous (services)

    // Everything below must run off the main thread (runOnIo): Room refuses main-thread
    // queries, and the IllegalStateException it throws looks exactly like "locked". The
    // autofill service's callbacks arrive on the main thread, which is why it used to get an
    // empty vault on every request.

    /** Returns empty if the vault is locked. */
    public List<PasswordEntry> getAllBlocking() {
        try {
            return dao().getAllBlocking();
        } catch (IllegalStateException locked) {
            return Collections.emptyList();
        }
    }

    /** @throws IllegalStateException if the vault is locked. */
    public long insertBlocking(PasswordEntry entry) {
        entry.createdAt = entry.updatedAt = entry.passwordUpdatedAt = System.currentTimeMillis();
        return dao().insert(entry);
    }

    /**
     * A password changed in another app. Goes through the normal update path, so the old
     * password lands in history. Fills in the app or site the change came from if the entry
     * has none, so the next fill there finds it straight away.
     *
     * @throws IllegalStateException if the vault is locked.
     */
    public void changePasswordBlocking(long id, String newPassword,
                                       @Nullable String packageName, @Nullable String webDomain) {
        PasswordEntry entry = dao().findById(id);
        if (entry == null) return;
        entry.password = newPassword;
        if (entry.packageName == null && packageName != null) entry.packageName = packageName;
        if (entry.webDomain == null && webDomain != null) entry.webDomain = webDomain;
        updateBlocking(entry);
    }

    /**
     * Remembers that this entry is used in this app, after the user picked it from the full
     * list there. Never overwrites an existing link.
     *
     * @throws IllegalStateException if the vault is locked.
     */
    public void linkPackageBlocking(long id, String packageName) {
        PasswordEntry entry = dao().findById(id);
        if (entry == null || entry.packageName != null) return;
        entry.packageName = packageName;
        entry.updatedAt = System.currentTimeMillis();
        dao().update(entry);
    }
}
