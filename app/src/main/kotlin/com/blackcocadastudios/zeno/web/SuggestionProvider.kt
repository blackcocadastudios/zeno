package com.blackcocadastudios.zeno.web

import com.blackcocadastudios.zeno.browser.search.SearchEngine
import com.blackcocadastudios.zeno.browser.url.UrlInput
import com.blackcocadastudios.zeno.data.bookmarks.BookmarksRepository
import com.blackcocadastudios.zeno.data.history.HistoryRepository
import com.blackcocadastudios.zeno.engine.Engine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One row under the address bar while typing. */
sealed interface Suggestion {
    /** Run the typed text as a search. */
    data class Search(val query: String, val engineName: String) : Suggestion

    /** The typed text is an address: go there. */
    data class Address(val url: String) : Suggestion

    data class Site(val url: String, val title: String, val bookmarked: Boolean) : Suggestion

    /** A term suggested by the search engine. */
    data class Term(val text: String) : Suggestion
}

data class Suggestions(
    val items: List<Suggestion> = emptyList(),
    /** Completion for the typed text, e.g. "github.com" for "git". */
    val completion: String? = null,
)

class SuggestionProvider(
    private val engine: Engine,
    private val history: HistoryRepository,
    private val bookmarks: BookmarksRepository,
) {
    suspend fun suggest(text: String, searchEngine: SearchEngine, remote: Boolean, private: Boolean): Suggestions = coroutineScope {
        val query = text.trim()
        if (query.isEmpty()) return@coroutineScope Suggestions()

        val remoteTerms = async {
            val url = searchEngine.suggestUrl(query)
            if (!remote || url == null) emptyList()
            else engine.fetchText(url, private)?.let { SearchEngine.parseSuggestions(it) }.orEmpty()
        }
        val sites = async { if (private) emptyList() else history.search(query, limit = 5) }
        val marks = async { if (private) emptyList() else bookmarks.search(query, limit = 3) }

        val items = mutableListOf<Suggestion>()
        val isUrl = UrlInput.isUrl(query)
        val resolved = UrlInput.resolve(query, searchEngine)
        if (isUrl && resolved != null) items += Suggestion.Address(resolved)
        items += Suggestion.Search(query, searchEngine.name)

        val bookmarkSites = marks.await().map { Suggestion.Site(it.url, it.title, bookmarked = true) }
        val historySites = sites.await()
            .filter { entry -> bookmarkSites.none { it.url == entry.url } }
            .map { Suggestion.Site(it.url, it.title.ifBlank { it.host }, bookmarked = false) }
        items += bookmarkSites + historySites
        items += remoteTerms.await()
            .filterNot { it.equals(query, ignoreCase = true) }
            .take(5)
            .map { Suggestion.Term(it) }

        val completion = completionFor(query, historySites.map { it.url } + bookmarkSites.map { it.url })
        Suggestions(items.distinct(), completion)
    }

    /** Inline completion: the first site whose host starts with what was typed. */
    private fun completionFor(query: String, urls: List<String>): String? {
        val typed = query.lowercase()
        if (typed.any { it.isWhitespace() } || typed.contains('/')) return null
        return urls.asSequence()
            .mapNotNull { runCatching { java.net.URI(it).host }.getOrNull()?.lowercase()?.removePrefix("www.") }
            .firstOrNull { it.startsWith(typed) && it.length > typed.length }
    }
}
