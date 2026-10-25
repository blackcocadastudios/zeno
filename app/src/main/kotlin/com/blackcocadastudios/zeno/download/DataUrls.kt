package com.blackcocadastudios.zeno.download

import android.util.Base64
import com.blackcocadastudios.zeno.engine.DownloadRequest
import java.io.ByteArrayInputStream
import java.net.URLDecoder

/** `data:` URLs (inline images) are decoded locally instead of fetched. */
object DataUrls {
    private const val MAX_BYTES = 32 * 1024 * 1024

    fun toDownload(url: String, private: Boolean): DownloadRequest? = runCatching {
        val header = url.substringAfter("data:").substringBefore(',')
        val payload = url.substringAfter(',', "")
        val base64 = header.endsWith(";base64")
        val mime = header.removeSuffix(";base64").substringBefore(';').ifEmpty { "text/plain" }
        val bytes = if (base64) Base64.decode(payload, Base64.DEFAULT) else URLDecoder.decode(payload, "UTF-8").toByteArray()
        require(bytes.size <= MAX_BYTES)
        DownloadRequest(
            url = "data:$mime",
            fileName = null,
            contentType = mime,
            contentLength = bytes.size.toLong(),
            isPrivate = private,
            body = ByteArrayInputStream(bytes),
        )
    }.getOrNull()
}
