package com.blackcocadastudios.zeno

import android.app.Application
import com.blackcocadastudios.zeno.browser.BrowserController
import com.blackcocadastudios.zeno.browser.BrowserExtensionTabs
import com.blackcocadastudios.zeno.browser.search.SearchEngine
import com.blackcocadastudios.zeno.browser.search.SearchEngines
import com.blackcocadastudios.zeno.browser.session.SessionSnapshot
import com.blackcocadastudios.zeno.browser.state.TabOrigin
import com.blackcocadastudios.zeno.data.bookmarks.BookmarksRepository
import com.blackcocadastudios.zeno.data.ZenoStorage
import com.blackcocadastudios.zeno.data.downloads.DownloadsRepository
import com.blackcocadastudios.zeno.data.history.HistoryRepository
import com.blackcocadastudios.zeno.data.logins.LoginsRepository
import com.blackcocadastudios.zeno.data.session.SessionStore
import com.blackcocadastudios.zeno.data.settings.SettingsRepository
import com.blackcocadastudios.zeno.data.settings.UserSettings
import com.blackcocadastudios.zeno.download.DownloadCoordinator
import com.blackcocadastudios.zeno.engine.BrowsingData
import com.blackcocadastudios.zeno.engine.WindowOpener
import com.blackcocadastudios.zeno.engine.gecko.GeckoEngine
import com.blackcocadastudios.zeno.notify.SiteNotifications
import com.blackcocadastudios.zeno.web.ExternalAppInterceptor
import com.blackcocadastudios.zeno.web.SuggestionProvider
import com.blackcocadastudios.zeno.web.ThumbnailStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Builds and wires Zeno's objects once per process. Manual injection: every
 * dependency is visible here, nothing is created by reflection.
 */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val engine = GeckoEngine(app, debug = BuildConfig.DEBUG)
    val controller = BrowserController(engine)

    private val storage by lazy { ZenoStorage(app) }
    val history: HistoryRepository get() = storage.history
    val bookmarks: BookmarksRepository get() = storage.bookmarks
    val downloads: DownloadsRepository get() = storage.downloads
    val logins: LoginsRepository get() = storage.logins
    val settings = SettingsRepository(app)
    private val sessionStore = SessionStore(app.filesDir)

    val thumbnails = ThumbnailStore()
    val siteNotifications = SiteNotifications(app) { userSettings.value.siteNotifications }
    val suggestions by lazy { SuggestionProvider(engine, history, bookmarks) }
    val downloadCoordinator by lazy { DownloadCoordinator(app, downloads, scope) }

    val userSettings: StateFlow<UserSettings> =
        settings.settings.stateIn(scope, SharingStarted.Eagerly, UserSettings())

    val searchEngine: SearchEngine get() = SearchEngines.byId(userSettings.value.searchEngineId)

    private val _ready = MutableStateFlow(false)

    /** True once the previous session was restored (or there was none). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun start() {
        engine.historyTracker = history
        engine.requestInterceptor = ExternalAppInterceptor(app)
        engine.extensions.tabs = BrowserExtensionTabs(controller)
        engine.notificationHandler = siteNotifications
        engine.loginStore = logins
        engine.windowOpener = WindowOpener { session, url ->
            controller.adoptSession(session, url, private = false, origin = TabOrigin.Link)
            true
        }

        scope.launch {
            settings.settings.collect { s ->
                engine.applySettings(s.toEngineSettings())
                controller.desktopModeByDefault = s.desktopModeByDefault
            }
        }
        scope.launch {
            // Turning site notifications off clears the ones on screen.
            userSettings.map { it.siteNotifications }.distinctUntilChanged().drop(1).collect { on ->
                if (!on) siteNotifications.cancelAll()
            }
        }
        scope.launch { controller.downloads.collect(downloadCoordinator::enqueue) }
        scope.launch {
            // Thumbnails of tabs closed by their own pages (window.close) go too.
            controller.state.map { state -> state.tabs.mapTo(HashSet()) { it.id } }.distinctUntilChanged().collect(thumbnails::retain)
        }
        scope.launch {
            // When the last private tab closes, its downloads leave the list too.
            controller.state.map { it.privateTabs.isNotEmpty() }.distinctUntilChanged().drop(1).collect { open ->
                if (!open) downloadCoordinator.forgetPrivate()
            }
        }
        scope.launch { downloads.markInterrupted() }
        recordHistoryTitles()
        scope.launch {
            restoreSession()
            _ready.value = true
            persistSession()
        }
    }

    suspend fun awaitReady() {
        ready.first { it }
    }

    private suspend fun restoreSession() {
        val restore = settings.settings.first().restoreTabs
        if (!restore) {
            sessionStore.clear()
            return
        }
        sessionStore.read()?.let(controller::restore)
    }

    @OptIn(FlowPreview::class)
    private fun persistSession() {
        scope.launch {
            controller.state
                .map { SessionSnapshot.from(it) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(1_500)
                .collect { snapshot -> runCatching { if (userSettings.value.restoreTabs) sessionStore.write(snapshot) } }
        }
    }

    /** Saves right away; used when the app goes to the background. */
    fun saveSessionNow() {
        scope.launch {
            runCatching { if (userSettings.value.restoreTabs) sessionStore.write(controller.snapshot()) }
        }
    }

    /** The engine reports visits without titles; titles arrive later through the tab state. */
    private fun recordHistoryTitles() {
        scope.launch {
            var known = emptySet<Pair<String, String>>()
            controller.state
                .map { state ->
                    state.tabs
                        .filter { !it.isPrivate && it.url != null && it.title.isNotBlank() }
                        .map { it.url!! to it.title }
                        .toSet()
                }
                .distinctUntilChanged()
                .collect { current ->
                    (current - known).forEach { (url, title) -> runCatching { history.updateTitle(url, title) } }
                    known = current
                }
        }
    }

    /**
     * Everything stored about browsing: history, cookies, cache, site data and,
     * with [includeTabs], the saved tabs and their thumbnails (close the tabs first).
     */
    suspend fun clearBrowsingData(includeTabs: Boolean) {
        runCatching { history.clear() }
        runCatching { engine.clearData(setOf(BrowsingData.All)) }
        if (includeTabs) {
            thumbnails.clear()
            sessionStore.clear()
        }
    }
}
