package com.blackcocadastudios.zeno.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackcocadastudios.zeno.AppContainer
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.BackResult
import com.blackcocadastudios.zeno.browser.state.BrowserState
import com.blackcocadastudios.zeno.browser.state.TabOrigin
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.browser.url.UrlInput
import com.blackcocadastudios.zeno.data.settings.UserSettings
import com.blackcocadastudios.zeno.download.DownloadEvent
import com.blackcocadastudios.zeno.download.DataUrls
import com.blackcocadastudios.zeno.download.DownloadNotifications
import com.blackcocadastudios.zeno.engine.ExtensionInstallRequest
import com.blackcocadastudios.zeno.engine.ExtensionPopup
import com.blackcocadastudios.zeno.web.Suggestions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Full-screen places besides the browser itself. */
enum class Screen { Browser, Settings, History, Bookmarks, Downloads, Extensions }

/** A short message shown above the toolbar. */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** A home page shortcut. */
data class Shortcut(val url: String, val title: String, val pinned: Boolean)

/** An extension asking to be installed, waiting for the user's answer. */
class PendingInstall(val request: ExtensionInstallRequest, private val answer: CompletableDeferred<Boolean>) {
    fun respond(approved: Boolean) {
        answer.complete(approved)
    }
}

/**
 * The interface's state holder. Browser state lives in [BrowserController];
 * this adds what only the interface cares about (which overlay is open, where
 * the user is).
 */
class MainViewModel(val container: AppContainer) : ViewModel() {
    val controller = container.controller
    val state: StateFlow<BrowserState> = controller.state
    val settings: StateFlow<UserSettings> = container.userSettings

    private val stack = mutableStateListOf(Screen.Browser)
    val screen: Screen get() = stack.last()

    var editing by mutableStateOf(false)
        private set
    var showTabs by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var findOpen by mutableStateOf(false)
    var extensionPopup by mutableStateOf<ExtensionPopup?>(null)
    var pendingInstall by mutableStateOf<PendingInstall?>(null)
        private set

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val shortcuts: StateFlow<List<Shortcut>> = combine(
        container.bookmarks.pinned,
        container.history.topSites(limit = 8),
        container.userSettings.map { it.showShortcuts },
    ) { pinned, top, show ->
        if (!show) return@combine emptyList()
        val hosts = HashSet<String>()
        val list = mutableListOf<Shortcut>()
        pinned.forEach { b ->
            if (hosts.add(UrlDisplay.host(b.url).orEmpty() + b.url)) list += Shortcut(b.url, b.title, pinned = true)
        }
        top.forEach { h ->
            val host = h.host.removePrefix("www.")
            if (list.none { UrlDisplay.host(it.url)?.removePrefix("www.") == host }) {
                list += Shortcut(h.url, h.title.ifBlank { UrlDisplay.siteName(h.url) }, pinned = false)
            }
        }
        list.take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        container.engine.extensions.installPrompt = { request ->
            val answer = CompletableDeferred<Boolean>()
            pendingInstall = PendingInstall(request, answer)
            try {
                answer.await()
            } finally {
                pendingInstall = null
            }
        }
        viewModelScope.launch {
            container.engine.extensions.popups.collect { popup ->
                extensionPopup?.session?.close()
                extensionPopup = popup
                showMenu = false
            }
        }
        viewModelScope.launch {
            container.downloadCoordinator.events.collect { event ->
                val app = container.app
                when (event) {
                    is DownloadEvent.Started -> message(app.getString(R.string.download_started, event.fileName))
                    is DownloadEvent.Completed -> message(
                        UiMessage(app.getString(R.string.download_complete), app.getString(R.string.download_open)) {
                            runCatching { app.startActivity(DownloadNotifications.openIntent(event.uri, event.mimeType)) }
                                .onFailure { message(app.getString(R.string.download_no_app)) }
                        },
                    )
                    is DownloadEvent.Failed -> message(app.getString(R.string.download_failed))
                }
            }
        }
    }

    override fun onCleared() {
        container.engine.extensions.installPrompt = null
        pendingInstall?.respond(false)
        extensionPopup?.session?.close()
    }

    // region Navigation between screens

    fun open(screen: Screen) {
        closeOverlays()
        if (screen != this.screen) stack += screen
    }

    /** Returns false when there was nothing to go back to. */
    fun popScreen(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun closeOverlays() {
        editing = false
        showTabs = false
        showMenu = false
    }

    // endregion

    // region Address bar

    fun startEditing() {
        showMenu = false
        showTabs = false
        findOpen = false
        editing = true
    }

    fun stopEditing() {
        editing = false
    }

    /** Text the address bar starts with when editing. */
    fun editableAddress(): String {
        val url = state.value.selectedTab?.url ?: return ""
        return UrlDisplay.editableText(url, container.searchEngine)
    }

    fun submit(text: String) {
        val url = UrlInput.resolve(text, container.searchEngine) ?: return
        controller.load(url)
        editing = false
    }

    fun openUrl(url: String, newTab: Boolean = false, private: Boolean = state.value.privateMode) {
        if (newTab) controller.newTab(url, private = private) else controller.load(url)
        editing = false
        if (screen != Screen.Browser) {
            while (popScreen()) Unit
        }
    }

    suspend fun suggest(text: String): Suggestions {
        val s = settings.value
        return container.suggestions.suggest(
            text = text,
            searchEngine = container.searchEngine,
            remote = s.searchSuggestions,
            private = state.value.privateMode,
        )
    }

    // endregion

    // region Tabs

    fun newTab(private: Boolean) {
        controller.newTab(private = private)
        showTabs = false
        showMenu = false
        editing = true
    }

    fun selectTab(id: String) {
        controller.selectTab(id)
        showTabs = false
    }

    fun closeTab(id: String) {
        controller.closeTab(id)
        container.thumbnails.remove(id)
    }

    fun closeAllTabs(private: Boolean) {
        val ids = state.value.tabs.filter { it.isPrivate == private }.map { it.id }
        controller.closeAllTabs(private)
        ids.forEach(container.thumbnails::remove)
    }

    /** System back on the browser screen. Returns false when the app should go to the background. */
    fun onBrowserBack(): Boolean = when (controller.handleBack()) {
        BackResult.Handled -> true
        BackResult.LeaveApp, BackResult.NotHandled -> false
    }

    // endregion

    // region Page actions

    fun toggleBookmark() {
        val tab = state.value.selectedTab ?: return
        val url = tab.url ?: return
        viewModelScope.launch {
            val bookmarked = container.bookmarks.isBookmarked(url).first()
            if (bookmarked) {
                container.bookmarks.remove(url)
                message(container.app.getString(R.string.menu_bookmark_removed))
            } else {
                container.bookmarks.add(url, tab.title.ifBlank { UrlDisplay.shortHost(url) })
                message(container.app.getString(R.string.menu_bookmark_added))
            }
        }
    }

    fun addToHome() {
        val tab = state.value.selectedTab ?: return
        val url = tab.url ?: return
        viewModelScope.launch {
            container.bookmarks.add(url, tab.title.ifBlank { UrlDisplay.siteName(url) }, pinned = true)
            message(container.app.getString(R.string.menu_added_to_home))
        }
        showMenu = false
    }

    fun removeShortcut(shortcut: Shortcut) {
        viewModelScope.launch {
            if (shortcut.pinned) container.bookmarks.setPinned(shortcut.url, false)
            else container.history.deleteSite(UrlDisplay.host(shortcut.url).orEmpty())
        }
    }

    fun openInBackground(url: String, private: Boolean) {
        val parent = state.value.selectedTabId
        controller.newTab(url, private = private, select = false, parentId = parent, origin = TabOrigin.Link)
        message(
            UiMessage(container.app.getString(R.string.context_opened_in_background), container.app.getString(R.string.context_switch)) {
                state.value.tabs.lastOrNull { it.url == url }?.let { controller.selectTab(it.id) }
            },
        )
    }

    /** Saves [url] (a link or an image) as a file. */
    fun download(url: String) {
        val private = state.value.selectedTab?.isPrivate ?: state.value.privateMode
        viewModelScope.launch {
            val request = if (url.startsWith("data:")) DataUrls.toDownload(url, private) else container.engine.download(url, private)
            if (request != null) container.downloadCoordinator.enqueue(request)
            else message(container.app.getString(R.string.download_failed))
        }
    }

    fun requestInstallUblock() = install(UBLOCK_URL)

    fun install(url: String) {
        viewModelScope.launch {
            container.engine.extensions.install(url)
                .onSuccess { message(container.app.getString(R.string.extensions_installed_toast, it.name)) }
                .onFailure { message(container.app.getString(R.string.extensions_failed)) }
        }
    }

    // endregion

    fun message(text: String) = message(UiMessage(text))

    fun message(message: UiMessage) {
        _messages.tryEmit(message)
    }

    /** Leaves the app; with "clear on exit" everything browsing-related goes first. */
    fun quit(onDone: () -> Unit) {
        viewModelScope.launch {
            if (settings.value.clearOnExit) {
                controller.closeAllTabs(private = false)
                controller.closeAllTabs(private = true)
                container.clearBrowsingData()
            } else {
                controller.closeAllTabs(private = true)
            }
            onDone()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }

    companion object {
        const val UBLOCK_URL = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    }
}
