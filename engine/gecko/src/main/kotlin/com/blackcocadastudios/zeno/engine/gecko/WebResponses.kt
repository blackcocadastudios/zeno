package com.blackcocadastudios.zeno.engine.gecko

import com.blackcocadastudios.zeno.engine.DownloadRequest
import org.mozilla.geckoview.WebResponse

/** A response Gecko will not render, as a file to save. */
internal fun WebResponse.toDownloadRequest(private: Boolean): DownloadRequest {
    val lowerHeaders = headers.mapKeys { it.key.lowercase() }
    // Large files can pause between chunks; do not give up after the default 30 s.
    runCatching { setReadTimeoutMillis(5 * 60 * 1000L) }
    return DownloadRequest(
        url = uri,
        fileName = lowerHeaders["content-disposition"]?.let(ContentDisposition::fileName),
        contentType = lowerHeaders["content-type"]?.substringBefore(';')?.trim()?.ifEmpty { null },
        contentLength = lowerHeaders["content-length"]?.trim()?.toLongOrNull()?.takeIf { it >= 0 },
        isPrivate = private,
        body = body,
    )
}
