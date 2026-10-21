package com.blackcocadastudios.zeno.engine

/**
 * One tab's renderer. Callbacks arrive on the main thread through [Observer].
 */
interface EngineSession {
    val isPrivate: Boolean

    /** Receives this session's events. One observer at a time (the tab that owns it). */
    var observer: Observer?

    fun load(url: String, flags: LoadFlags = LoadFlags())

    fun reload(bypassCache: Boolean = false)

    fun stop()

    fun goBack()

    fun goForward()

    /** Restores history and scroll position saved from [Observer.onStateUpdated]. */
    fun restoreState(state: EngineSessionState)

    /** Requests the desktop version of sites; [reload] applies it to the current page. */
    fun setDesktopMode(enabled: Boolean, reload: Boolean)

    /** Starts or continues a find-in-page search; results arrive via [Observer.onFindResult]. */
    fun find(text: String, backwards: Boolean = false)

    fun clearFind()

    fun exitFullscreen()

    /** Inactive sessions stop painting and throttle timers. */
    fun setActive(active: Boolean)

    /** Removes back/forward history, e.g. before reusing a session. */
    fun purgeHistory()

    /** Translates the page between two BCP-47 language tags. */
    fun translate(fromLanguage: String, toLanguage: String)

    fun restoreOriginalLanguage()

    /**
     * Turns tracking protection off ([excepted]) or back on for the site this
     * session shows, and reloads the page so it applies.
     */
    fun setProtectionException(excepted: Boolean)

    /** Releases the renderer. The session cannot be used afterwards. */
    fun close()

    /** Every callback has a default so observers implement only what they need. */
    interface Observer {
        fun onLocationChange(url: String, hasUserGesture: Boolean) {}
        fun onTitleChange(title: String) {}
        fun onProgress(progress: Int) {}
        fun onLoadingStateChange(loading: Boolean) {}
        fun onNavigationStateChange(canGoBack: Boolean? = null, canGoForward: Boolean? = null) {}
        fun onSecurityChange(security: SecurityInfo) {}
        fun onTrackerBlocked(tracker: BlockedTracker) {}

        /** Whether tracking protection is off for the site now shown; reported with each page. */
        fun onProtectionExceptionChange(excepted: Boolean) {}
        fun onFullscreenChange(enabled: Boolean) {}
        fun onMediaFullscreen(enabled: Boolean, landscape: Boolean?) {}
        fun onStateUpdated(state: EngineSessionState) {}
        fun onContextMenu(hit: HitResult) {}
        fun onDownload(download: DownloadRequest) {}
        fun onPrompt(prompt: PromptRequest) {}
        fun onPromptDismissed(prompt: PromptRequest) {}
        fun onPermissionRequest(request: PermissionRequest) {}
        fun onAppPermissionRequest(request: AppPermissionRequest) {}
        fun onWindowRequest(request: WindowRequest) {}
        fun onFindResult(result: FindResult) {}
        fun onTranslationState(state: TranslationState) {}
        fun onFirstContentfulPaint() {}
        fun onCrash() {}
        fun onKill() {}
    }
}

data class LoadFlags(
    val bypassCache: Boolean = false,
    /** Load triggered by another app (VIEW intent); no referrer, no history replacement. */
    val external: Boolean = false,
    val replaceHistory: Boolean = false,
    /** Skip the engine's own load interception for internal navigations. */
    val bypassInterceptor: Boolean = false,
)

/** Opaque snapshot of a session's history, produced and consumed by the engine. */
@JvmInline
value class EngineSessionState(val serialized: String)
