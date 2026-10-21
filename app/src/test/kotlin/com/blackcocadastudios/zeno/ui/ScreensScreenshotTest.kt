package com.blackcocadastudios.zeno.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.search.SearchEngines
import com.blackcocadastudios.zeno.browser.state.ContentState
import com.blackcocadastudios.zeno.browser.state.TabState
import com.blackcocadastudios.zeno.data.bookmarks.Bookmark
import com.blackcocadastudios.zeno.data.downloads.DownloadRecord
import com.blackcocadastudios.zeno.data.downloads.DownloadStatus
import com.blackcocadastudios.zeno.data.history.HistoryEntry
import com.blackcocadastudios.zeno.data.settings.UserSettings
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.design.theme.ZenoTheme
import com.blackcocadastudios.zeno.engine.ExtensionInfo
import com.blackcocadastudios.zeno.engine.FindResult
import com.blackcocadastudios.zeno.engine.PermissionKind
import com.blackcocadastudios.zeno.engine.SecurityInfo
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.ui.browser.CrashedTab
import com.blackcocadastudios.zeno.ui.browser.addressDisplay
import com.blackcocadastudios.zeno.ui.extensions.ExtensionsActions
import com.blackcocadastudios.zeno.ui.extensions.ExtensionsScreen
import com.blackcocadastudios.zeno.ui.home.HomeScreen
import com.blackcocadastudios.zeno.ui.home.UblockCard
import com.blackcocadastudios.zeno.ui.library.BookmarksScreen
import com.blackcocadastudios.zeno.ui.library.DownloadsScreen
import com.blackcocadastudios.zeno.ui.library.HistoryScreen
import com.blackcocadastudios.zeno.ui.menu.MenuActions
import com.blackcocadastudios.zeno.ui.menu.MenuContent
import com.blackcocadastudios.zeno.ui.menu.MenuExtension
import com.blackcocadastudios.zeno.ui.menu.MenuPage
import com.blackcocadastudios.zeno.ui.menu.ReaderState
import com.blackcocadastudios.zeno.ui.menu.SheetHandle
import com.blackcocadastudios.zeno.ui.settings.SettingsActions
import com.blackcocadastudios.zeno.ui.prompts.PermissionQuestion
import com.blackcocadastudios.zeno.ui.settings.SettingsScreen
import com.blackcocadastudios.zeno.ui.settings.SitePermissionsScreen
import com.blackcocadastudios.zeno.ui.site.SiteInfo
import com.blackcocadastudios.zeno.ui.site.SitePanelActions
import com.blackcocadastudios.zeno.ui.site.SitePanelContent
import com.blackcocadastudios.zeno.ui.tabs.TabsScreen
import com.blackcocadastudios.zeno.ui.toolbar.FindBar
import com.blackcocadastudios.zeno.ui.toolbar.Toolbar
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Every screen of Zeno, rendered with sample data, so a change shows up in
 * review as pictures. The page itself (Gecko) cannot run here; a sketch of a
 * page stands in for it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi", application = Application::class)
class ScreensScreenshotTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 21, 15, 0, 0) }.timeInMillis

    private val shortcuts = listOf(
        Shortcut("https://pt.wikipedia.org/", "Wikipédia", pinned = true),
        Shortcut("https://www.folha.uol.com.br/", "Folha", pinned = false),
        Shortcut("https://github.com/", "GitHub", pinned = false),
        Shortcut("https://www.youtube.com/", "YouTube", pinned = false),
        Shortcut("https://www.nhk.or.jp/", "NHK", pinned = false),
        Shortcut("https://duckduckgo.com/", "DuckDuckGo", pinned = false),
    )

    private val tabs = listOf(
        TabState("a", "https://pt.wikipedia.org/wiki/Zen", "Zen – Wikipédia, a enciclopédia livre", isLive = true),
        TabState("b", "https://www.folha.uol.com.br/", "Folha de S.Paulo"),
        TabState("c", "https://github.com/blackcocadastudios/zeno", "blackcocadastudios/zeno"),
        TabState("d", null),
        TabState("e", "https://www.nhk.or.jp/", "NHK"),
        TabState("p", "https://duckduckgo.com/?q=jardim+zen", "jardim zen", isPrivate = true),
    )

    @Test
    fun homeDay() = capture("home_day") { Home(privateMode = false, withCard = true) }

    @Test
    fun homeNight() = capture("home_night", dark = true) { Home(privateMode = false, withCard = false) }

    @Test
    fun homePrivate() = capture("home_private", privateMode = true) { Home(privateMode = true, withCard = false) }

    @Test
    fun pageLoading() = capture("page_loading") {
        val tab = TabState(
            "a",
            "https://pt.wikipedia.org/wiki/Zen",
            "Zen",
            content = ContentState(loading = true, progress = 62, security = SecurityInfo(secure = true)),
        )
        BrowserFrame(tab, tabCount = 5) { FakePage() }
    }

    @Test
    fun pageSearch() = capture("page_search", dark = true) {
        val tab = TabState("a", SearchEngines.DuckDuckGo.searchUrl("jardim zen kyoto"), "jardim zen kyoto")
        BrowserFrame(tab, tabCount = 12) { FakePage() }
    }

    @Test
    fun pageInsecure() = capture("page_insecure") {
        val tab = TabState("a", "http://neverssl.com/", "NeverSSL")
        BrowserFrame(tab, tabCount = 1) { FakePage() }
    }

    @Test
    fun pageReader() = capture("page_reader") {
        val tab = TabState("a", READER_URL, "O jardim de pedras de Ryōan-ji")
        BrowserFrame(tab, tabCount = 3, readerOriginal = { url -> url.takeIf { it == READER_URL }?.let { "https://www.caderno.example/ryoanji" } }) { FakePage() }
    }

    @Test
    fun findInPage() = capture("find_in_page") {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { FakePage() }
            FindBar(FindResult(found = true, current = 2, total = 7, searchText = "zen"), {}, {}, {}, {})
        }
    }

    @Test
    fun tabsGrid() = capture("tabs_grid") {
        TabsScreen(tabs, selectedTabId = "a", privateMode = false, thumbnail = { null }, onSelect = {}, onClose = {}, onCloseAll = {}, onNewTab = {}, onDismiss = {})
    }

    @Test
    fun tabsPrivate() = capture("tabs_private", privateMode = true) {
        TabsScreen(tabs, selectedTabId = "p", privateMode = true, thumbnail = { null }, onSelect = {}, onClose = {}, onCloseAll = {}, onNewTab = {}, onDismiss = {})
    }

    @Test
    fun menu() = capture("menu") { MenuOverPage(dark = false) }

    @Test
    fun menuNight() = capture("menu_night", dark = true) { MenuOverPage(dark = true) }

    @Test
    @Config(qualifiers = "+h1500dp")
    fun settings() = capture("settings", tall = true) {
        SettingsScreen(UserSettings(), versions = "Versão 0.1.0 · Gecko 158.0.1", actions = NoSettingsActions)
    }

    @Test
    fun history() = capture("history") {
        val entries = listOf(
            HistoryEntry("https://pt.wikipedia.org/wiki/Zen", "Zen – Wikipédia", "pt.wikipedia.org", 3, now - 5 * MINUTE),
            HistoryEntry("https://github.com/blackcocadastudios/zeno", "blackcocadastudios/zeno", "github.com", 9, now - 3 * HOUR),
            HistoryEntry("https://www.folha.uol.com.br/", "Folha de S.Paulo", "www.folha.uol.com.br", 21, now - 26 * HOUR),
            HistoryEntry("https://www.nhk.or.jp/", "NHK", "www.nhk.or.jp", 2, now - 3 * DAY),
            HistoryEntry("https://www.ghibli.jp/", "スタジオジブリ", "www.ghibli.jp", 1, now - 20 * DAY),
        )
        HistoryScreen(entries, now, onOpen = {}, onDelete = {}, onClear = {}, onBack = {})
    }

    @Test
    fun bookmarks() = capture("bookmarks") {
        val bookmarks = listOf(
            Bookmark(1, "https://pt.wikipedia.org/", "Wikipédia", now, pinned = true),
            Bookmark(2, "https://developer.mozilla.org/", "MDN Web Docs", now, pinned = false),
            Bookmark(3, "https://www.aozora.gr.jp/", "青空文庫", now, pinned = false),
        )
        BookmarksScreen(bookmarks, onOpen = {}, onDelete = {}, onRename = { _, _ -> }, onTogglePin = {}, onBack = {})
    }

    @Test
    fun downloads() = capture("downloads") {
        val downloads = listOf(
            DownloadRecord(1, "https://a.example/manual.pdf", "manual-zeno.pdf", "application/pdf", 4_200_000, 2_900_000, DownloadStatus.Running, null, now, null),
            DownloadRecord(2, "https://a.example/foto.jpg", "kyoto-ryoanji.jpg", "image/jpeg", 1_800_000, 1_800_000, DownloadStatus.Completed, "content://x/2", now - HOUR, now - HOUR),
            DownloadRecord(3, "https://a.example/big.zip", "dados.zip", "application/zip", null, 0, DownloadStatus.Failed, null, now - DAY, null),
        )
        DownloadsScreen(downloads, now, onOpen = {}, onDelete = {}, onCancel = {}, onClear = {}, onBack = {})
    }

    @Test
    fun extensions() = capture("extensions") {
        val ublock = ExtensionInfo(
            id = "uBlock0@raymondhill.net",
            name = "uBlock Origin",
            description = "Finally, an efficient blocker. Easy on CPU and memory.",
            version = "1.66.4",
            enabled = true,
            allowedInPrivate = true,
            optionsUrl = "moz-extension://x/dashboard.html",
            homepageUrl = null,
            isBuiltIn = false,
            permissions = emptyList(),
            origins = emptyList(),
        )
        ExtensionsScreen(listOf(ublock), installing = setOf("https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi"), actions = NoExtensionsActions)
    }

    @Test
    fun crashed() = capture("crashed", dark = true) { CrashedTab(onReload = {}) }

    @Test
    fun sitePanel() = capture("site_panel") {
        SheetOverPage {
            SitePanelContent(
                info = SiteInfo(
                    url = "https://www.openstreetmap.org/",
                    secure = true,
                    security = SecurityInfo(secure = true, host = "www.openstreetmap.org", issuer = "Let's Encrypt"),
                    trackersBlocked = 3,
                    protectionAvailable = true,
                    protectionException = false,
                ),
                permissions = listOf(
                    SitePermission("https://www.openstreetmap.org", PermissionKind.Location, allowed = true),
                    SitePermission("https://www.openstreetmap.org", PermissionKind.PersistentStorage, allowed = false),
                ),
                actions = NoSitePanelActions,
            )
        }
    }

    @Test
    fun sitePanelInsecure() = capture("site_panel_insecure", dark = true) {
        SheetOverPage {
            SitePanelContent(
                info = SiteInfo(
                    url = "http://neverssl.com/",
                    secure = false,
                    security = SecurityInfo(secure = false),
                    trackersBlocked = 0,
                    protectionAvailable = true,
                    protectionException = true,
                ),
                permissions = emptyList(),
                actions = NoSitePanelActions,
            )
        }
    }

    @Test
    fun sitePermissions() = capture("site_permissions") {
        SitePermissionsScreen(
            permissions = listOf(
                SitePermission("https://www.openstreetmap.org", PermissionKind.Location, allowed = true),
                SitePermission("https://www.openstreetmap.org", PermissionKind.PersistentStorage, allowed = false),
                SitePermission("https://music.example", PermissionKind.ProtectedMedia, allowed = true),
                SitePermission("https://news.example", PermissionKind.Notifications, allowed = false),
                SitePermission("https://bank.example", PermissionKind.Trackers, allowed = true),
            ),
            onChange = { _, _ -> },
            onBack = {},
        )
    }

    @Test
    fun sitePermissionsEmpty() = capture("site_permissions_empty", dark = true) {
        SitePermissionsScreen(permissions = emptyList(), onChange = { _, _ -> }, onBack = {})
    }

    @Test
    fun permissionPrompt() = capture("permission_prompt") {
        DialogOverPage {
            PermissionQuestion(PermissionKind.Location, "openstreetmap.org", canRemember = true, remember = true, onRememberChange = {})
        }
    }

    @Composable
    private fun Home(privateMode: Boolean, withCard: Boolean) {
        BrowserFrame(tab = null, tabCount = if (privateMode) 1 else 5, privateMode = privateMode) {
            HomeScreen(
                privateMode = privateMode,
                shortcuts = shortcuts,
                onOpen = {},
                onOpenPrivate = {},
                onRemove = {},
                animate = false,
                footer = if (withCard) {
                    { UblockCard(installing = false, onInstall = {}, onDismiss = {}) }
                } else {
                    null
                },
            )
        }
    }

    /** A bottom sheet as it sits over a page. */
    @Composable
    private fun SheetOverPage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            FakePage()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Zeno.colors.scrim),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(ZenoShapes.sheet)
                    .background(Zeno.colors.paperRaised),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally)) { SheetHandle() }
                content()
            }
        }
    }

    /** A dialog's body and buttons, drawn in place (real dialogs open in their own window). */
    @Composable
    private fun DialogOverPage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            FakePage()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Zeno.colors.scrim),
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp)
                    .fillMaxWidth()
                    .clip(ZenoShapes.large)
                    .background(Zeno.colors.paperRaised)
                    .padding(24.dp),
            ) {
                content()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(stringResource(R.string.prompt_block), style = Zeno.type.label, color = Zeno.colors.inkSoft, modifier = Modifier.padding(12.dp))
                    Text(stringResource(R.string.prompt_allow), style = Zeno.type.label, color = Zeno.colors.accentInk, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }

    @Composable
    private fun MenuOverPage(dark: Boolean) {
        SheetOverPage {
            MenuContent(
                page = MenuPage(
                    hasPage = true,
                    canGoBack = true,
                    canGoForward = false,
                    loading = false,
                    bookmarked = !dark,
                    desktopMode = false,
                    translated = false,
                    trackersBlocked = 14,
                    reader = if (dark) ReaderState.Active else ReaderState.Available,
                ),
                extensions = listOf(MenuExtension("uBlock0@raymondhill.net", "uBlock Origin", "23")),
                showQuit = dark,
                actions = NoMenuActions,
            )
        }
    }

    private fun capture(
        name: String,
        dark: Boolean = false,
        privateMode: Boolean = false,
        tall: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("build/outputs/roborazzi/screen_$name.png") {
            ZenoTheme(dark = dark, privateMode = privateMode) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (tall) 1500.dp else 891.dp)
                        .background(Zeno.colors.paper),
                ) {
                    content()
                }
            }
        }
    }

    private companion object {
        const val READER_URL = "moz-extension://reader/reader.html?url=https%3A%2F%2Fwww.caderno.example%2Fryoanji"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}

/** The browser's frame as it looks in use: page above, toolbar below. */
@Composable
private fun BrowserFrame(
    tab: TabState?,
    tabCount: Int,
    privateMode: Boolean = false,
    readerOriginal: (String) -> String? = { null },
    page: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page() }
        Toolbar(
            display = addressDisplay(tab, SearchEngines.DuckDuckGo, readerOriginal),
            tabCount = tabCount,
            privateMode = privateMode,
            onAddressClick = {},
            onReload = {},
            onStop = {},
            onTabsClick = {},
            onMenuClick = {},
        )
    }
}

/** A sketch of a web page: headline, paragraphs, a picture. */
@Composable
private fun FakePage() {
    val ink = Color(0xFF2B2B2B)
    val text = Color(0xFFD9D9D9)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(20.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(26.dp)
                .background(ink),
        )
        Spacer(Modifier.height(18.dp))
        repeat(6) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i == 5) 0.55f else 1f)
                    .height(11.dp)
                    .background(text),
            )
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Color(0xFFBCE2E8)),
        )
        Spacer(Modifier.height(18.dp))
        repeat(8) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i % 4 == 3) 0.7f else 1f)
                    .height(11.dp)
                    .background(text),
            )
            Spacer(Modifier.height(9.dp))
        }
    }
}

private object NoMenuActions : MenuActions {
    override fun back() = Unit
    override fun forward() = Unit
    override fun reload() = Unit
    override fun stop() = Unit
    override fun toggleBookmark() = Unit
    override fun share() = Unit
    override fun newTab(private: Boolean) = Unit
    override fun find() = Unit
    override fun translate() = Unit
    override fun toggleReader() = Unit
    override fun setDesktopMode(enabled: Boolean) = Unit
    override fun addToHome() = Unit
    override fun extension(id: String) = Unit
    override fun bookmarks() = Unit
    override fun history() = Unit
    override fun downloads() = Unit
    override fun extensions() = Unit
    override fun settings() = Unit
    override fun quit() = Unit
}

private object NoSettingsActions : SettingsActions {
    override fun update(transform: (UserSettings) -> UserSettings) = Unit
    override fun clearBrowsingData() = Unit
    override fun openUrl(url: String) = Unit
    override fun makeDefaultBrowser() = Unit
    override fun sitePermissions() = Unit
    override fun requestNotificationPermission() = Unit
    override fun back() = Unit
}

private object NoSitePanelActions : SitePanelActions {
    override fun setProtection(enabled: Boolean) = Unit
    override fun changePermission(permission: SitePermission, allowed: Boolean?) = Unit
    override fun clearSiteData() = Unit
}

private object NoExtensionsActions : ExtensionsActions {
    override fun install(url: String) = Unit
    override fun setEnabled(id: String, enabled: Boolean) = Unit
    override fun setAllowedInPrivate(id: String, allowed: Boolean) = Unit
    override fun remove(id: String) = Unit
    override fun openUrl(url: String) = Unit
    override fun back() = Unit
}
