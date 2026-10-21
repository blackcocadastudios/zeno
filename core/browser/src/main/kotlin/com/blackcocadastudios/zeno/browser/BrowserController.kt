package com.blackcocadastudios.zeno.browser

import com.blackcocadastudios.zeno.browser.session.SessionSnapshot
import com.blackcocadastudios.zeno.browser.state.BrowserState
import com.blackcocadastudios.zeno.browser.state.ContentState
import com.blackcocadastudios.zeno.browser.state.TabOrigin
import com.blackcocadastudios.zeno.browser.state.TabState
import com.blackcocadastudios.zeno.engine.AppPermissionRequest
import com.blackcocadastudios.zeno.engine.BlockedTracker
import com.blackcocadastudios.zeno.engine.DownloadRequest
import com.blackcocadastudios.zeno.engine.Engine
import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.EngineSessionState
import com.blackcocadastudios.zeno.engine.FindResult
import com.blackcocadastudios.zeno.engine.HitResult
import com.blackcocadastudios.zeno.engine.LoadFlags
import com.blackcocadastudios.zeno.engine.PermissionRequest
import com.blackcocadastudios.zeno.engine.PromptRequest
import com.blackcocadastudios.zeno.engine.SecurityInfo
import com.blackcocadastudios.zeno.engine.TranslationState
import com.blackcocadastudios.zeno.engine.WindowRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Owns the tabs: their state and their engine sessions. The interface reads
 * [state] and calls these methods; engine events flow back into [state].
 *
 * Renderers are created lazily: a restored or suspended tab has no session until
 * it is selected, which keeps startup fast and memory low.
 *
 * Not thread-safe by design: call from the main thread, like the engine.
 */
class BrowserController(
    private val engine: Engine,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _downloads = MutableSharedFlow<DownloadRequest>(extraBufferCapacity = 8)

    /** Files the engine handed over; the app's download manager collects these. */
    val downloads: SharedFlow<DownloadRequest> = _downloads.asSharedFlow()

    private val sessions = HashMap<String, EngineSession>()

    /** Default for new tabs; follows the user's setting. */
    var desktopModeByDefault: Boolean = false

    // region Tabs

    /**
     * Opens a tab. With a null [url] the tab shows Zeno's home page and has no
     * renderer until something is loaded into it.
     */
    fun newTab(
        url: String? = null,
        private: Boolean = state.value.privateMode,
        select: Boolean = true,
        parentId: String? = null,
        origin: TabOrigin = TabOrigin.User,
        flags: LoadFlags = LoadFlags(),
    ): String {
        val tab = insertTab(url, private, select, parentId, origin)
        if (url != null) {
            createSession(tab).load(url, flags)
        }
        if (select) onSelected(tab.id, previous = null)
        return tab.id
    }

    /** Makes [id] the visible tab, waking it if it was suspended. */
    fun selectTab(id: String) {
        val current = state.value
        val tab = current.tab(id) ?: return
        val previous = current.selectedTabId
        _state.update {
            it.copy(
                selectedTabId = id,
                privateMode = tab.isPrivate,
                tabs = it.tabs.map { t -> if (t.id == id) t.copy(lastAccessedAt = clock()) else t },
            )
        }
        onSelected(id, previous.takeIf { it != id })
    }

    fun closeTab(id: String) {
        val before = state.value
        val tab = before.tab(id) ?: return
        releaseSession(id, tab)
        _state.update { current ->
            val remaining = current.tabs.filterNot { it.id == id }
            val nextSelected = when {
                current.selectedTabId != id -> current.selectedTabId
                else -> nextSelection(tab, remaining)
            }
            current.copy(
                tabs = remaining,
                selectedTabId = nextSelected,
                privateMode = remaining.firstOrNull { it.id == nextSelected }?.isPrivate ?: current.privateMode,
            )
        }
        val selected = state.value.selectedTabId
        if (before.selectedTabId == id && selected != null) onSelected(selected, previous = null)
    }

    /**
     * Closes every private (or every normal) tab. Private data is discarded
     * with them, and closing the private tabs leaves private mode.
     */
    fun closeAllTabs(private: Boolean) {
        val doomed = state.value.tabs.filter { it.isPrivate == private }
        doomed.forEach { releaseSession(it.id, it) }
        val doomedIds = doomed.map { it.id }.toSet()
        _state.update { current ->
            val remaining = current.tabs.filterNot { it.id in doomedIds }
            current.copy(
                tabs = remaining,
                selectedTabId = current.selectedTabId?.takeUnless { it in doomedIds },
                privateMode = current.privateMode && !private,
            )
        }
    }

    /** Opens a copy of [id], with its history. */
    fun duplicateTab(id: String): String? {
        val source = state.value.tab(id) ?: return null
        val copy = insertTab(source.url, source.isPrivate, select = true, parentId = id, origin = TabOrigin.User)
        _state.update { s -> s.copy(tabs = s.tabs.map { if (it.id == copy.id) it.copy(engineState = source.engineState, title = source.title) else it }) }
        sessionFor(copy.id)
        onSelected(copy.id, previous = id)
        return copy.id
    }

    /**
     * Brings back a tab closed a moment ago ("undo"), at [index], with its
     * history. Its renderer is created when it is selected.
     */
    fun reopen(closed: TabState, index: Int, select: Boolean): String {
        val now = clock()
        val tab = closed.copy(
            id = newId(),
            parentId = closed.parentId?.takeIf { state.value.tab(it) != null },
            lastAccessedAt = now,
            isLive = false,
            content = ContentState(),
        )
        val previous = state.value.selectedTabId
        _state.update { current ->
            val tabs = current.tabs.toMutableList()
            tabs.add(index.coerceIn(0, tabs.size), tab)
            current.copy(
                tabs = tabs,
                selectedTabId = if (select) tab.id else current.selectedTabId,
                privateMode = if (select) tab.isPrivate else current.privateMode,
            )
        }
        if (select) onSelected(tab.id, previous = previous)
        return tab.id
    }

    /**
     * Shows a renderer the engine created by itself (an extension opening its
     * dashboard, for instance) in a new tab.
     */
    fun adoptSession(
        session: EngineSession,
        url: String,
        private: Boolean,
        origin: TabOrigin = TabOrigin.Extension,
        select: Boolean = true,
    ): String {
        val tab = insertTab(url, private, select, parentId = null, origin = origin)
        bind(tab.id, session)
        if (select) onSelected(tab.id, previous = null)
        return tab.id
    }

    /** The tab rendered by [session], if any. */
    fun tabIdOf(session: EngineSession): String? = sessions.entries.firstOrNull { it.value === session }?.key

    /** Switches the home page between normal and private mode without selecting a tab. */
    fun showHome(private: Boolean) {
        val previous = state.value.selectedTabId
        _state.update { it.copy(selectedTabId = null, privateMode = private) }
        previous?.let { sessions[it]?.setActive(false) }
    }

    // endregion

    // region Navigation

    /** Loads [url] in [tabId], or in a new tab when there is none. */
    fun load(url: String, tabId: String? = state.value.selectedTabId, flags: LoadFlags = LoadFlags()) {
        val tab = tabId?.let { state.value.tab(it) }
        if (tab == null) {
            newTab(url, flags = flags)
            return
        }
        updateTab(tab.id) { it.copy(url = url, content = it.content.copy(contextMenu = null, find = null)) }
        val session = sessions[tab.id] ?: wake(tab)
        session.load(url, flags)
    }

    fun reload(tabId: String, bypassCache: Boolean = false) {
        val tab = state.value.tab(tabId) ?: return
        if (tab.content.crashed || !tab.isLive) {
            updateTab(tabId) { it.copy(content = it.content.copy(crashed = false)) }
            sessionFor(tabId)
            return
        }
        sessions[tabId]?.reload(bypassCache)
    }

    fun stop(tabId: String) = sessions[tabId]?.stop() ?: Unit

    fun goBack(tabId: String) = sessions[tabId]?.goBack() ?: Unit

    fun goForward(tabId: String) = sessions[tabId]?.goForward() ?: Unit

    /**
     * What the system back gesture does on the browser screen, in order:
     * leave fullscreen, go back in history, return to the tab that opened this
     * one, return to the app that opened this one.
     */
    fun handleBack(): BackResult {
        val tab = state.value.selectedTab ?: return BackResult.NotHandled
        val session = sessions[tab.id]
        return when {
            tab.content.fullscreen && session != null -> {
                session.exitFullscreen()
                BackResult.Handled
            }
            tab.content.canGoBack && session != null -> {
                session.goBack()
                BackResult.Handled
            }
            tab.parentId != null && state.value.tab(tab.parentId) != null -> {
                val parent = tab.parentId
                closeTab(tab.id)
                selectTab(parent)
                BackResult.Handled
            }
            tab.origin == TabOrigin.External -> {
                closeTab(tab.id)
                BackResult.LeaveApp
            }
            else -> BackResult.NotHandled
        }
    }

    fun setDesktopMode(tabId: String, enabled: Boolean) {
        updateTab(tabId) { it.copy(desktopMode = enabled) }
        sessions[tabId]?.setDesktopMode(enabled, reload = true)
    }

    fun find(tabId: String, text: String, backwards: Boolean = false) {
        if (text.isEmpty()) {
            clearFind(tabId)
            return
        }
        sessions[tabId]?.find(text, backwards)
    }

    fun clearFind(tabId: String) {
        sessions[tabId]?.clearFind()
        updateContent(tabId) { it.copy(find = null) }
    }

    fun exitFullscreen(tabId: String) = sessions[tabId]?.exitFullscreen() ?: Unit

    fun translate(tabId: String, from: String, to: String) = sessions[tabId]?.translate(from, to) ?: Unit

    fun restoreOriginalLanguage(tabId: String) = sessions[tabId]?.restoreOriginalLanguage() ?: Unit

    /** Tracking protection off ([excepted]) or back on for the site in [tabId]; the page reloads. */
    fun setProtectionException(tabId: String, excepted: Boolean) = sessions[tabId]?.setProtectionException(excepted) ?: Unit

    // endregion

    // region Requests from the page

    fun consumeContextMenu(tabId: String) = updateContent(tabId) { it.copy(contextMenu = null) }

    /** Removes an answered (or abandoned) prompt from the tab. */
    fun consumePrompt(tabId: String, prompt: PromptRequest) =
        updateContent(tabId) { it.copy(prompts = it.prompts - prompt) }

    fun consumePermissionRequest(tabId: String, request: PermissionRequest) =
        updateContent(tabId) { it.copy(permissionRequests = it.permissionRequests - request) }

    fun consumeAppPermissionRequest(tabId: String, request: AppPermissionRequest) =
        updateContent(tabId) { it.copy(appPermissionRequests = it.appPermissionRequests - request) }

    // endregion

    // region Engine sessions

    /** The renderer [tabId] has right now, without creating one. Safe to call while drawing. */
    fun liveSession(tabId: String): EngineSession? = sessions[tabId]

    /** The renderer for [tabId], created (and restored) if the tab was suspended. */
    fun sessionFor(tabId: String): EngineSession? {
        sessions[tabId]?.let { return it }
        val tab = state.value.tab(tabId) ?: return null
        // A crashed tab waits for the user to reload it, so a crashing page cannot loop.
        if (tab.content.crashed) return null
        if (tab.url == null && tab.engineState == null) return null
        return wake(tab)
    }

    /** Frees the renderer of a background tab; it is restored when selected again. */
    fun suspendTab(tabId: String) {
        if (tabId == state.value.selectedTabId) return
        val tab = state.value.tab(tabId) ?: return
        val session = sessions.remove(tabId) ?: return
        tab.content.prompts.forEach { it.dismiss() }
        tab.content.permissionRequests.forEach { it.deny() }
        tab.content.appPermissionRequests.forEach { it.complete(false) }
        session.observer = null
        session.close()
        updateTab(tabId) { it.copy(isLive = false, content = ContentState()) }
    }

    /** Suspends background tabs not used for [inactiveMillis]. */
    fun suspendInactiveTabs(inactiveMillis: Long) {
        val threshold = clock() - inactiveMillis
        state.value.tabs
            .filter { it.isLive && it.id != state.value.selectedTabId && it.lastAccessedAt < threshold }
            .forEach { suspendTab(it.id) }
    }

    /** Memory is tight: keep only the visible tab alive. */
    fun suspendAllBackgroundTabs() {
        state.value.tabs.filter { it.isLive }.forEach { suspendTab(it.id) }
    }

    /** App left the foreground (or came back). */
    fun setAppVisible(visible: Boolean) {
        state.value.selectedTabId?.let { sessions[it]?.setActive(visible) }
    }

    // endregion

    // region Persistence

    fun snapshot(): SessionSnapshot = SessionSnapshot.from(state.value)

    /** Restores tabs from the last run. Only the selected tab gets a renderer now. */
    fun restore(snapshot: SessionSnapshot) {
        if (state.value.tabs.isNotEmpty()) return
        val tabs = snapshot.tabs.map { it.toTabState() }
        val selected = snapshot.selectedTabId?.takeIf { id -> tabs.any { it.id == id } }
        _state.value = BrowserState(tabs = tabs, selectedTabId = selected, privateMode = false)
        selected?.let { onSelected(it, previous = null) }
    }

    // endregion

    private fun insertTab(url: String?, private: Boolean, select: Boolean, parentId: String?, origin: TabOrigin): TabState {
        val now = clock()
        val tab = TabState(
            id = newId(),
            url = url,
            isPrivate = private,
            parentId = parentId,
            origin = origin,
            createdAt = now,
            lastAccessedAt = now,
            desktopMode = desktopModeByDefault,
        )
        val previous = state.value.selectedTabId
        _state.update { current ->
            // Tabs opened from a page go right after it, so related tabs stay together.
            val parentIndex = parentId?.let { pid -> current.tabs.indexOfFirst { it.id == pid } } ?: -1
            val tabs = current.tabs.toMutableList()
            if (parentIndex >= 0) tabs.add(parentIndex + 1, tab) else tabs.add(tab)
            current.copy(
                tabs = tabs,
                selectedTabId = if (select) tab.id else current.selectedTabId,
                privateMode = if (select) private else current.privateMode,
            )
        }
        if (select) previous?.let { sessions[it]?.setActive(false) }
        return tab
    }

    private fun onSelected(id: String, previous: String?) {
        previous?.let { sessions[it]?.setActive(false) }
        val session = sessionFor(id) ?: return
        session.setActive(true)
        engine.extensions.setActiveSession(session)
    }

    /** Creates a renderer for [tab] and restores what it showed before. */
    private fun wake(tab: TabState): EngineSession {
        val session = createSession(tab)
        val saved = tab.engineState
        when {
            saved != null -> session.restoreState(saved)
            tab.url != null -> session.load(tab.url)
        }
        return session
    }

    private fun createSession(tab: TabState): EngineSession {
        val session = engine.createSession(tab.isPrivate)
        if (tab.desktopMode) session.setDesktopMode(enabled = true, reload = false)
        bind(tab.id, session)
        return session
    }

    private fun bind(tabId: String, session: EngineSession) {
        session.observer = TabObserver(tabId)
        sessions[tabId] = session
        updateTab(tabId) { it.copy(isLive = true, rendererGeneration = it.rendererGeneration + 1) }
    }

    private fun releaseSession(id: String, tab: TabState) {
        tab.content.prompts.forEach { it.dismiss() }
        tab.content.permissionRequests.forEach { it.deny() }
        tab.content.appPermissionRequests.forEach { it.complete(false) }
        sessions.remove(id)?.let {
            it.observer = null
            it.close()
        }
    }

    private fun nextSelection(closed: TabState, remaining: List<TabState>): String? {
        val sameMode = remaining.filter { it.isPrivate == closed.isPrivate }
        closed.parentId?.let { pid -> sameMode.firstOrNull { it.id == pid }?.let { return it.id } }
        return sameMode.maxByOrNull { it.lastAccessedAt }?.id
    }

    private fun updateTab(id: String, transform: (TabState) -> TabState) {
        _state.update { current ->
            if (current.tabs.none { it.id == id }) current
            else current.copy(tabs = current.tabs.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun updateContent(id: String, transform: (ContentState) -> ContentState) =
        updateTab(id) { it.copy(content = transform(it.content)) }

    /** Engine events for one tab. Stale callbacks for closed tabs are dropped by [updateTab]. */
    private inner class TabObserver(private val tabId: String) : EngineSession.Observer {

        override fun onLocationChange(url: String, hasUserGesture: Boolean) {
            // A new renderer reports about:blank before the first real page; it is not a destination.
            if (url == "about:blank" && state.value.tab(tabId)?.url != null) return
            updateTab(tabId) { it.copy(url = url, content = it.content.copy(contextMenu = null, find = null)) }
        }

        override fun onTitleChange(title: String) = updateTab(tabId) { it.copy(title = title) }

        override fun onProgress(progress: Int) = updateContent(tabId) { it.copy(progress = progress) }

        override fun onLoadingStateChange(loading: Boolean) = updateContent(tabId) {
            if (loading) {
                it.copy(loading = true, progress = 0, trackersBlocked = 0, crashed = false)
            } else {
                it.copy(loading = false, progress = 100)
            }
        }

        override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) = updateContent(tabId) {
            it.copy(canGoBack = canGoBack ?: it.canGoBack, canGoForward = canGoForward ?: it.canGoForward)
        }

        override fun onSecurityChange(security: SecurityInfo) = updateContent(tabId) { it.copy(security = security) }

        override fun onTrackerBlocked(tracker: BlockedTracker) =
            updateContent(tabId) { it.copy(trackersBlocked = it.trackersBlocked + 1) }

        override fun onProtectionExceptionChange(excepted: Boolean) =
            updateContent(tabId) { it.copy(protectionException = excepted) }

        override fun onFullscreenChange(enabled: Boolean) = updateContent(tabId) {
            it.copy(fullscreen = enabled, mediaLandscape = if (enabled) it.mediaLandscape else null)
        }

        override fun onMediaFullscreen(enabled: Boolean, landscape: Boolean?) =
            updateContent(tabId) { it.copy(mediaLandscape = if (enabled) landscape else null) }

        override fun onStateUpdated(state: EngineSessionState) = updateTab(tabId) { it.copy(engineState = state) }

        override fun onContextMenu(hit: HitResult) = updateContent(tabId) { it.copy(contextMenu = hit) }

        override fun onDownload(download: DownloadRequest) {
            val delivered = _downloads.subscriptionCount.value > 0 && _downloads.tryEmit(download)
            if (!delivered) runCatching { download.body?.close() }
        }

        override fun onPrompt(prompt: PromptRequest) = updateContent(tabId) { it.copy(prompts = it.prompts + prompt) }

        override fun onPromptDismissed(prompt: PromptRequest) =
            updateContent(tabId) { it.copy(prompts = it.prompts - prompt) }

        override fun onPermissionRequest(request: PermissionRequest) =
            updateContent(tabId) { it.copy(permissionRequests = it.permissionRequests + request) }

        override fun onAppPermissionRequest(request: AppPermissionRequest) =
            updateContent(tabId) { it.copy(appPermissionRequests = it.appPermissionRequests + request) }

        override fun onWindowRequest(request: WindowRequest) {
            val opener = state.value.tab(tabId) ?: return
            when (request) {
                is WindowRequest.Open -> adopt(request.session, request.url, opener)
                WindowRequest.Close -> closeTab(tabId)
            }
        }

        override fun onFindResult(result: FindResult) = updateContent(tabId) { it.copy(find = result) }

        override fun onTranslationState(state: TranslationState) =
            updateContent(tabId) { it.copy(translation = state) }

        override fun onCrash() = lose(crashed = true)

        override fun onKill() = lose(crashed = false)

        /** The renderer is gone. A crash is shown to the user; a kill is quietly restored. */
        private fun lose(crashed: Boolean) {
            sessions.remove(tabId)?.observer = null
            updateTab(tabId) { it.copy(isLive = false, content = ContentState(crashed = crashed)) }
            if (!crashed && state.value.selectedTabId == tabId) sessionFor(tabId)
        }
    }

    /** A page opened a window: show its already-created session in a new tab after the opener. */
    private fun adopt(session: EngineSession, url: String, opener: TabState) {
        val tab = insertTab(url, opener.isPrivate, select = true, parentId = opener.id, origin = TabOrigin.Link)
        bind(tab.id, session)
        onSelected(tab.id, previous = opener.id)
    }
}

enum class BackResult {
    /** Back was used inside the browser. */
    Handled,

    /** Return to the app that opened the tab. */
    LeaveApp,

    /** Nothing to go back to; let the system handle it. */
    NotHandled,
}
