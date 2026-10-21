package com.blackcocadastudios.zeno.download

import android.webkit.MimeTypeMap
import java.net.URI
import java.net.URLDecoder

/** Safe, readable file names for downloads. */
object FileNames {
    private const val MAX_LENGTH = 120
    private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001f\u007f]""")

    fun resolve(suggested: String?, url: String, mimeType: String?): String {
        val raw = suggested?.takeIf { it.isNotBlank() } ?: nameFromUrl(url) ?: "download"
        var name = sanitize(raw).ifEmpty { "download" }
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = mimeType?.lowercase()?.takeIf { it != "application/octet-stream" }
        if (extension.isEmpty() && mime != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { name = "$name.$it" }
        }
        return name
    }

    fun mimeTypeFor(fileName: String, fallback: String?): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: "application/octet-stream"
    }

    private fun nameFromUrl(url: String): String? = runCatching {
        val path = URI(url).rawPath ?: return null
        URLDecoder.decode(path.substringAfterLast('/'), "UTF-8").takeIf { it.isNotBlank() }
    }.getOrNull()

    internal fun sanitize(name: String): String {
        val cleaned = name.replace(forbidden, "_").trim().trim('.').trim()
        if (cleaned.length <= MAX_LENGTH) return cleaned
        val extension = cleaned.substringAfterLast('.', "")
        val keep = if (extension.length in 1..10) MAX_LENGTH - extension.length - 1 else MAX_LENGTH
        return if (extension.length in 1..10) cleaned.take(keep) + "." + extension else cleaned.take(keep)
    }
}
