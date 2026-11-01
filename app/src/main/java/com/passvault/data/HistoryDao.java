package com.passvault.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {

    @Query("SELECT * FROM history WHERE entryId = :entryId ORDER BY replacedAt DESC")
    LiveData<List<PasswordHistory>> observeFor(long entryId);

    @Query("SELECT * FROM history WHERE entryId = :entryId ORDER BY replacedAt DESC")
    List<PasswordHistory> getFor(long entryId);

    @Insert
    void insert(PasswordHistory history);

    /** Keep the last N per entry so the table cannot grow without bound. */
    @Query("DELETE FROM history WHERE entryId = :entryId AND id NOT IN "
            + "(SELECT id FROM history WHERE entryId = :entryId ORDER BY replacedAt DESC LIMIT :keep)")
    void trim(long entryId, int keep);

    @Query("DELETE FROM history")
    void deleteAll();
}
