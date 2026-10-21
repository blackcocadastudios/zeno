package com.blackcocadastudios.zeno.engine

/**
 * Reader mode: a page's article alone, in Zeno's typography. The reader is a
 * page of its own that shows the article of another page, so entering it is a
 * navigation and the back button leaves it.
 */
interface ReaderMode {
    /** The address that shows [url] in reader mode; null when it cannot be read that way. */
    suspend fun readerUrl(url: String): String?

    /** The page a reader-mode [url] shows, or null when [url] is not reader mode. */
    fun originalUrl(url: String): String?
}
