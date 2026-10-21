package com.blackcocadastudios.zeno.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackcocadastudios.zeno.AppContainer
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.BackResult
import com.blackcocadastudios.zeno.browser.state.BrowserState
import com.blackcocadastudios.zeno.browser.state.TabOrigin
import com.blackcocadastudios.zeno.browser.state.TabState
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.browser.url.UrlInput
import com.blackcocadastudios.zeno.data.bookmarks.Bookmark
import com.blackcocadastudios.zeno.data.downloads.DownloadRecord
import com.blackcocadastudios.zeno.data.logins.SavedLogin
import com.blackcocadastudios.zeno.data.settings.UserSettings
import com.blackcocadastudios.zeno.download.DataUrls
import com.blackcocadastudios.zeno.download.DownloadEvent
import com.blackcocadastudios.zeno.download.DownloadNotifications
import com.blackcocadastudios.zeno.engine.ExtensionInstallRequest
import com.blackcocadastudios.zeno.engine.ExtensionPopup
import com.blackcocadastudios.zeno.engine.LoadFlags
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.ui.library.HistoryRange
import com.blackcocadastudios.zeno.web.Suggestions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/** Full-screen places besides the browser itself. */
enum class Screen { Browser, Settings, SitePermissions, Passwords, History, Bookmarks, Downloads, Extensions }

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
    var showSiteInfo by mutableStateOf(false)
    var findOpen by mutableStateOf(false)

    /** Changes whenever a site permission changes, so lists showing them reload. */
    var sitePermissionsVersion by mutableIntStateOf(0)
        private set
    var extensionPopup by mutableStateOf<ExtensionPopup?>(null)
    var pendingInstall by mutableStateOf<PendingInstall?>(null)
        private set

    /** Install URLs in progress, so their buttons can say so. */
    var installing by mutableStateOf(emptySet<String>())
        private set

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val app get() = container.app

    val shortcuts: StateFlow<List<Shortcut>> = combine(
        container.bookmarks.pinned,
        container.history.topSites(limit = 8),
        container.userSettings.map { it.showShortcuts },
    ) { pinned, top, show ->
        if (!show) return@combine emptyList()
        val list = mutableListOf<Shortcut>()
        pinned.forEach { b ->
            if (list.none { it.url == b.url }) list += Shortcut(b.url, b.title, pinned = true)
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
                when (event) {
                    is DownloadEvent.Started -> message(app.getString(R.string.download_started, event.fileName))
                    is DownloadEvent.Completed -> message(
                        UiMessage(app.getString(R.string.download_complete), app.getString(R.string.download_open)) {
                            openFile(event.uri.toString(), event.mimeType)
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

    private fun popToBrowser() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
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
        showSiteInfo = false
    }

    // endregion

    // region Address bar

    fun startEditing() {
        showMenu = false
        showSiteInfo = false
        showTabs = false
        findOpen = false
        editing = true
    }

    fun stopEditing() {
        editing = false
    }

    /** Text the address bar starts with when editing. */
    fun editableAddress(): String {
        val url = pageUrl(state.value.selectedTab) ?: return ""
        return UrlDisplay.editableText(url, container.searchEngine)
    }

    fun submit(text: String) {
        val url = UrlInput.resolve(text, container.searchEngine) ?: return
        controller.load(url)
        editing = false
    }

    /** Opens [url] here, or in a new tab; from any screen, ending on the browser. */
    fun openUrl(url: String, newTab: Boolean = false, private: Boolean = state.value.privateMode) {
        if (newTab) controller.newTab(url, private = private) else controller.load(url)
        editing = false
        popToBrowser()
    }

    /** A link from another app: a normal tab, and back on its first page returns to that app. */
    fun openExternal(url: String) {
        controller.newTab(url, private = false, origin = TabOrigin.External, flags = LoadFlags(external = true))
        closeOverlays()
        findOpen = false
        popToBrowser()
    }

    /** A tap on a site's notification: its tab comes forward and the site learns of the tap. */
    fun openSiteNotification(tag: String?, token: String?) {
        val notification = container.siteNotifications.tapped(tag, token) ?: return
        val host = (notification.source ?: notification.origin)?.let(UrlDisplay::host)
        val tab = state.value.tabs.firstOrNull { !it.isPrivate && it.url == notification.source }
            ?: state.value.tabs.firstOrNull { !it.isPrivate && host != null && it.url?.let(UrlDisplay::host) == host }
        tab?.let { controller.selectTab(it.id) }
        closeOverlays()
        popToBrowser()
        notification.click()
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

    /** Zeno's home page in the given mode, with the address bar ready; the tab is created when something loads. */
    fun newTab(private: Boolean) {
        controller.showHome(private)
        showTabs = false
        showMenu = false
        findOpen = false
        popToBrowser()
        editing = true
    }

    fun selectTab(id: String) {
        controller.selectTab(id)
        showTabs = false
    }

    /** Closes [id]; for a moment the user can bring it back. */
    fun closeTab(id: String) {
        val before = state.value
        val tab = before.tab(id) ?: return
        val index = before.tabs.indexOf(tab)
        val wasSelected = before.selectedTabId == id
        val thumbnail = container.thumbnails.get(id)
        controller.closeTab(id)
        container.thumbnails.remove(id)
        if (tab.url == null) return
        message(
            UiMessage(app.getString(R.string.tabs_closed), app.getString(R.string.tabs_undo)) {
                val reopened = controller.reopen(tab, index, select = wasSelected)
                thumbnail?.let { container.thumbnails.put(reopened, it) }
            },
        )
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

    fun isBookmarked(url: String?): Flow<Boolean> = url?.let(container.bookmarks::isBookmarked) ?: flowOf(false)

    fun toggleBookmark() {
        val tab = state.value.selectedTab ?: return
        val url = pageUrl(tab) ?: return
        viewModelScope.launch {
            val bookmarked = container.bookmarks.isBookmarked(url).first()
            if (bookmarked) {
                container.bookmarks.remove(url)
                message(app.getString(R.string.menu_bookmark_removed))
            } else {
                container.bookmarks.add(url, tab.title.ifBlank { UrlDisplay.shortHost(url) })
                message(app.getString(R.string.menu_bookmark_added))
            }
        }
    }

    fun addToHome() {
        val tab = state.value.selectedTab ?: return
        val url = pageUrl(tab) ?: return
        viewModelScope.launch {
            container.bookmarks.add(url, tab.title.ifBlank { UrlDisplay.siteName(url) }, pinned = true)
            message(app.getString(R.string.menu_added_to_home))
        }
        showMenu = false
    }

    fun removeShortcut(shortcut: Shortcut) {
        viewModelScope.launch {
            if (shortcut.pinned) {
                container.bookmarks.setPinned(shortcut.url, false)
            } else {
                container.history.deleteSite(UrlDisplay.host(shortcut.url).orEmpty())
            }
        }
    }

    fun openInBackground(url: String, private: Boolean) {
        val parent = state.value.selectedTabId
        val id = controller.newTab(url, private = private, select = false, parentId = parent, origin = TabOrigin.Link)
        message(
            UiMessage(app.getString(R.string.context_opened_in_background), app.getString(R.string.context_switch)) {
                if (state.value.tab(id) != null) controller.selectTab(id)
            },
        )
    }

    /** The page [url] shows in reader mode, or null when it is not reader mode. */
    fun readerOriginal(url: String): String? = container.engine.reader.originalUrl(url)

    /** The address of what [tab] shows, for bookmarks and sharing: in reader mode, the page being read. */
    fun pageUrl(tab: TabState?): String? = tab?.url?.let { readerOriginal(it) ?: it }

    /** Reader mode on, or back to the page. */
    fun toggleReader() {
        val tab = state.value.selectedTab ?: return
        val url = tab.url ?: return
        val original = readerOriginal(url)
        if (original != null) {
            // Reader mode was opened from the page, so going back returns to it (scroll position included).
            if (tab.content.canGoBack) controller.goBack(tab.id) else controller.load(original, tab.id, LoadFlags(replaceHistory = true))
            return
        }
        viewModelScope.launch {
            val reader = container.engine.reader.readerUrl(url)
            if (reader == null) {
                message(app.getString(R.string.reader_unavailable))
                return@launch
            }
            if (state.value.selectedTab?.id == tab.id) controller.load(reader, tab.id)
        }
    }

    /** Translates the page into the user's language, or shows the original again. */
    fun translate() {
        val tab = state.value.selectedTab ?: return
        val translation = tab.content.translation
        if (translation?.isTranslated == true) {
            controller.restoreOriginalLanguage(tab.id)
            return
        }
        val from = translation?.documentLanguage
        val to = translation?.userLanguage ?: Locale.getDefault().language
        if (from.isNullOrBlank() || from.substringBefore('-') == to.substringBefore('-')) {
            message(app.getString(R.string.translate_unavailable))
            return
        }
        controller.translate(tab.id, from, to)
        message(app.getString(R.string.translate_working))
    }

    // region Logins

    /** Offer to save a login for [origin]? Not when the user said never there, or turned it off. */
    suspend fun shouldOfferToSave(origin: String): Boolean =
        settings.value.askToSavePasswords && !runCatching { container.logins.isNeverSave(origin) }.getOrDefault(false)

    fun neverSaveLogins(origin: String) {
        viewModelScope.launch { runCatching { container.logins.neverSave(origin) } }
    }

    fun allowSavingLogins(origin: String) {
        viewModelScope.launch { runCatching { container.logins.allowSaving(origin) } }
    }

    fun changeLogin(guid: String, username: String, password: String) {
        viewModelScope.launch { runCatching { container.logins.update(guid, username, password) } }
    }

    fun deleteLogin(login: SavedLogin) {
        viewModelScope.launch {
            runCatching { container.logins.delete(login.guid) }
            message(app.getString(R.string.login_deleted))
        }
    }

    /** Copies a username or password; a password is marked sensitive so the clipboard preview hides it. */
    fun copyLoginPart(text: String, isPassword: Boolean) {
        val clip = ClipData.newPlainText(app.getString(if (isPassword) R.string.prompt_password else R.string.prompt_username), text)
        if (isPassword) clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        app.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        // Android 13+ confirms copies itself.
        if (Build.VERSION.SDK_INT < 33) {
            message(app.getString(if (isPassword) R.string.login_copied_password else R.string.login_copied_username))
        }
    }

    /** Leaving the app closes the passwords: coming back asks for the screen lock again. */
    fun lockPasswords() {
        if (screen == Screen.Passwords) popScreen()
    }

    // endregion

    // region Site permissions

    suspend fun sitePermissions(url: String, private: Boolean): List<SitePermission> =
        runCatching { container.engine.sitePermissions.forSite(url, private) }.getOrDefault(emptyList())

    suspend fun allSitePermissions(): List<SitePermission> =
        runCatching { container.engine.sitePermissions.all() }.getOrDefault(emptyList())

    fun setSitePermission(permission: SitePermission, allowed: Boolean?) {
        viewModelScope.launch {
            runCatching { container.engine.sitePermissions.set(permission, allowed) }
            sitePermissionsVersion++
        }
    }

    /** Tracking protection on or off for the site in the current tab; the page reloads. */
    fun setProtection(enabled: Boolean) {
        val tab = state.value.selectedTab ?: return
        val url = tab.url ?: return
        controller.setProtectionException(tab.id, excepted = !enabled)
        showSiteInfo = false
        val site = UrlDisplay.shortHost(url)
        message(app.getString(if (enabled) R.string.site_protection_turned_on else R.string.site_protection_turned_off, site))
    }

    /** Cookies, storage and permissions of the current tab's site. */
    fun clearSiteData() {
        val url = state.value.selectedTab?.url ?: return
        val host = UrlDisplay.host(url) ?: return
        showSiteInfo = false
        viewModelScope.launch {
            runCatching { container.engine.clearSiteData(host) }
            sitePermissionsVersion++
            message(app.getString(R.string.site_cleared, UrlDisplay.shortHost(url)))
        }
    }

    // endregion

    /** Saves [url] (a link or an image) as a file. */
    fun download(url: String) {
        val private = state.value.selectedTab?.isPrivate ?: state.value.privateMode
        viewModelScope.launch {
            val request = if (url.startsWith("data:")) {
                // Inline images can be megabytes of base64: decode off the main thread.
                withContext(Dispatchers.Default) { DataUrls.toDownload(url, private) }
            } else {
                container.engine.download(url, private)
            }
            if (request != null) {
                container.downloadCoordinator.enqueue(request)
            } else {
                message(app.getString(R.string.download_failed))
            }
        }
    }

    // endregion

    // region Library

    fun deleteHistory(url: String) {
        viewModelScope.launch { container.history.delete(url) }
    }

    fun clearHistory(range: HistoryRange) {
        viewModelScope.launch {
            when (range) {
                HistoryRange.LastHour -> container.history.deleteSince(System.currentTimeMillis() - 60 * 60 * 1000L)
                HistoryRange.TodayAndYesterday -> container.history.deleteSince(startOfYesterday())
                HistoryRange.Everything -> container.history.clear()
            }
        }
    }

    fun deleteBookmark(url: String) {
        viewModelScope.launch { container.bookmarks.remove(url) }
    }

    fun renameBookmark(url: String, title: String) {
        viewModelScope.launch { container.bookmarks.rename(url, title) }
    }

    fun toggleBookmarkPin(bookmark: Bookmark) {
        viewModelScope.launch { container.bookmarks.setPinned(bookmark.url, !bookmark.pinned) }
    }

    fun openDownload(record: DownloadRecord) {
        val uri = record.contentUri ?: return
        openFile(uri, record.mimeType ?: "*/*")
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { container.downloadCoordinator.delete(id) }
    }

    fun cancelDownload(id: Long) = container.downloadCoordinator.cancel(id)

    fun clearDownloads() {
        viewModelScope.launch { container.downloadCoordinator.clearFinished() }
    }

    private fun openFile(uri: String, mimeType: String) {
        runCatching { app.startActivity(DownloadNotifications.openIntent(uri.toUri(), mimeType)) }
            .onFailure { message(app.getString(R.string.download_no_app)) }
    }

    // endregion

    // region Extensions

    fun requestInstallUblock() = install(UBLOCK_URL)

    fun install(url: String) {
        if (url in installing) return
        installing = installing + url
        viewModelScope.launch {
            try {
                container.engine.extensions.install(url)
                    .onSuccess { message(app.getString(R.string.extensions_installed_toast, it.name)) }
                    .onFailure { message(app.getString(R.string.extensions_failed)) }
            } finally {
                installing = installing - url
            }
        }
    }

    fun setExtensionEnabled(id: String, enabled: Boolean) = extensionAction { setEnabled(id, enabled) }

    fun setExtensionAllowedInPrivate(id: String, allowed: Boolean) = extensionAction { setAllowedInPrivate(id, allowed) }

    fun uninstallExtension(id: String) = extensionAction { uninstall(id) }

    private fun extensionAction(action: suspend com.blackcocadastudios.zeno.engine.ExtensionController.() -> Result<*>) {
        viewModelScope.launch {
            container.engine.extensions.action().onFailure { message(app.getString(R.string.extensions_action_failed)) }
        }
    }

    // endregion

    // region Settings

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    /** History, cookies, cache and site data; open tabs stay. */
    fun clearBrowsingData() {
        viewModelScope.launch {
            container.clearBrowsingData(includeTabs = false)
            message(app.getString(R.string.settings_cleared))
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
            controller.closeAllTabs(private = true)
            if (settings.value.clearOnExit) {
                controller.closeAllTabs(private = false)
                container.clearBrowsingData(includeTabs = true)
            }
            onDone()
        }
    }

    private fun startOfYesterday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }

    companion object {
        /** ClipDescription.EXTRA_IS_SENSITIVE (Android 13); older versions ignore it. */
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        const val UBLOCK_URL = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    }
}
