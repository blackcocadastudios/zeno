package com.blackcocadastudios.zeno.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class, BookmarkEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ZenoDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao

    abstract fun bookmarks(): BookmarkDao

    abstract fun downloads(): DownloadDao

    companion object {
        /** Schema changes need a migration; there is no destructive fallback on purpose. */
        fun create(context: Context): ZenoDatabase =
            Room.databaseBuilder(context.applicationContext, ZenoDatabase::class.java, "zeno.db").build()
    }
}

/** Escapes LIKE wildcards so user input matches literally. */
internal fun likePattern(query: String): String {
    val escaped = buildString {
        for (c in query.trim()) {
            if (c == '%' || c == '_' || c == '\\') append('\\')
            append(c)
        }
    }
    return "%$escaped%"
}
