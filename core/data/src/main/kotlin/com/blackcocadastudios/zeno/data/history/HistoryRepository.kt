package com.blackcocadastudios.zeno.data.history

import com.blackcocadastudios.zeno.data.db.HistoryDao
import com.blackcocadastudios.zeno.data.db.HistoryEntity
import com.blackcocadastudios.zeno.data.db.likePattern
import com.blackcocadastudios.zeno.engine.HistoryTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URI

data class HistoryEntry(
    val url: String,
    val title: String,
    val host: String,
    val visitCount: Int,
    val lastVisitedAt: Long,
)

/** Browsing history. Private tabs never reach it (the engine does not report their visits). */
class HistoryRepository(
    private val dao: HistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : HistoryTracker {

    override suspend fun onVisited(url: String, isRedirect: Boolean, isReload: Boolean): Boolean {
        if (!isRecordable(url)) return false
        if (!isReload) dao.recordVisit(url, hostOf(url), clock())
        return true
    }

    override suspend fun visited(urls: List<String>): List<Boolean> {
        if (urls.isEmpty()) return emptyList()
        // SQLite caps bound variables; stay well below the limit.
        val found = urls.chunked(500).flatMap { dao.existing(it) }.toHashSet()
        return urls.map { it in found }
    }

    suspend fun updateTitle(url: String, title: String) {
        if (title.isNotBlank() && isRecordable(url)) dao.updateTitle(url, title)
    }

    fun recent(limit: Int = 1000): Flow<List<HistoryEntry>> = dao.recent(limit).map { list -> list.map { it.toEntry() } }

    /** One entry per site, the most visited first: the home page's shortcuts. */
    fun topSites(limit: Int = 8): Flow<List<HistoryEntry>> = dao.mostVisited(limit * 8).map { rows ->
        rows.groupBy { it.host }
            .filterKeys { it.isNotEmpty() }
            .map { (_, visits) -> visits.maxBy { it.visitCount } to visits.sumOf { it.visitCount } }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.toEntry() }
    }

    /** Address bar matches: sites starting with what was typed come first. */
    suspend fun search(query: String, limit: Int = 6): List<HistoryEntry> {
        val text = query.trim().lowercase()
        if (text.isEmpty()) return emptyList()
        return dao.search(likePattern(text), limit * 4)
            .sortedByDescending { score(it, text) }
            .take(limit)
            .map { it.toEntry() }
    }

    suspend fun delete(url: String) = dao.delete(url)

    suspend fun deleteSite(host: String) = dao.deleteHost(host)

    suspend fun deleteSince(since: Long) = dao.deleteSince(since)

    suspend fun clear() = dao.clear()

    private fun score(entry: HistoryEntity, text: String): Double {
        val host = entry.host.removePrefix("www.")
        var score = entry.visitCount.toDouble()
        if (host.startsWith(text)) score += 1_000
        else if (entry.url.substringAfter("://").removePrefix("www.").startsWith(text)) score += 500
        if (entry.title.lowercase().startsWith(text)) score += 100
        // Recent visits rank higher: one extra point per day of freshness within a month.
        val ageDays = (clock() - entry.lastVisitedAt) / 86_400_000.0
        score += (30 - ageDays).coerceAtLeast(0.0)
        return score
    }

    companion object {
        private val recordableSchemes = setOf("http", "https")

        fun isRecordable(url: String): Boolean =
            runCatching { URI(url).scheme?.lowercase() in recordableSchemes }.getOrDefault(false)

        fun hostOf(url: String): String = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
    }
}

private fun HistoryEntity.toEntry() = HistoryEntry(url, title, host, visitCount, lastVisitedAt)
