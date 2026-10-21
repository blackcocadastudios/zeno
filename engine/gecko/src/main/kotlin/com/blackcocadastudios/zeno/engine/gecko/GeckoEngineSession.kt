package com.blackcocadastudios.zeno.engine.gecko

import com.blackcocadastudios.zeno.engine.BlockedTracker
import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.EngineSessionState
import com.blackcocadastudios.zeno.engine.FindResult
import com.blackcocadastudios.zeno.engine.HitResult
import com.blackcocadastudios.zeno.engine.HitType
import com.blackcocadastudios.zeno.engine.Interception
import com.blackcocadastudios.zeno.engine.LoadFlags
import com.blackcocadastudios.zeno.engine.LoadRequest
import com.blackcocadastudios.zeno.engine.SecurityInfo
import com.blackcocadastudios.zeno.engine.TrackerCategory
import com.blackcocadastudios.zeno.engine.TrackingProtection
import com.blackcocadastudios.zeno.engine.TranslationState
import com.blackcocadastudios.zeno.engine.WindowRequest
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

/** One tab's GeckoSession, translated into [EngineSession] events. */
class GeckoEngineSession internal constructor(
    private val engine: GeckoEngine,
    override val isPrivate: Boolean,
    open: Boolean,
) : EngineSession {

    val geckoSession: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .useTrackingProtection(engine.settings.trackingProtection != TrackingProtection.Off)
            .suspendMediaWhenInactive(false)
            .build(),
    )

    override var observer: EngineSession.Observer? = null

    private var closed = false
    private var lastFindText: String? = null

    internal val isClosed: Boolean get() = closed

    /** The view painting this session, released before the session closes. */
    internal var attachedView: GeckoEngineView? = null

    /** The page's vertical scroll position as Gecko last reported it; 0 at the top. */
    internal var scrollY = 0
        private set

    /**
     * The current site's tracking protection entry. GeckoView sends one with
     * every page (a placeholder when there is no exception), and changing its
     * value is the only way to add or remove an exception.
     */
    private var trackingPermission: GeckoSession.PermissionDelegate.ContentPermission? = null

    init {
        geckoSession.contentDelegate = ContentDelegate()
        geckoSession.progressDelegate = ProgressDelegate()
        geckoSession.navigationDelegate = NavigationDelegate()
        geckoSession.promptDelegate = GeckoPromptDelegate(engine.appContext) { observer }
        geckoSession.permissionDelegate = GeckoPermissionDelegate(engine, this)
        geckoSession.contentBlockingDelegate = BlockingDelegate()
        geckoSession.mediaSessionDelegate = MediaDelegate()
        geckoSession.translationsSessionDelegate = TranslationDelegate()
        geckoSession.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                this@GeckoEngineSession.scrollY = scrollY
                attachedView?.onPageScrolled(scrollY)
            }
        }
        if (!isPrivate) geckoSession.historyDelegate = HistoryDelegate()
        engine.onSessionCreated(this)
        if (open) geckoSession.open(engine.runtime)
        engine.extensions.onSessionCreated(this)
    }

    override fun load(url: String, flags: LoadFlags) {
        var geckoFlags = GeckoSession.LOAD_FLAGS_NONE
        if (flags.bypassCache) geckoFlags = geckoFlags or GeckoSession.LOAD_FLAGS_BYPASS_CACHE
        if (flags.external) geckoFlags = geckoFlags or GeckoSession.LOAD_FLAGS_EXTERNAL
        if (flags.replaceHistory) geckoFlags = geckoFlags or GeckoSession.LOAD_FLAGS_REPLACE_HISTORY
        if (flags.bypassInterceptor) geckoFlags = geckoFlags or GeckoSession.LOAD_FLAGS_BYPASS_LOAD_URI_DELEGATE
        geckoSession.load(GeckoSession.Loader().uri(url).flags(geckoFlags))
    }

    override fun reload(bypassCache: Boolean) =
        geckoSession.reload(if (bypassCache) GeckoSession.LOAD_FLAGS_BYPASS_CACHE else GeckoSession.LOAD_FLAGS_NONE)

    override fun stop() = geckoSession.stop()

    override fun goBack() = geckoSession.goBack(true)

    override fun goForward() = geckoSession.goForward(true)

    override fun restoreState(state: EngineSessionState) {
        GeckoSession.SessionState.fromString(state.serialized)?.let(geckoSession::restoreState)
    }

    override fun setDesktopMode(enabled: Boolean, reload: Boolean) {
        val settings = geckoSession.settings
        settings.userAgentMode =
            if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        settings.viewportMode =
            if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        if (reload) geckoSession.reload()
    }

    override fun find(text: String, backwards: Boolean) {
        val finder = geckoSession.finder
        finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL or
            GeckoSession.FINDER_DISPLAY_DIM_PAGE or
            GeckoSession.FINDER_DISPLAY_DRAW_LINK_OUTLINE
        val direction = if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else GeckoSession.FINDER_FIND_FORWARD
        // Passing null continues the previous search (next/previous match).
        val query = text.takeIf { it != lastFindText }
        lastFindText = text
        finder.find(query, direction).accept(
            { result ->
                if (result != null && !closed) {
                    observer?.onFindResult(FindResult(result.found, result.current, result.total, result.searchString))
                }
            },
            { },
        )
    }

    override fun clearFind() {
        lastFindText = null
        geckoSession.finder.clear()
    }

    override fun exitFullscreen() = geckoSession.exitFullScreen()

    override fun setActive(active: Boolean) {
        if (closed) return
        geckoSession.setActive(active)
        if (active) engine.extensions.setActiveSession(this)
    }

    override fun purgeHistory() = geckoSession.purgeHistory()

    override fun translate(fromLanguage: String, toLanguage: String) {
        val options = TranslationsController.SessionTranslation.TranslationOptions.Builder()
            .downloadModel(true)
            .build()
        geckoSession.sessionTranslation?.translate(fromLanguage, toLanguage, options)
    }

    override fun restoreOriginalLanguage() {
        geckoSession.sessionTranslation?.restoreOriginalPage()
    }

    override fun setProtectionException(excepted: Boolean) {
        val permission = trackingPermission ?: return
        engine.runtime.storageController.setPermission(
            permission,
            if (excepted) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW else GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
        )
        observer?.onProtectionExceptionChange(excepted)
        geckoSession.reload()
    }

    override fun close() {
        if (closed) return
        closed = true
        observer = null
        engine.oneTimeGrants.revoke(engine.runtime.storageController, this)
        attachedView?.detach()
        engine.onSessionClosed(this)
        engine.extensions.onSessionClosed(this)
        geckoSession.close()
    }

    internal fun setTrackingProtection(enabled: Boolean) {
        geckoSession.settings.useTrackingProtection = enabled
    }

    private inner class ContentDelegate : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            observer?.onTitleChange(title.orEmpty())
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            observer?.onFullscreenChange(fullScreen)
        }

        override fun onContextMenu(
            session: GeckoSession,
            screenX: Int,
            screenY: Int,
            element: GeckoSession.ContentDelegate.ContextElement,
        ) {
            val link = element.linkUri
            val type = when (element.type) {
                GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE -> if (link != null) HitType.ImageLink else HitType.Image
                GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO -> HitType.Video
                GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO -> HitType.Audio
                else -> if (link != null) HitType.Link else HitType.Other
            }
            if (type == HitType.Other) return
            observer?.onContextMenu(
                HitResult(
                    type = type,
                    linkUrl = link,
                    linkText = element.linkText,
                    mediaUrl = element.srcUri,
                    title = element.title,
                    altText = element.altText,
                ),
            )
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            val target = observer
            if (target == null) {
                runCatching { response.body?.close() }
                return
            }
            target.onDownload(response.toDownloadRequest(isPrivate))
        }

        override fun onCrash(session: GeckoSession) {
            observer?.onCrash()
        }

        override fun onKill(session: GeckoSession) {
            observer?.onKill()
        }

        override fun onFirstContentfulPaint(session: GeckoSession) {
            observer?.onFirstContentfulPaint()
        }

        override fun onCloseRequest(session: GeckoSession) {
            observer?.onWindowRequest(WindowRequest.Close)
        }

        override fun onFocusRequest(session: GeckoSession) {
            observer?.onFocusRequest()
        }
    }

    private inner class ProgressDelegate : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            // A new document starts at its top; a restored position is reported as a scroll.
            scrollY = 0
            observer?.onLoadingStateChange(true)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            observer?.onLoadingStateChange(false)
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            observer?.onProgress(progress)
        }

        override fun onSecurityChange(
            session: GeckoSession,
            securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
        ) {
            val loaded = GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED
            observer?.onSecurityChange(
                SecurityInfo(
                    secure = securityInfo.isSecure,
                    host = securityInfo.host,
                    issuer = securityInfo.certificate?.issuerX500Principal?.name?.let(::organization),
                    isException = securityInfo.isException,
                    mixedContent = securityInfo.mixedModeActive == loaded || securityInfo.mixedModePassive == loaded,
                ),
            )
        }

        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            observer?.onStateUpdated(EngineSessionState(sessionState.toString()))
        }
    }

    private inner class NavigationDelegate : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            if (url == null) return
            // Leaving a site takes back what it was allowed only this time.
            engine.oneTimeGrants.revoke(engine.runtime.storageController, this@GeckoEngineSession, keepOrigin = originOf(url))
            trackingPermission = perms.firstOrNull { it.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING }
            observer?.onLocationChange(url, hasUserGesture)
            observer?.onProtectionExceptionChange(
                trackingPermission?.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
            )
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            observer?.onNavigationStateChange(canGoBack = canGoBack)
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            observer?.onNavigationStateChange(canGoForward = canGoForward)
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? {
            val interceptor = engine.requestInterceptor ?: return null
            val decision = interceptor.onLoadRequest(
                this@GeckoEngineSession,
                LoadRequest(
                    url = request.uri,
                    triggerUrl = request.triggerUri,
                    isRedirect = request.isRedirect,
                    hasUserGesture = request.hasUserGesture,
                    isDirectNavigation = request.isDirectNavigation,
                ),
            )
            return if (decision == Interception.Handled) GeckoResult.deny() else null
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            val target = observer ?: return null
            val child = engine.createUnopenedSession(isPrivate)
            target.onWindowRequest(WindowRequest.Open(child, uri))
            return GeckoResult.fromValue(child.geckoSession)
        }

        override fun onLoadError(
            session: GeckoSession,
            uri: String?,
            error: WebRequestError,
        ): GeckoResult<String>? = GeckoResult.fromValue(ErrorPages.create(engine.appContext, uri, error))
    }

    private inner class BlockingDelegate : ContentBlocking.Delegate {
        override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
            val category = event.antiTrackingCategory
            if (category == ContentBlocking.AntiTracking.NONE) return
            observer?.onTrackerBlocked(BlockedTracker(event.uri, trackerCategory(category)))
        }
    }

    private inner class MediaDelegate : MediaSession.Delegate {
        override fun onFullscreen(
            session: GeckoSession,
            mediaSession: MediaSession,
            enabled: Boolean,
            meta: MediaSession.ElementMetadata?,
        ) {
            val landscape = meta?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width >= it.height }
            observer?.onMediaFullscreen(enabled, landscape)
        }
    }

    private inner class TranslationDelegate : TranslationsController.SessionTranslation.Delegate {
        override fun onTranslationStateChange(
            session: GeckoSession,
            translationState: TranslationsController.SessionTranslation.TranslationState?,
        ) {
            val state = translationState ?: return
            observer?.onTranslationState(
                TranslationState(
                    documentLanguage = state.detectedLanguages?.docLangTag,
                    userLanguage = state.detectedLanguages?.userLangTag,
                    isTranslated = state.requestedTranslationPair != null,
                    isEngineReady = state.isEngineReady == true,
                    error = state.error,
                ),
            )
        }
    }

    private inner class HistoryDelegate : GeckoSession.HistoryDelegate {
        override fun onVisited(
            session: GeckoSession,
            url: String,
            lastVisitedURL: String?,
            flags: Int,
        ): GeckoResult<Boolean>? {
            val tracker = engine.historyTracker ?: return null
            val topLevel = flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL != 0
            val failed = flags and GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR != 0
            val redirectSource = flags and (
                GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE or
                    GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT
                ) != 0
            if (!topLevel || failed || redirectSource) return GeckoResult.fromValue(false)
            val isRedirect = flags and (
                GeckoSession.HistoryDelegate.VISIT_REDIRECT_TEMPORARY or
                    GeckoSession.HistoryDelegate.VISIT_REDIRECT_PERMANENT
                ) != 0
            val result = GeckoResult<Boolean>()
            engine.scope.launch {
                result.complete(runCatching { tracker.onVisited(url, isRedirect, isReload = url == lastVisitedURL) }.getOrDefault(false))
            }
            return result
        }

        override fun getVisited(session: GeckoSession, urls: Array<String>): GeckoResult<BooleanArray>? {
            val tracker = engine.historyTracker ?: return null
            val result = GeckoResult<BooleanArray>()
            engine.scope.launch {
                val visited = runCatching { tracker.visited(urls.toList()) }.getOrNull()
                result.complete(visited?.toBooleanArray() ?: BooleanArray(urls.size))
            }
            return result
        }
    }

    private companion object {
        fun trackerCategory(category: Int) = when {
            category and ContentBlocking.AntiTracking.AD != 0 -> TrackerCategory.Ads
            category and ContentBlocking.AntiTracking.ANALYTIC != 0 -> TrackerCategory.Analytics
            category and (ContentBlocking.AntiTracking.SOCIAL or ContentBlocking.AntiTracking.STP) != 0 -> TrackerCategory.Social
            category and ContentBlocking.AntiTracking.CRYPTOMINING != 0 -> TrackerCategory.Cryptomining
            category and ContentBlocking.AntiTracking.FINGERPRINTING != 0 -> TrackerCategory.Fingerprinting
            category and ContentBlocking.AntiTracking.EMAIL != 0 -> TrackerCategory.Email
            category and ContentBlocking.AntiTracking.CONTENT != 0 -> TrackerCategory.Content
            else -> TrackerCategory.Other
        }

        private val organizationPart = Regex("""(?:^|,)\s*O=((?:\\,|[^,])+)""")

        /** The "O=" part of an X.500 name, e.g. "Let's Encrypt". */
        fun organization(distinguishedName: String): String? =
            organizationPart.find(distinguishedName)?.groupValues?.get(1)?.replace("\\,", ",")?.trim()
                ?: distinguishedName.takeIf { it.isNotBlank() }
    }
}
