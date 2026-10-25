package com.blackcocadastudios.zeno.engine

data class SecurityInfo(
    val secure: Boolean,
    val host: String? = null,
    /** Certificate issuer organization, when the connection is secure. */
    val issuer: String? = null,
    /** The user added a certificate exception for this site. */
    val isException: Boolean = false,
    /** Insecure (http) content was loaded into a secure page. */
    val mixedContent: Boolean = false,
) {
    companion object {
        val Unknown = SecurityInfo(secure = false)
    }
}

data class BlockedTracker(val url: String, val category: TrackerCategory)

enum class TrackerCategory { Ads, Analytics, Social, Content, Cryptomining, Fingerprinting, Email, Other }

/** What is under the user's finger on a long press. */
data class HitResult(
    val type: HitType,
    val linkUrl: String? = null,
    val linkText: String? = null,
    val mediaUrl: String? = null,
    val title: String? = null,
    val altText: String? = null,
)

enum class HitType { Link, Image, Video, Audio, ImageLink, Other }

data class FindResult(
    val found: Boolean,
    val current: Int,
    val total: Int,
    val searchText: String,
)

/** Web content asks for a new window or to close itself. */
sealed interface WindowRequest {
    /** `window.open` / `target=_blank`: [session] is already created and must be shown in a new tab. */
    data class Open(val session: EngineSession, val url: String) : WindowRequest

    /** `window.close()` from a window the page opened. */
    data object Close : WindowRequest
}

data class TranslationState(
    val documentLanguage: String?,
    val userLanguage: String?,
    val isTranslated: Boolean,
    val isEngineReady: Boolean,
    val error: String? = null,
)
