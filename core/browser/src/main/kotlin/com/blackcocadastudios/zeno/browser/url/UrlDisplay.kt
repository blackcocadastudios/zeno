package com.blackcocadastudios.zeno.browser.url

import com.blackcocadastudios.zeno.browser.search.SearchEngine
import java.net.URI

/** How addresses are shown: quietly, without the noise of a full URL. */
object UrlDisplay {

    private val noisePrefixes = listOf("www.", "m.", "mobile.", "amp.")

    /** Two-part public suffixes common enough to matter for site names. */
    private val secondLevelSuffixes = setOf(
        "com.br", "net.br", "org.br", "gov.br", "edu.br", "art.br", "blog.br",
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "co.in", "com.mx", "com.ar", "com.pt",
        "co.nz", "com.cn", "com.tw", "co.kr", "com.sg", "co.za",
    )

    fun host(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    /** The host without `www.` — what the toolbar shows when not editing. */
    fun shortHost(url: String): String {
        val host = host(url) ?: return url.removePrefix("about:").ifEmpty { url }
        return noisePrefixes.fold(host) { acc, prefix -> acc.removePrefix(prefix) }
    }

    /** The site's own name, e.g. "wikipedia" for pt.wikipedia.org, "folha" for folha.uol.com.br. */
    fun siteName(url: String): String {
        val host = host(url) ?: return url
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return labels.firstOrNull() ?: host
        val lastTwo = labels.takeLast(2).joinToString(".")
        val nameIndex = if (lastTwo in secondLevelSuffixes && labels.size >= 3) labels.size - 3 else labels.size - 2
        return labels[nameIndex]
    }

    /** Letter for the site's seal. */
    fun sealLetter(url: String): String =
        siteName(url).firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·"

    /** If [url] is a results page of [engine], the terms searched; shown instead of the URL. */
    fun searchTerms(url: String, engine: SearchEngine): String? = engine.termsFrom(url)

    /** Text for the address bar while editing: the full URL, except for searches. */
    fun editableText(url: String, engine: SearchEngine): String = searchTerms(url, engine) ?: url
}
