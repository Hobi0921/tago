package com.passvault.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Previous passwords for an entry, so a botched rotation is recoverable.
 *
 * CASCADE on delete: removing an entry must take its history with it, otherwise
 * deleting a credential would leave the old passwords sitting in the database.
 */
@Entity(tableName = "history",
        indices = {@Index("entryId")},
        foreignKeys = @ForeignKey(
                entity = PasswordEntry.class,
                parentColumns = "id",
                childColumns = "entryId",
                onDelete = ForeignKey.CASCADE))
public class PasswordHistory {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "entryId")
    public long entryId;

    @ColumnInfo(name = "password")
    @NonNull
    public String password = "";

    @ColumnInfo(name = "replacedAt")
    public long replacedAt = System.currentTimeMillis();

    public PasswordHistory() { }

    @androidx.room.Ignore
    public PasswordHistory(long entryId, @NonNull String password) {
        this.entryId = entryId;
        this.password = password;
    }
}
