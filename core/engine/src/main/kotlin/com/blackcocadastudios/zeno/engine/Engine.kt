package com.blackcocadastudios.zeno.engine

/**
 * Browser engine boundary. Everything above this interface is engine-agnostic;
 * only `:engine:gecko` knows GeckoView.
 *
 * All methods must be called on the main thread unless marked `suspend`.
 */
interface Engine {
    /**
     * Creates a session (one tab's renderer). Nothing loads until [EngineSession.load]
     * or [EngineSession.restoreState] is called.
     */
    fun createSession(private: Boolean): EngineSession

    /** Applies settings to every current and future session. */
    fun applySettings(settings: EngineSettings)

    /** Decides what happens with navigations (external apps, blocked schemes). */
    var requestInterceptor: RequestInterceptor?

    /** Records visits for history and answers "was this link visited?". */
    var historyTracker: HistoryTracker?

    val extensions: ExtensionController

    /** Permissions sites were given or refused, and where tracking protection is off. */
    val sitePermissions: SitePermissionStore

    /** Clears stored browsing data held by the engine (cookies, cache, site data…). */
    suspend fun clearData(data: Set<BrowsingData>)

    /** Clears data stored by one site (cookies, storage, permissions). */
    suspend fun clearSiteData(host: String)

    /** Opens a connection ahead of time, e.g. while the user is still typing. */
    fun speculativeConnect(url: String)

    /**
     * Requests [url] as a file (for "save image", "download link"), with the
     * cookies of the normal or private context. Null when the server refuses.
     */
    suspend fun download(url: String, private: Boolean): DownloadRequest?

    /**
     * Fetches a small text resource through the engine's network stack, so it
     * obeys the same proxy, DNS and privacy settings as pages. Returns null on failure.
     */
    suspend fun fetchText(url: String, private: Boolean, timeoutMillis: Long = 4_000): String?
}

/** Navigation policy supplied by the app. */
fun interface RequestInterceptor {
    /**
     * Called for every top-level navigation. Return [Interception.Allow] to let the
     * engine load it, or [Interception.Handled] when the app took care of it
     * (opened another app, blocked a scheme…).
     */
    fun onLoadRequest(session: EngineSession, request: LoadRequest): Interception
}

data class LoadRequest(
    val url: String,
    val triggerUrl: String?,
    val isRedirect: Boolean,
    val hasUserGesture: Boolean,
    val isDirectNavigation: Boolean,
)

enum class Interception { Allow, Handled }

/** Bridge between engine visits and the app's history storage. */
interface HistoryTracker {
    /** A top-level visit happened. Return true if it was recorded. */
    suspend fun onVisited(url: String, isRedirect: Boolean, isReload: Boolean): Boolean

    /** Which of [urls] were visited before (for link coloring). */
    suspend fun visited(urls: List<String>): List<Boolean>
}

enum class BrowsingData { Cookies, Cache, SiteStorage, SitePermissions, AuthSessions, All }
