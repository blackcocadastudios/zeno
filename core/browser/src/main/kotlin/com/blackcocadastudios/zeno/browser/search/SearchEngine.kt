package com.blackcocadastudios.zeno.browser.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A search provider. [searchTemplate] and [suggestTemplate] contain `{searchTerms}`.
 * Suggestions use the OpenSearch JSON format: `["query", ["a", "b", …]]`.
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val searchTemplate: String,
    val suggestTemplate: String?,
    /** Query parameter holding the terms on results pages. */
    val queryParameter: String,
) {
    fun searchUrl(terms: String): String = searchTemplate.replace(PLACEHOLDER, encode(terms))

    fun suggestUrl(terms: String): String? = suggestTemplate?.replace(PLACEHOLDER, encode(terms))

    /** Search terms if [url] is one of this engine's results pages. */
    fun termsFrom(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val template = runCatching { URI(searchTemplate.replace(PLACEHOLDER, "x")) }.getOrNull() ?: return null
        if (!uri.host.equals(template.host, ignoreCase = true) || uri.path != template.path) return null
        val query = uri.rawQuery ?: return null
        return query.split('&')
            .map { it.substringBefore('=') to it.substringAfter('=', "") }
            .firstOrNull { it.first == queryParameter }
            ?.second
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val PLACEHOLDER = "{searchTerms}"

        // String charset overloads: the Charset ones need Android API 33.
        private fun encode(terms: String) = URLEncoder.encode(terms, "UTF-8")

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses an OpenSearch suggestions response; never throws. */
        fun parseSuggestions(body: String, limit: Int = 6): List<String> = runCatching {
            val root = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
            val list = root.getOrNull(1)?.jsonArray ?: return emptyList()
            list.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(limit)
        }.getOrDefault(emptyList())
    }
}

object SearchEngines {
    val DuckDuckGo = SearchEngine(
        id = "duckduckgo",
        name = "DuckDuckGo",
        searchTemplate = "https://duckduckgo.com/?q={searchTerms}",
        suggestTemplate = "https://duckduckgo.com/ac/?q={searchTerms}&type=list",
        queryParameter = "q",
    )
    val Google = SearchEngine(
        id = "google",
        name = "Google",
        searchTemplate = "https://www.google.com/search?q={searchTerms}",
        suggestTemplate = "https://suggestqueries.google.com/complete/search?client=firefox&q={searchTerms}",
        queryParameter = "q",
    )
    val Bing = SearchEngine(
        id = "bing",
        name = "Bing",
        searchTemplate = "https://www.bing.com/search?q={searchTerms}",
        suggestTemplate = "https://www.bing.com/osjson.aspx?query={searchTerms}",
        queryParameter = "q",
    )
    val Brave = SearchEngine(
        id = "brave",
        name = "Brave Search",
        searchTemplate = "https://search.brave.com/search?q={searchTerms}",
        suggestTemplate = "https://search.brave.com/api/suggest?q={searchTerms}",
        queryParameter = "q",
    )
    val Startpage = SearchEngine(
        id = "startpage",
        name = "Startpage",
        searchTemplate = "https://www.startpage.com/sp/search?query={searchTerms}",
        suggestTemplate = null,
        queryParameter = "query",
    )
    val Ecosia = SearchEngine(
        id = "ecosia",
        name = "Ecosia",
        searchTemplate = "https://www.ecosia.org/search?q={searchTerms}",
        suggestTemplate = "https://ac.ecosia.org/autocomplete?q={searchTerms}&type=list",
        queryParameter = "q",
    )
    val Wikipedia = SearchEngine(
        id = "wikipedia",
        name = "Wikipédia",
        searchTemplate = "https://pt.wikipedia.org/w/index.php?search={searchTerms}",
        suggestTemplate = "https://pt.wikipedia.org/w/api.php?action=opensearch&format=json&search={searchTerms}",
        queryParameter = "search",
    )

    val all: List<SearchEngine> = listOf(DuckDuckGo, Google, Bing, Brave, Startpage, Ecosia, Wikipedia)

    val default: SearchEngine = DuckDuckGo

    fun byId(id: String?): SearchEngine = all.firstOrNull { it.id == id } ?: default
}
