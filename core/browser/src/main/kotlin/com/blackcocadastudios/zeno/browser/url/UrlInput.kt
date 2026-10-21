package com.blackcocadastudios.zeno.browser.url

import com.blackcocadastudios.zeno.browser.search.SearchEngine
import java.net.URI

/** Turns what the user typed into something to load: a URL or a search. */
object UrlInput {

    private val webSchemes = setOf("http", "https", "about", "view-source", "data", "file", "moz-extension")

    /** Schemes handed to other apps by the request interceptor. */
    private val appSchemes = setOf("mailto", "tel", "sms", "smsto", "geo", "intent", "market", "magnet")

    /** Typed `javascript:` is never run from the address bar (self-XSS / paste-jacking). */
    private val blockedSchemes = setOf("javascript", "vbscript", "chrome", "resource", "jar")

    /** Endings that look like a domain but are almost always file names or code. */
    private val fileExtensions = setOf(
        "apk", "bat", "csv", "dll", "doc", "docx", "exe", "gif", "htm", "html", "java", "jpeg", "jpg",
        "js", "json", "kt", "mp3", "mp4", "pdf", "png", "rar", "sh", "svg", "txt", "xls", "xlsx", "zip",
    )

    private val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):(.*)$")
    private val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val portRegex = Regex("^\\d{1,5}$")

    /** Returns the URL to load, or null for blank input. */
    fun resolve(input: String, searchEngine: SearchEngine): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return toUrlOrNull(text) ?: searchEngine.searchUrl(text)
    }

    /** True when [input] would be loaded as an address rather than searched. */
    fun isUrl(input: String): Boolean = toUrlOrNull(input.trim()) != null

    private val linkInText = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /** Longest text searched as-is when another app shares or selects it. */
    private const val MAX_SHARED_QUERY = 500

    /**
     * What to open for text another app shared or selected ("Look at this:
     * https://…"): the first web link in it, or else the text itself, as typed
     * input. Other apps only ever get web pages opened: anything else that
     * looks like an address (file:, about:, data:…) is searched as text.
     */
    fun fromSharedText(text: String, searchEngine: SearchEngine): String? {
        linkInText.find(text)?.value?.let(::trimLinkEnd)?.let { link ->
            if (normalizeHttp(link) != null) return link
        }
        val query = text.trim().take(MAX_SHARED_QUERY)
        val resolved = resolve(query, searchEngine) ?: return null
        val web = resolved.startsWith("https://", ignoreCase = true) || resolved.startsWith("http://", ignoreCase = true)
        return if (web) resolved else searchEngine.searchUrl(query)
    }

    /** Drops punctuation that ends the sentence, not the link; keeps balanced brackets (Wikipedia). */
    private fun trimLinkEnd(link: String): String {
        var end = link.length
        while (end > 0) {
            val c = link[end - 1]
            val drop = when (c) {
                '.', ',', ';', ':', '!', '?', '\'', '"', '…' -> true
                ')' -> link.substring(0, end).count { it == ')' } > link.substring(0, end).count { it == '(' }
                ']' -> link.substring(0, end).count { it == ']' } > link.substring(0, end).count { it == '[' }
                else -> false
            }
            if (!drop) break
            end--
        }
        return link.substring(0, end)
    }

    private fun toUrlOrNull(text: String): String? {
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null

        val schemeMatch = schemeRegex.find(text)
        if (schemeMatch != null) {
            val scheme = schemeMatch.groupValues[1].lowercase()
            val rest = schemeMatch.groupValues[2]
            when {
                scheme in blockedSchemes -> return null
                scheme in webSchemes -> return if (scheme == "http" || scheme == "https") normalizeHttp(text) else text
                scheme in appSchemes -> return text
            }
            // "localhost:8080/x" or "example.com:8443": a host with a port, not a scheme.
            val port = rest.substringBefore('/').substringBefore('?').substringBefore('#')
            if (!portRegex.matches(port)) return null
        }

        val host = hostOf(text) ?: return null
        val scheme = if (isLocal(host)) "http" else "https"
        return when {
            isLocal(host) || isIpv4(host) || isIpv6Literal(host) -> "$scheme://$text"
            looksLikeDomain(host) -> "$scheme://$text"
            else -> null
        }
    }

    private fun normalizeHttp(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.host.isNullOrEmpty() && uri.rawAuthority.isNullOrEmpty()) null else url
    }.getOrNull()

    private fun hostOf(text: String): String? {
        val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty() || '@' in authority) return null
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            return if (end > 0) authority.substring(0, end + 1) else null
        }
        return authority.substringBefore(':').lowercase()
    }

    private fun isLocal(host: String) = host == "localhost" || host.endsWith(".localhost")

    private fun isIpv4(host: String): Boolean {
        val match = ipv4.matchEntire(host) ?: return false
        return match.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    private fun isIpv6Literal(host: String) = host.startsWith("[") && host.endsWith("]") && ':' in host

    private fun looksLikeDomain(host: String): Boolean {
        if (host.startsWith('.') || host.endsWith('.') || ".." in host) return false
        val labels = host.split('.')
        if (labels.size < 2) return false
        if (labels.any { label -> label.isEmpty() || label.startsWith('-') || label.endsWith('-') }) return false
        if (!host.all { it.isLetterOrDigit() || it == '-' || it == '.' }) return false
        val tld = labels.last()
        if (tld.startsWith("xn--")) return tld.length > 4
        if (tld.length < 2 || !tld.all { it.isLetter() }) return false
        return tld !in fileExtensions
    }
}
