package com.blackcocadastudios.zeno.ui.browser

import kotlin.math.min
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.blackcocadastudios.zeno.design.theme.ZenoMotion
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.search.SearchEngine
import com.blackcocadastudios.zeno.browser.search.SearchEngines
import com.blackcocadastudios.zeno.browser.state.TabState
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.design.component.Enso
import com.blackcocadastudios.zeno.design.component.ZenoPillButton
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.engine.PermissionKind
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.engine.TrackingProtection
import com.blackcocadastudios.zeno.engine.gecko.GeckoEngineView
import com.blackcocadastudios.zeno.ui.MainViewModel
import com.blackcocadastudios.zeno.ui.Screen
import com.blackcocadastudios.zeno.ui.common.MessageHost
import com.blackcocadastudios.zeno.ui.home.HomeScreen
import com.blackcocadastudios.zeno.ui.home.UblockCard
import com.blackcocadastudios.zeno.ui.menu.MenuActions
import com.blackcocadastudios.zeno.ui.menu.MenuExtension
import com.blackcocadastudios.zeno.ui.menu.MenuPage
import com.blackcocadastudios.zeno.ui.menu.MenuSheet
import com.blackcocadastudios.zeno.ui.prompts.AppPermissionHost
import com.blackcocadastudios.zeno.ui.prompts.PermissionHost
import com.blackcocadastudios.zeno.ui.prompts.PromptHost
import com.blackcocadastudios.zeno.ui.site.SiteInfo
import com.blackcocadastudios.zeno.ui.site.SitePanelActions
import com.blackcocadastudios.zeno.ui.site.SitePanelSheet
import com.blackcocadastudios.zeno.ui.tabs.TabsScreen
import com.blackcocadastudios.zeno.ui.toolbar.AddressDisplay
import com.blackcocadastudios.zeno.ui.toolbar.AddressEditor
import com.blackcocadastudios.zeno.ui.toolbar.AddressKind
import com.blackcocadastudios.zeno.ui.toolbar.FindBar
import com.blackcocadastudios.zeno.ui.toolbar.Toolbar
import kotlinx.coroutines.delay

/** uBlock Origin's add-on id, to know whether the home page should suggest it. */
private const val UBLOCK_ID = "uBlock0@raymondhill.net"

/** Time for a loaded page to settle (fonts, images) before its thumbnail is taken. */
private const val THUMBNAIL_DELAY_MILLIS = 1_200L

/** How far a page is pulled down to reload it. */
private val PULL_TO_RELOAD = 96.dp

/**
 * The browser: the page (or Zeno's home) above, the toolbar below, and every
 * overlay a page or the user can open. It stays composed under the other
 * screens so the page keeps its surface; [active] says whether it is in front.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(vm: MainViewModel, engineView: GeckoEngineView, active: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val installed by vm.container.engine.extensions.installed.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val colors = Zeno.colors

    val tab = state.selectedTab
    val privateMode = state.privateMode
    val content = tab?.content
    val showHome = tab?.url == null
    // Reading the generation makes this recompose whenever the tab gets a new renderer.
    val session = if (tab != null && tab.isLive && tab.rendererGeneration > 0 && !showHome) vm.controller.liveSession(tab.id) else null
    val fullscreen = content?.fullscreen == true
    val searchEngine = remember(settings.searchEngineId) { SearchEngines.byId(settings.searchEngineId) }
    // The keyboard's destination, not its current position: no toolbar flicker while it slides away.
    val keyboardShown = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
    val keyboardForPage = keyboardShown && !vm.editing
    val showBar = !fullscreen && (!keyboardForPage || vm.findOpen)
    val coverColor = colors.paper.toArgb()
    val density = LocalDensity.current

    // With "hide the bar while scrolling", the page extends under the bar, which
    // slides down as the page scrolls down and comes back when it scrolls up.
    val overlayBar = settings.hideToolbarOnScroll && !fullscreen
    val slidingBar = overlayBar && showBar && !showHome && !vm.findOpen && content?.crashed != true
    var barHeightPx by remember { mutableIntStateOf(with(density) { 62.dp.roundToPx() }) }
    var barOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val settling = remember { JobSlot() }

    // Pulling down a page that is at its top draws an ensō; let go once it closes to reload.
    var pull by remember { mutableFloatStateOf(0f) }
    val pullThreshold = with(density) { PULL_TO_RELOAD.toPx() }
    val pulling = remember { JobSlot() }

    // The bar comes back for every new page and new tab, and stays while it cannot slide.
    LaunchedEffect(tab?.id, tab?.url, slidingBar) {
        settling.job?.cancel()
        if (!slidingBar) barOffset = 0f else animate(barOffset, 0f, animationSpec = tween(ZenoMotion.QUICK)) { value, _ -> barOffset = value }
    }
    val pageShown = session != null && !fullscreen && !showHome
    DisposableEffect(engineView, pageShown, slidingBar, barHeightPx, tab?.id) {
        val tabId = tab?.id
        engineView.scrollListener = if (!pageShown) {
            null
        } else {
            object : GeckoEngineView.ScrollListener {
                override fun onScroll(dy: Float) {
                    if (!slidingBar) return
                    settling.job?.cancel()
                    barOffset = (barOffset + dy).coerceIn(0f, barHeightPx.toFloat())
                }

                override fun onPull(distance: Float) {
                    pulling.job?.cancel()
                    pull = distance
                }

                override fun onScrollEnd() {
                    if (pull > 0f) {
                        if (pull >= pullThreshold && tabId != null) vm.controller.reload(tabId)
                        pulling.job = scope.launch {
                            animate(pull, 0f, animationSpec = tween(ZenoMotion.STANDARD, easing = ZenoMotion.Calm)) { value, _ -> pull = value }
                        }
                    }
                    if (!slidingBar) return
                    val target = if (barOffset > barHeightPx / 2f) barHeightPx.toFloat() else 0f
                    settling.job = scope.launch {
                        animate(barOffset, target, animationSpec = tween(ZenoMotion.QUICK, easing = ZenoMotion.Calm)) { value, _ -> barOffset = value }
                    }
                }
            }
        }
        onDispose {
            engineView.scrollListener = null
            pull = 0f
        }
    }
    // Gecko needs the bar's height, and how much of it covers the page right now, to keep
    // fixed elements (cookie banners, chat buttons) above it.
    LaunchedEffect(engineView, overlayBar, barHeightPx) {
        engineView.setDynamicToolbarMaxHeight(if (overlayBar) barHeightPx else 0)
    }
    LaunchedEffect(engineView, overlayBar, showBar, barHeightPx) {
        snapshotFlow { barOffset }.collect { offset ->
            val covered = if (overlayBar && showBar) (barHeightPx - offset).roundToInt().coerceAtLeast(0) else 0
            engineView.setVerticalClipping(-covered)
        }
    }

    FullscreenEffect(activity, fullscreen, content?.mediaLandscape)

    // A picture for the tab grid once a page has loaded and painted.
    LaunchedEffect(tab?.id, content?.loading, session) {
        val id = tab?.id ?: return@LaunchedEffect
        if (session == null || content?.loading != false) return@LaunchedEffect
        delay(THUMBNAIL_DELAY_MILLIS)
        engineView.capture { bitmap -> bitmap?.let { vm.container.thumbnails.put(id, it) } }
    }

    // Back handlers: later ones win, so the most specific come last.
    BackHandler(enabled = active && !vm.editing && !vm.showTabs && !vm.findOpen) {
        if (!vm.onBrowserBack()) activity?.moveTaskToBack(true)
    }
    BackHandler(enabled = active && vm.findOpen && !vm.editing && !vm.showTabs) {
        vm.findOpen = false
        tab?.let { vm.controller.clearFind(it.id) }
    }
    BackHandler(enabled = active && vm.showTabs) { vm.showTabs = false }
    BackHandler(enabled = active && vm.editing) { vm.stopEditing() }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.paper),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (fullscreen) {
                        Modifier
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    },
                )
                // The page shrinks once, to the keyboard's final height, instead of on every frame of its animation.
                // While the address is being edited, the page stays as it is under the editor.
                .then(if (vm.editing) Modifier else Modifier.windowInsetsPadding(WindowInsets.imeAnimationTarget)),
        ) {
            val bar: @Composable (Modifier) -> Unit = { modifier ->
                if (vm.findOpen && tab != null) {
                    FindBar(
                        result = content?.find,
                        onSearch = { vm.controller.find(tab.id, it) },
                        onNext = { vm.controller.find(tab.id, it) },
                        onPrevious = { vm.controller.find(tab.id, it, backwards = true) },
                        onClose = {
                            vm.findOpen = false
                            vm.controller.clearFind(tab.id)
                        },
                        modifier = modifier,
                    )
                } else {
                    Toolbar(
                        display = addressDisplay(tab, searchEngine),
                        tabCount = state.tabs.count { it.isPrivate == privateMode },
                        privateMode = privateMode,
                        onAddressClick = vm::startEditing,
                        onReload = { tab?.let { vm.controller.reload(it.id) } },
                        onStop = { tab?.let { vm.controller.stop(it.id) } },
                        onTabsClick = {
                            if (tab != null && session != null) {
                                val id = tab.id
                                engineView.capture { bitmap -> bitmap?.let { vm.container.thumbnails.put(id, it) } }
                            }
                            vm.showTabs = true
                        },
                        onMenuClick = { vm.showMenu = true },
                        onSiteInfo = { vm.showSiteInfo = true },
                        modifier = modifier,
                    )
                }
            }
            // Home and the crash view keep clear of a bar that floats over them.
            val underBar = if (overlayBar && showBar) with(density) { barHeightPx.toDp() } else 0.dp
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (overlayBar) Modifier.navigationBarsPadding() else Modifier)
                    .clipToBounds(),
            ) {
                AndroidView(
                    factory = { engineView.apply { (parent as? ViewGroup)?.removeView(this) } },
                    update = { view -> view.render(session, coverColor) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (showHome) {
                    val showUblock = !privateMode && !settings.onboardingDone && installed.none { it.id == UBLOCK_ID }
                    val footer: (@Composable () -> Unit)? = if (showUblock) {
                        {
                            UblockCard(
                                installing = MainViewModel.UBLOCK_URL in vm.installing,
                                onInstall = {
                                    vm.requestInstallUblock()
                                    vm.updateSettings { it.copy(onboardingDone = true) }
                                },
                                onDismiss = { vm.updateSettings { it.copy(onboardingDone = true) } },
                            )
                        }
                    } else {
                        null
                    }
                    HomeScreen(
                        privateMode = privateMode,
                        shortcuts = shortcuts,
                        onOpen = { vm.openUrl(it.url) },
                        onOpenPrivate = { vm.openUrl(it.url, newTab = true, private = true) },
                        onRemove = vm::removeShortcut,
                        footer = footer,
                        modifier = Modifier.padding(bottom = underBar),
                    )
                }
                if (tab != null && content?.crashed == true) {
                    CrashedTab(onReload = { vm.controller.reload(tab.id) }, modifier = Modifier.padding(bottom = underBar))
                }
                PullIndicator(pull = { pull }, threshold = pullThreshold, modifier = Modifier.align(Alignment.TopCenter))
                if (overlayBar && showBar) {
                    bar(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { barHeightPx = it.height }
                            .graphicsLayer { translationY = barOffset },
                    )
                }
            }
            if (!overlayBar && showBar) bar(Modifier.navigationBarsPadding())
        }

        if (active && vm.editing) {
            AddressEditor(
                initialText = vm.editableAddress(),
                privateMode = privateMode,
                suggest = vm::suggest,
                searchUrl = searchEngine::searchUrl,
                onSubmit = vm::submit,
                onOpenUrl = { vm.openUrl(it) },
                onDismiss = vm::stopEditing,
            )
        }

        if (active && vm.showTabs) {
            val thumbnails = vm.container.thumbnails
            TabsScreen(
                tabs = state.tabs,
                selectedTabId = state.selectedTabId,
                privateMode = privateMode,
                thumbnail = { id ->
                    // Reading the version subscribes to changes of this thumbnail.
                    thumbnails.versions[id]
                    thumbnails.get(id)
                },
                onSelect = vm::selectTab,
                onClose = vm::closeTab,
                onCloseAll = vm::closeAllTabs,
                onNewTab = vm::newTab,
                onDismiss = { vm.showTabs = false },
            )
        }

        if (active) {
            // Above the toolbar (following it as it slides), or above the tab grid's button.
            MessageHost(
                vm.messages,
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.imeAnimationTarget))
                    .padding(bottom = if (vm.showTabs) 88.dp else 0.dp)
                    .offset {
                        val lift = if (!vm.showTabs && showBar) (barHeightPx - barOffset).roundToInt().coerceAtLeast(0) else 0
                        IntOffset(0, -lift)
                    },
            )
        }
    }

    if (!active) return

    if (vm.showMenu) {
        val url = tab?.url
        val bookmarked by remember(url) { vm.isBookmarked(url) }.collectAsStateWithLifecycle(initialValue = false)
        val extensionActions by vm.container.engine.extensions.actions.collectAsStateWithLifecycle()
        MenuSheet(
            page = MenuPage(
                hasPage = url != null,
                canGoBack = content?.canGoBack == true,
                canGoForward = content?.canGoForward == true,
                loading = content?.loading == true,
                bookmarked = bookmarked,
                desktopMode = tab?.desktopMode == true,
                translated = content?.translation?.isTranslated == true,
                trackersBlocked = content?.trackersBlocked ?: 0,
            ),
            extensions = extensionActions.mapNotNull { (id, action) ->
                val info = installed.firstOrNull { it.id == id && it.enabled } ?: return@mapNotNull null
                MenuExtension(id, action.title?.takeIf { it.isNotBlank() } ?: info.name, action.badgeText?.takeIf { it.isNotBlank() })
            },
            showQuit = settings.clearOnExit || state.privateTabs.isNotEmpty(),
            actions = menuActions(vm, tab, LocalContext.current, activity),
            onDismiss = { vm.showMenu = false },
        )
    }

    if (tab == null) return

    val siteUrl = tab.url
    if (vm.showSiteInfo && siteUrl != null && content != null) {
        var permissions by remember(siteUrl) { mutableStateOf(emptyList<SitePermission>()) }
        LaunchedEffect(siteUrl, tab.isPrivate, vm.sitePermissionsVersion) {
            // Tracking protection has its own switch in the panel.
            permissions = vm.sitePermissions(siteUrl, tab.isPrivate).filter { it.kind != PermissionKind.Trackers }
        }
        SitePanelSheet(
            info = SiteInfo(
                url = siteUrl,
                secure = addressDisplay(tab, searchEngine).kind != AddressKind.Insecure,
                security = content.security,
                trackersBlocked = content.trackersBlocked,
                protectionAvailable = settings.trackingProtection != TrackingProtection.Off,
                protectionException = content.protectionException,
            ),
            permissions = permissions,
            actions = remember(vm) {
                object : SitePanelActions {
                    override fun setProtection(enabled: Boolean) = vm.setProtection(enabled)

                    override fun changePermission(permission: SitePermission, allowed: Boolean?) =
                        vm.setSitePermission(permission, allowed)

                    override fun clearSiteData() = vm.clearSiteData()
                }
            },
            onDismiss = { vm.showSiteInfo = false },
        )
    }

    val context = LocalContext.current
    val copied = stringResource(R.string.context_copied)
    val deniedBySystem = stringResource(R.string.permission_denied_system)

    content?.contextMenu?.let { hit ->
        ContextMenuSheet(
            hit = hit,
            privateMode = tab.isPrivate,
            actions = object : ContextMenuActions {
                override fun openInNewTab(url: String, private: Boolean) = vm.openInBackground(url, private)

                override fun copy(text: String) {
                    copyToClipboard(context, text)
                    // Android 13+ confirms copies itself.
                    if (Build.VERSION.SDK_INT < 33) vm.message(copied)
                }

                override fun share(url: String) = shareText(context, url, null)

                override fun download(url: String) = vm.download(url)
            },
            onDismiss = { vm.controller.consumeContextMenu(tab.id) },
        )
    }

    val origin = tab.url?.let(UrlDisplay::shortHost).orEmpty()
    content?.prompts?.firstOrNull()?.let { prompt ->
        PromptHost(prompt, origin) { vm.controller.consumePrompt(tab.id, it) }
    }
    content?.permissionRequests?.firstOrNull()?.let { request ->
        PermissionHost(request) { vm.controller.consumePermissionRequest(tab.id, it) }
    }
    content?.appPermissionRequests?.firstOrNull()?.let { request ->
        AppPermissionHost(request, onDenied = { vm.message(deniedBySystem) }) { vm.controller.consumeAppPermissionRequest(tab.id, it) }
    }
}

/** What the address pill says about [tab]: search terms, the host, or nothing (home). */
internal fun addressDisplay(tab: TabState?, engine: SearchEngine): AddressDisplay {
    val url = tab?.url ?: return AddressDisplay(null, AddressKind.Home, loading = false, progress = 0, canReload = false)
    val content = tab.content
    val terms = UrlDisplay.searchTerms(url, engine)
    val https = url.startsWith("https://", ignoreCase = true)
    val kind = when {
        terms != null -> AddressKind.Search
        // While an https page loads its certificate is not known yet; do not flash the open lock.
        https && (content.security.secure || content.loading) -> AddressKind.Secure
        https || url.startsWith("http://", ignoreCase = true) -> AddressKind.Insecure
        else -> AddressKind.Internal
    }
    return AddressDisplay(
        text = terms ?: UrlDisplay.shortHost(url),
        kind = kind,
        loading = content.loading,
        progress = content.progress,
        canReload = true,
    )
}

private fun menuActions(vm: MainViewModel, tab: TabState?, context: Context, activity: Activity?): MenuActions = object : MenuActions {
    private fun close() {
        vm.showMenu = false
    }

    private fun onTab(action: (String) -> Unit) {
        tab?.let { action(it.id) }
        close()
    }

    override fun back() = onTab(vm.controller::goBack)

    override fun forward() = onTab(vm.controller::goForward)

    override fun reload() = onTab { vm.controller.reload(it) }

    override fun stop() = onTab(vm.controller::stop)

    override fun toggleBookmark() {
        vm.toggleBookmark()
        close()
    }

    override fun share() {
        tab?.url?.let { shareText(context, it, tab.title) }
        close()
    }

    override fun newTab(private: Boolean) = vm.newTab(private)

    override fun find() {
        close()
        vm.findOpen = true
    }

    override fun translate() {
        close()
        vm.translate()
    }

    override fun setDesktopMode(enabled: Boolean) = onTab { vm.controller.setDesktopMode(it, enabled) }

    override fun addToHome() = vm.addToHome()

    override fun extension(id: String) {
        close()
        vm.container.engine.extensions.clickAction(id)
    }

    override fun bookmarks() = vm.open(Screen.Bookmarks)

    override fun history() = vm.open(Screen.History)

    override fun downloads() = vm.open(Screen.Downloads)

    override fun extensions() = vm.open(Screen.Extensions)

    override fun settings() = vm.open(Screen.Settings)

    override fun quit() {
        close()
        vm.quit { activity?.finishAndRemoveTask() }
    }
}

/** The renderer of this tab stopped. A broken ensō, and a way back. */
@Composable
internal fun CrashedTab(onReload: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Zeno.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(horizontal = Ma.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Enso(Modifier.size(64.dp), color = colors.inkFaint, drawProgress = 0.72f)
        Spacer(Modifier.height(Ma.xl))
        Text(stringResource(R.string.crash_title), style = Zeno.type.mincho.copy(fontSize = 22.sp), color = colors.ink)
        Spacer(Modifier.height(Ma.s))
        Text(
            stringResource(R.string.crash_body),
            style = Zeno.type.bodySmall,
            color = colors.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(Ma.xl))
        ZenoPillButton(stringResource(R.string.crash_reload), onReload, filled = true)
        Spacer(Modifier.weight(1.3f))
    }
}

/** Fullscreen video: hide the system bars, follow the video's orientation, keep the screen on. */
@Composable
private fun FullscreenEffect(activity: Activity?, fullscreen: Boolean, landscape: Boolean?) {
    val view = LocalView.current
    val window = activity?.window ?: return
    DisposableEffect(fullscreen) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        view.keepScreenOn = fullscreen
        onDispose {
            if (fullscreen) controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
    LaunchedEffect(fullscreen, landscape) {
        activity.requestedOrientation = when {
            fullscreen && landscape == true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            fullscreen && landscape == false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("url", text))
}

internal fun shareText(context: Context, text: String, title: String?) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .apply { if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title) }
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_via)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Holds an animation's job outside of composition state. */
private class JobSlot {
    var job: Job? = null
}

/**
 * The pull-to-reload mark: an ensō drawn as the page is pulled, closed when
 * letting go reloads. Reads [pull] in its own scope so only it redraws.
 */
@Composable
private fun PullIndicator(pull: () -> Float, threshold: Float, modifier: Modifier = Modifier) {
    val distance = pull()
    if (distance <= 0f) return
    val progress = (distance / threshold).coerceIn(0f, 1f)
    val colors = Zeno.colors
    Box(
        modifier
            .offset { IntOffset(0, (min(distance, threshold * 1.4f) * 0.55f).roundToInt()) }
            .graphicsLayer { alpha = progress }
            .size(44.dp)
            .shadow(4.dp, CircleShape)
            .background(colors.paperRaised, CircleShape)
            .padding(9.dp),
    ) {
        Enso(Modifier.fillMaxSize(), color = if (progress >= 1f) colors.accent else colors.inkSoft, drawProgress = progress)
    }
}
