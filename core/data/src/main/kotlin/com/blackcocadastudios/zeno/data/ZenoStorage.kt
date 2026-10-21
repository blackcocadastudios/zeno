package com.blackcocadastudios.zeno.data

import android.content.Context
import com.blackcocadastudios.zeno.data.bookmarks.BookmarksRepository
import com.blackcocadastudios.zeno.data.db.ZenoDatabase
import com.blackcocadastudios.zeno.data.downloads.DownloadsRepository
import com.blackcocadastudios.zeno.data.history.HistoryRepository

/** Everything stored in Zeno's database, behind repositories. The database itself stays private. */
class ZenoStorage(context: Context) {
    private val database = ZenoDatabase.create(context)

    val history = HistoryRepository(database.history())
    val bookmarks = BookmarksRepository(database.bookmarks())
    val downloads = DownloadsRepository(database.downloads())
}
