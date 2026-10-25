package com.blackcocadastudios.zeno.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastVisitedAt DESC LIMIT :limit")
    abstract fun recent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY visitCount DESC, lastVisitedAt DESC LIMIT :limit")
    abstract fun mostVisited(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        """
        SELECT * FROM history
        WHERE url LIKE :pattern ESCAPE '\' OR title LIKE :pattern ESCAPE '\'
        ORDER BY visitCount DESC, lastVisitedAt DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun search(pattern: String, limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url = :url")
    abstract suspend fun get(url: String): HistoryEntity?

    @Query("SELECT url FROM history WHERE url IN (:urls)")
    abstract suspend fun existing(urls: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entry: HistoryEntity)

    @Query("UPDATE history SET title = :title WHERE url = :url")
    abstract suspend fun updateTitle(url: String, title: String)

    @Query("DELETE FROM history WHERE url = :url")
    abstract suspend fun delete(url: String)

    @Query("DELETE FROM history WHERE host = :host")
    abstract suspend fun deleteHost(host: String)

    @Query("DELETE FROM history WHERE lastVisitedAt >= :since")
    abstract suspend fun deleteSince(since: Long)

    @Query("DELETE FROM history")
    abstract suspend fun clear()

    @Transaction
    open suspend fun recordVisit(url: String, host: String, now: Long) {
        val existing = get(url)
        upsert(
            existing?.copy(visitCount = existing.visitCount + 1, lastVisitedAt = now)
                ?: HistoryEntity(url = url, title = "", host = host, visitCount = 1, firstVisitedAt = now, lastVisitedAt = now),
        )
    }
}

@Dao
abstract class BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    abstract fun all(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE pinned = 1 ORDER BY position ASC, createdAt ASC")
    abstract fun pinned(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url")
    abstract fun observe(url: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE url = :url")
    abstract suspend fun get(url: String): BookmarkEntity?

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE url LIKE :pattern ESCAPE '\' OR title LIKE :pattern ESCAPE '\'
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun search(pattern: String, limit: Int): List<BookmarkEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM bookmarks WHERE pinned = 1")
    abstract suspend fun nextPinnedPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    abstract suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    abstract suspend fun delete(url: String)
}

@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    abstract fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    abstract suspend fun get(id: Long): DownloadEntity?

    @Insert
    abstract suspend fun insert(download: DownloadEntity): Long

    @Query("UPDATE downloads SET downloadedBytes = :bytes, totalBytes = COALESCE(:total, totalBytes) WHERE id = :id")
    abstract suspend fun updateProgress(id: Long, bytes: Long, total: Long?)

    @Query("UPDATE downloads SET status = :status, contentUri = COALESCE(:contentUri, contentUri), completedAt = :completedAt WHERE id = :id")
    abstract suspend fun finish(id: Long, status: Int, contentUri: String?, completedAt: Long?)

    @Query("UPDATE downloads SET contentUri = :contentUri, fileName = :fileName WHERE id = :id")
    abstract suspend fun setTarget(id: Long, contentUri: String, fileName: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status != 0")
    abstract suspend fun clearFinished()

    /** Downloads cut off by a process death are failures, not forever "running". */
    @Query("UPDATE downloads SET status = 2 WHERE status = 0")
    abstract suspend fun failInterrupted()
}

@Dao
abstract class LoginDao {
    @Query("SELECT * FROM logins ORDER BY host ASC, createdAt ASC")
    abstract fun all(): Flow<List<LoginEntity>>

    @Query("SELECT * FROM logins")
    abstract suspend fun allNow(): List<LoginEntity>

    /** Logins whose host ends with [suffix] (a LIKE pattern); callers check the exact domain. */
    @Query("SELECT * FROM logins WHERE host LIKE :suffix ESCAPE '\\'")
    abstract suspend fun hostsEndingWith(suffix: String): List<LoginEntity>

    @Query("SELECT * FROM logins WHERE host = :host")
    abstract suspend fun forHost(host: String): List<LoginEntity>

    @Query("SELECT * FROM logins WHERE guid = :guid")
    abstract suspend fun get(guid: String): LoginEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(login: LoginEntity)

    @Query("UPDATE logins SET lastUsedAt = :at, timesUsed = timesUsed + 1 WHERE guid = :guid")
    abstract suspend fun markUsed(guid: String, at: Long)

    @Query("DELETE FROM logins WHERE guid = :guid")
    abstract suspend fun delete(guid: String)

    @Query("SELECT * FROM login_exceptions ORDER BY origin ASC")
    abstract fun exceptions(): Flow<List<LoginExceptionEntity>>

    @Query("SELECT COUNT(*) > 0 FROM login_exceptions WHERE origin = :origin")
    abstract suspend fun isException(origin: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun addException(exception: LoginExceptionEntity)

    @Query("DELETE FROM login_exceptions WHERE origin = :origin")
    abstract suspend fun removeException(origin: String)
}

