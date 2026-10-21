package com.blackcocadastudios.zeno.engine

import java.io.InputStream

/**
 * A response the engine will not render (a file). [body] streams the bytes the
 * engine already received with the page's cookies; the consumer must read or close it.
 */
class DownloadRequest(
    val url: String,
    val fileName: String?,
    val contentType: String?,
    val contentLength: Long?,
    val isPrivate: Boolean,
    val body: InputStream?,
)
