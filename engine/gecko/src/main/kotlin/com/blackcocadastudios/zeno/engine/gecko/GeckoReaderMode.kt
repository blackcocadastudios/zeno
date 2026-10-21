package com.blackcocadastudios.zeno.engine.gecko

import android.net.Uri
import com.blackcocadastudios.zeno.engine.ReaderMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reader mode through a built-in extension (`assets/extensions/reader`): its
 * page downloads the article's page again and lays it out with Mozilla's
 * Readability. It has no background script and no content script, so pages
 * pay nothing for it and nothing runs until the user asks to read.
 */
internal class GeckoReaderMode(private val engine: GeckoEngine) : ReaderMode {
    /** `moz-extension://…/`, once Gecko has the extension; the same in every run. */
    private var baseUrl: String? = null

    /** One installation at a time (at start, and maybe a first reader request right after). */
    private val installing = Mutex()

    /** Installs the extension, or updates it after an app update; Gecko keeps it between runs. */
    fun start() {
        engine.scope.launch { ensure() }
    }

    private suspend fun ensure(): String? = installing.withLock {
        baseUrl?.let { return it }
        val controller = engine.runtime.webExtensionController
        var extension = runCatching { controller.ensureBuiltIn(LOCATION, ID).await() }.getOrNull() ?: return null
        if (!extension.metaData.allowedInPrivateBrowsing) {
            // In a private tab the reader runs, and downloads, in that tab's private context.
            extension = runCatching { controller.setAllowedInPrivateBrowsing(extension, true).await() }.getOrNull() ?: extension
        }
        extension.metaData.baseUrl.also { baseUrl = it }
    }

    override suspend fun readerUrl(url: String): String? {
        if (!isWeb(url)) return null
        val base = ensure() ?: return null
        return base.trimEnd('/') + "/" + PAGE + "?url=" + Uri.encode(url)
    }

    override fun originalUrl(url: String): String? {
        val base = baseUrl?.trimEnd('/') ?: return null
        if (!url.startsWith("$base/$PAGE")) return null
        return runCatching { Uri.parse(url).getQueryParameter("url") }.getOrNull()?.takeIf(::isWeb)
    }

    private fun isWeb(url: String) = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    companion object {
        const val ID = "reader@zeno.blackcocadastudios.com"
        private const val LOCATION = "resource://android/assets/extensions/reader/"
        private const val PAGE = "reader.html"
    }
}
