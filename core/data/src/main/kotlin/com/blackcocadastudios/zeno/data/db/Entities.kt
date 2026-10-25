package com.blackcocadastudios.zeno.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [Index("lastVisitedAt"), Index("host")],
)
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val host: String,
    val visitCount: Int,
    val firstVisitedAt: Long,
    val lastVisitedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true), Index("pinned")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
    /** Shown as a shortcut on the home page. */
    val pinned: Boolean = false,
    /** Order among pinned shortcuts. */
    val position: Int = 0,
)

@Entity(
    tableName = "downloads",
    indices = [Index("createdAt")],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val status: Int,
    /** content:// URI of the saved file, once it exists. */
    val contentUri: String?,
    val createdAt: Long,
    val completedAt: Long?,
)

/**
 * A saved login. Username and password are encrypted with a key kept in
 * Android's keystore ([com.blackcocadastudios.zeno.data.logins.SecretCipher]);
 * the site is plain, so logins can be found by site.
 */
@Entity(
    tableName = "logins",
    indices = [Index("host")],
)
data class LoginEntity(
    @PrimaryKey val guid: String,
    /** Scheme, host and port, e.g. `https://accounts.example.com`. */
    val origin: String,
    /** The origin's host, lowercase: logins are fetched by domain. */
    val host: String,
    val formActionOrigin: String?,
    val httpRealm: String?,
    val encryptedUsername: String,
    val encryptedPassword: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val passwordChangedAt: Long,
    val timesUsed: Int,
)

/** A site where Zeno never offers to save a login. */
@Entity(tableName = "login_exceptions")
data class LoginExceptionEntity(
    @PrimaryKey val origin: String,
    val createdAt: Long,
)

