package com.blackcocadastudios.zeno.data.bookmarks

import com.blackcocadastudios.zeno.data.db.BookmarkDao
import com.blackcocadastudios.zeno.data.db.BookmarkEntity
import com.blackcocadastudios.zeno.data.db.likePattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Bookmark(
    val id: Long,
    val url: String,
    val title: String,
    val createdAt: Long,
    val pinned: Boolean,
)

class BookmarksRepository(
    private val dao: BookmarkDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val all: Flow<List<Bookmark>> = dao.all().map { list -> list.map { it.toBookmark() } }

    /** Shortcuts on the home page, in the user's order. */
    val pinned: Flow<List<Bookmark>> = dao.pinned().map { list -> list.map { it.toBookmark() } }

    fun isBookmarked(url: String): Flow<Boolean> = dao.observe(url).map { it != null }

    suspend fun add(url: String, title: String, pinned: Boolean = false) {
        val existing = dao.get(url)
        if (existing != null) {
            if (pinned && !existing.pinned) dao.update(existing.copy(pinned = true, position = dao.nextPinnedPosition()))
            return
        }
        val position = if (pinned) dao.nextPinnedPosition() else 0
        dao.insert(BookmarkEntity(url = url, title = title.ifBlank { url }, createdAt = clock(), pinned = pinned, position = position))
    }

    suspend fun remove(url: String) = dao.delete(url)

    suspend fun rename(url: String, title: String) {
        val existing = dao.get(url) ?: return
        dao.update(existing.copy(title = title.ifBlank { existing.title }))
    }

    suspend fun setPinned(url: String, pinned: Boolean) {
        val existing = dao.get(url) ?: return
        if (existing.pinned == pinned) return
        dao.update(existing.copy(pinned = pinned, position = if (pinned) dao.nextPinnedPosition() else 0))
    }

    suspend fun search(query: String, limit: Int = 4): List<Bookmark> {
        if (query.isBlank()) return emptyList()
        return dao.search(likePattern(query.lowercase()), limit).map { it.toBookmark() }
    }
}

private fun BookmarkEntity.toBookmark() = Bookmark(id, url, title, createdAt, pinned)
