package com.passvault.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A single vault entry.
 *
 * Compared to the desktop version this drops toFileString()/fromFileString(): the
 * pipe delimiter broke on any password containing "|", and Room handles
 * serialisation properly. Strength is no longer stored — it is derived on demand,
 * so improving the scorer instantly re-rates every existing entry.
 */
@Entity(tableName = "entries", indices = {@Index("siteName"), @Index("packageName")})
public class PasswordEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "siteName")
    @NonNull
    public String siteName = "";

    @ColumnInfo(name = "username")
    @NonNull
    public String username = "";

    @ColumnInfo(name = "password")
    @NonNull
    public String password = "";

    /** Optional web domain, used to match autofill requests from browsers. */
    @ColumnInfo(name = "webDomain")
    @Nullable
    public String webDomain;

    /** Optional Android package name, used to match autofill requests from apps. */
    @ColumnInfo(name = "packageName")
    @Nullable
    public String packageName;

    @ColumnInfo(name = "notes")
    @Nullable
    public String notes;

    /** Base32 TOTP shared secret, or null if this entry has no 2FA code. */
    @ColumnInfo(name = "totpSecret")
    @Nullable
    public String totpSecret;

    @ColumnInfo(name = "favorite")
    public boolean favorite;

    /**
     * When the password itself last changed, as distinct from updatedAt, which
     * moves when you edit a label. Rotation age should not reset because someone
     * fixed a typo in the site name.
     */
    @ColumnInfo(name = "passwordUpdatedAt")
    public long passwordUpdatedAt = System.currentTimeMillis();

    @ColumnInfo(name = "createdAt")
    public long createdAt = System.currentTimeMillis();

    @ColumnInfo(name = "updatedAt")
    public long updatedAt = System.currentTimeMillis();

    public PasswordEntry() { }

    @androidx.room.Ignore
    public PasswordEntry(@NonNull String siteName, @NonNull String username,
                         @NonNull String password) {
        this.siteName = siteName;
        this.username = username;
        this.password = password;
    }

    /** Human-readable label for the entry, falling back to the username. */
    public String displayLabel() {
        return siteName.isEmpty() ? username : siteName;
    }

    @NonNull
    @Override
    public String toString() {
        return displayLabel() + " (" + username + ")";
    }
}
