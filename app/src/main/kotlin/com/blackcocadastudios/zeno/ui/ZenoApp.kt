package com.blackcocadastudios.zeno.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackcocadastudios.zeno.BuildConfig
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.data.settings.AppTheme
import com.blackcocadastudios.zeno.data.settings.UserSettings
import com.blackcocadastudios.zeno.design.theme.ZenoTheme
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.engine.gecko.GeckoEngineView
import com.blackcocadastudios.zeno.ui.browser.BrowserScreen
import com.blackcocadastudios.zeno.ui.common.MessageHost
import com.blackcocadastudios.zeno.ui.extensions.ExtensionPopupSheet
import com.blackcocadastudios.zeno.ui.extensions.ExtensionsActions
import com.blackcocadastudios.zeno.ui.extensions.ExtensionsScreen
import com.blackcocadastudios.zeno.ui.extensions.InstallDialog
import com.blackcocadastudios.zeno.ui.library.BookmarksScreen
import com.blackcocadastudios.zeno.ui.library.DownloadsScreen
import com.blackcocadastudios.zeno.ui.library.HistoryScreen
import com.blackcocadastudios.zeno.ui.settings.SettingsActions
import com.blackcocadastudios.zeno.ui.settings.SettingsScreen
import com.blackcocadastudios.zeno.ui.settings.SitePermissionsScreen

/** What the interface asks of the activity hosting it. */
interface AppHost {
    /** Light icons on the system bars when the screen in front is dark. */
    fun setSystemBarsDark(dark: Boolean)

    /** Asks Android to make Zeno the default browser. */
    fun makeDefaultBrowser()

    /** Android 13+: asks to show notifications (needed for sites' notifications). */
    fun requestNotificationPermission()
}

/**
 * Zeno's whole interface. The browser is always there underneath; settings
 * and the library open over it, in the day (or night) theme, never Yoru.
 */
@Composable
fun ZenoApp(vm: MainViewModel, engineView: GeckoEngineView, host: AppHost) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val dark = when (settings.appTheme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
    val screen = vm.screen
    val onBrowser = screen == Screen.Browser
    val frontPrivate = onBrowser && state.privateMode

    LaunchedEffect(dark, frontPrivate) { host.setSystemBarsDark(dark || frontPrivate) }

    Box(Modifier.fillMaxSize()) {
        ZenoTheme(dark = dark, privateMode = state.privateMode) {
            BrowserScreen(vm, engineView, active = onBrowser)
        }
        if (!onBrowser) {
            ZenoTheme(dark = dark) {
                BackHandler { vm.popScreen() }
                Box(Modifier.fillMaxSize()) {
                    LibraryOrSettings(vm, screen, settings, host)
                    MessageHost(
                        vm.messages,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
            }
        }
        // Asked from the menu or the extensions page; answered wherever the user is.
        ZenoTheme(dark = dark, privateMode = frontPrivate) {
            vm.pendingInstall?.let { InstallDialog(it) }
            vm.extensionPopup?.let { popup ->
                ExtensionPopupSheet(popup) {
                    popup.session.close()
                    vm.extensionPopup = null
                }
            }
        }
    }
}

@Composable
private fun LibraryOrSettings(vm: MainViewModel, screen: Screen, settings: UserSettings, host: AppHost) {
    val goBack: () -> Unit = { vm.popScreen() }
    when (screen) {
        Screen.Browser -> Unit
        Screen.Settings -> SettingsScreen(
            settings = settings,
            versions = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, vm.container.engine.version),
            actions = remember(vm, host) {
                object : SettingsActions {
                    override fun update(transform: (UserSettings) -> UserSettings) = vm.updateSettings(transform)

                    override fun clearBrowsingData() = vm.clearBrowsingData()

                    override fun openUrl(url: String) = vm.openUrl(url, newTab = true)

                    override fun makeDefaultBrowser() = host.makeDefaultBrowser()

                    override fun sitePermissions() = vm.open(Screen.SitePermissions)

                    override fun requestNotificationPermission() = host.requestNotificationPermission()

                    override fun back() = goBack()
                }
            },
        )
        Screen.SitePermissions -> {
            var permissions by remember { mutableStateOf<List<SitePermission>?>(null) }
            LaunchedEffect(vm.sitePermissionsVersion) { permissions = vm.allSitePermissions() }
            SitePermissionsScreen(permissions = permissions, onChange = vm::setSitePermission, onBack = goBack)
        }
        Screen.History -> {
            val entries by remember { vm.container.history.recent() }.collectAsStateWithLifecycle(initialValue = emptyList())
            val now = remember { System.currentTimeMillis() }
            HistoryScreen(
                entries = entries,
                now = now,
                onOpen = { vm.openUrl(it, newTab = true) },
                onDelete = vm::deleteHistory,
                onClear = vm::clearHistory,
                onBack = goBack,
            )
        }
        Screen.Bookmarks -> {
            val bookmarks by remember { vm.container.bookmarks.all }.collectAsStateWithLifecycle(initialValue = emptyList())
            BookmarksScreen(
                bookmarks = bookmarks,
                onOpen = { vm.openUrl(it, newTab = true) },
                onDelete = vm::deleteBookmark,
                onRename = vm::renameBookmark,
                onTogglePin = vm::toggleBookmarkPin,
                onBack = goBack,
            )
        }
        Screen.Downloads -> {
            val downloads by remember { vm.container.downloadCoordinator.records }.collectAsStateWithLifecycle(initialValue = emptyList())
            val now = remember { System.currentTimeMillis() }
            DownloadsScreen(
                downloads = downloads,
                now = now,
                onOpen = vm::openDownload,
                onDelete = vm::deleteDownload,
                onCancel = vm::cancelDownload,
                onClear = vm::clearDownloads,
                onBack = goBack,
            )
        }
        Screen.Extensions -> {
            val installed by vm.container.engine.extensions.installed.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.container.engine.extensions.refresh() }
            ExtensionsScreen(
                installed = installed,
                installing = vm.installing,
                actions = remember(vm) {
                    object : ExtensionsActions {
                        override fun install(url: String) = vm.install(url)

                        override fun setEnabled(id: String, enabled: Boolean) = vm.setExtensionEnabled(id, enabled)

                        override fun setAllowedInPrivate(id: String, allowed: Boolean) = vm.setExtensionAllowedInPrivate(id, allowed)

                        override fun remove(id: String) = vm.uninstallExtension(id)

                        override fun openUrl(url: String) = vm.openUrl(url, newTab = true)

                        override fun back() = goBack()
                    }
                },
            )
        }
    }
}
