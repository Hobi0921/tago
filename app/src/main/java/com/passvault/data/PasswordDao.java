package com.passvault.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PasswordDao {

    /** Favourites first, then alphabetical. */
    @Query("SELECT * FROM entries ORDER BY favorite DESC, siteName COLLATE NOCASE ASC")
    LiveData<List<PasswordEntry>> observeAll();

    /** Synchronous variant — the autofill service runs off the main thread. */
    @Query("SELECT * FROM entries ORDER BY siteName COLLATE NOCASE ASC")
    List<PasswordEntry> getAllBlocking();

    @Query("SELECT * FROM entries WHERE siteName LIKE '%' || :q || '%' "
            + "OR username LIKE '%' || :q || '%' "
            + "OR webDomain LIKE '%' || :q || '%' "
            + "ORDER BY favorite DESC, siteName COLLATE NOCASE ASC")
    LiveData<List<PasswordEntry>> search(String q);

    @Query("SELECT * FROM entries WHERE id = :id")
    PasswordEntry findById(long id);

    /** Emits null once the entry is deleted, which the detail sheet uses to close itself. */
    @Query("SELECT * FROM entries WHERE id = :id")
    LiveData<PasswordEntry> observeById(long id);

    /** Deliberately leaves updatedAt alone: starring is not an edit. */
    @Query("UPDATE entries SET favorite = :favorite WHERE id = :id")
    void setFavorite(long id, boolean favorite);

    @Insert
    long insert(PasswordEntry entry);

    @Update
    void update(PasswordEntry entry);

    @Delete
    void delete(PasswordEntry entry);

    @Query("DELETE FROM entries")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM entries")
    int count();
}
