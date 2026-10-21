package com.blackcocadastudios.zeno.browser.state

import com.blackcocadastudios.zeno.engine.AppPermissionRequest
import com.blackcocadastudios.zeno.engine.EngineSessionState
import com.blackcocadastudios.zeno.engine.FindResult
import com.blackcocadastudios.zeno.engine.HitResult
import com.blackcocadastudios.zeno.engine.PermissionRequest
import com.blackcocadastudios.zeno.engine.PromptRequest
import com.blackcocadastudios.zeno.engine.SecurityInfo
import com.blackcocadastudios.zeno.engine.TranslationState

/** Everything the interface needs to draw the browser. Immutable; replaced on every change. */
data class BrowserState(
    val tabs: List<TabState> = emptyList(),
    val selectedTabId: String? = null,
    /** Mode of the home page and of new tabs when no tab is selected. */
    val privateMode: Boolean = false,
) {
    val selectedTab: TabState? get() = tabs.firstOrNull { it.id == selectedTabId }

    fun tab(id: String): TabState? = tabs.firstOrNull { it.id == id }

    val normalTabs: List<TabState> get() = tabs.filter { !it.isPrivate }

    val privateTabs: List<TabState> get() = tabs.filter { it.isPrivate }
}

data class TabState(
    val id: String,
    /** Null while the tab shows Zeno's home page (a new, empty tab). */
    val url: String? = null,
    val title: String = "",
    val isPrivate: Boolean = false,
    /** Tab that opened this one; back from the first page returns there. */
    val parentId: String? = null,
    val origin: TabOrigin = TabOrigin.User,
    val createdAt: Long = 0,
    val lastAccessedAt: Long = 0,
    val desktopMode: Boolean = false,
    /** Latest engine snapshot; used to restore the tab after suspension or restart. */
    val engineState: EngineSessionState? = null,
    /** False while the tab is suspended (no renderer in memory). */
    val isLive: Boolean = false,
    val content: ContentState = ContentState(),
)

enum class TabOrigin {
    /** Opened by the user (new tab, typed address). */
    User,

    /** Opened by a page (`target=_blank`, `window.open`). */
    Link,

    /** Opened by another app; back on the first page returns to it. */
    External,

    /** Opened by an extension (dashboard, options page). */
    Extension,

    /** Restored from a previous run. */
    Restored,
}

/** Transient page state. Reset when the renderer is recreated. */
data class ContentState(
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val security: SecurityInfo = SecurityInfo.Unknown,
    val trackersBlocked: Int = 0,
    val fullscreen: Boolean = false,
    /** Video in fullscreen; true when it is wider than tall. */
    val mediaLandscape: Boolean? = null,
    val find: FindResult? = null,
    val prompts: List<PromptRequest> = emptyList(),
    val permissionRequests: List<PermissionRequest> = emptyList(),
    val appPermissionRequests: List<AppPermissionRequest> = emptyList(),
    val contextMenu: HitResult? = null,
    val translation: TranslationState? = null,
    val crashed: Boolean = false,
)
