package com.passvault.data;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Schema migrations.
 *
 * Without these, any schema change wipes the user's vault — Room's
 * fallbackToDestructiveMigration would drop and recreate every table. For a
 * password manager that is unacceptable, so this app has no destructive fallback
 * and every version bump gets a real migration.
 */
public final class Migrations {

    /** v1 -> v2: TOTP secrets, favourites, password-change timestamps, history table. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE entries ADD COLUMN totpSecret TEXT");
            db.execSQL("ALTER TABLE entries ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE entries ADD COLUMN passwordUpdatedAt INTEGER NOT NULL DEFAULT 0");

            // Existing rows have no separate password timestamp, so seed it from
            // updatedAt rather than leaving every entry looking brand new.
            db.execSQL("UPDATE entries SET passwordUpdatedAt = updatedAt");

            db.execSQL("CREATE TABLE IF NOT EXISTS history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "entryId INTEGER NOT NULL, "
                    + "password TEXT NOT NULL, "
                    + "replacedAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(entryId) REFERENCES entries(id) ON DELETE CASCADE)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_history_entryId ON history(entryId)");
        }
    };

    public static final Migration[] ALL = { MIGRATION_1_2 };

    private Migrations() { }
}
