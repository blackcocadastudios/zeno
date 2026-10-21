package com.blackcocadastudios.zeno.engine.gecko

import java.net.URLDecoder

/** Reads the file name from a `Content-Disposition` header (RFC 6266), preferring `filename*`. */
internal object ContentDisposition {
    private val extended = Regex("""filename\*\s*=\s*([\w!#$&+.^`|~-]+)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
    private val quoted = Regex("""filename\s*=\s*"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE)
    private val plain = Regex("""filename\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE)

    fun fileName(header: String): String? {
        extended.find(header)?.let { match ->
            val charset = match.groupValues[1]
            val value = runCatching { URLDecoder.decode(match.groupValues[2].trim(), charset) }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
        quoted.find(header)?.let { return it.groupValues[1].replace(Regex("""\\(.)"""), "$1").ifBlank { null } }
        plain.find(header)?.let { return it.groupValues[1].trim('"').ifBlank { null } }
        return null
    }
}
