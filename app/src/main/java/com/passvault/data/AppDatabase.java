package com.passvault.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import com.passvault.security.VaultLock;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The vault database. Backed by SQLCipher, so the file on disk is AES-256
 * encrypted page-by-page. The passphrase comes from VaultLock, which only holds
 * it while the vault is unlocked — so the instance is torn down on lock.
 */
@Database(
        entities = {PasswordEntry.class, PasswordHistory.class},
        version = 2,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "passvault.db";

    public abstract PasswordDao passwordDao();

    public abstract HistoryDao historyDao();

    private static volatile AppDatabase instance;

    static {
        System.loadLibrary("sqlcipher");
    }

    /**
     * Returns the open database, building it if needed.
     * @throws IllegalStateException if the vault is locked.
     */
    public static AppDatabase get(Context context) {
        AppDatabase local = instance;
        if (local != null) return local;

        synchronized (AppDatabase.class) {
            if (instance != null) return instance;

            char[] passphrase = VaultLock.get().getPassphraseChars();
            if (passphrase == null) {
                throw new IllegalStateException("Vault is locked — cannot open database");
            }

            byte[] keyBytes = toBytes(passphrase);
            Arrays.fill(passphrase, '\0');

            SupportSQLiteOpenHelper.Factory factory = new SupportOpenHelperFactory(keyBytes);

            instance = Room.databaseBuilder(
                            context.getApplicationContext(), AppDatabase.class, DB_NAME)
                    .openHelperFactory(factory)
                    .addMigrations(Migrations.ALL)
                    // No fallbackToDestructiveMigration: if a migration is missing,
                    // crashing is correct. Silently wiping passwords is not.
                    .build();

            return instance;
        }
    }

    /**
     * Close and forget the singleton. Called whenever the vault locks.
     *
     * Deliberately NOT named close(): RoomDatabase already declares an instance
     * method close(), and a static method cannot hide an instance one.
     */
    public static synchronized void closeInstance() {
        AppDatabase local = instance;
        instance = null;
        if (local != null && local.isOpen()) {
            local.close();
        }
    }

    /**
     * Deletes the database files outright. Only used by the unlock screen's
     * recovery path: when the Keystore key is gone, the passphrase that decrypts
     * this file is gone with it, so the bytes on disk are permanently unreadable
     * and keeping them just blocks a fresh vault from being created.
     */
    public static synchronized void deleteDatabaseFiles(Context context) {
        closeInstance();
        Context app = context.getApplicationContext();
        app.deleteDatabase(DB_NAME);
        // -wal and -shm are removed by deleteDatabase, but a -journal left by an
        // older run is not always caught, so sweep the siblings too.
        java.io.File dir = app.getDatabasePath(DB_NAME).getParentFile();
        if (dir == null) return;
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File file : files) {
            if (file.getName().startsWith(DB_NAME)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static byte[] toBytes(char[] chars) {
        // Passphrase is hex, so ASCII-safe.
        return new String(chars).getBytes(StandardCharsets.UTF_8);
    }
}
