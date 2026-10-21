package com.blackcocadastudios.zeno.engine

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** WebExtensions (uBlock Origin and friends). */
interface ExtensionController {
    /** Installed extensions, refreshed after every change. */
    val installed: StateFlow<List<ExtensionInfo>>

    /** Toolbar actions (title, badge) per extension id, for the tab in front. */
    val actions: StateFlow<Map<String, ExtensionAction>>

    /** Popups an extension wants to show (after a click on its action). */
    val popups: SharedFlow<ExtensionPopup>

    /** Asks the user to approve permissions before installing. Set by the app. */
    var installPrompt: (suspend (ExtensionInstallRequest) -> Boolean)?

    /** Lets extensions open, close and focus tabs. Set by the app. */
    var tabs: ExtensionTabs?

    /** Installs from an `https://` XPI url (e.g. addons.mozilla.org). */
    suspend fun install(url: String): Result<ExtensionInfo>

    suspend fun uninstall(id: String): Result<Unit>

    suspend fun setEnabled(id: String, enabled: Boolean): Result<ExtensionInfo>

    suspend fun setAllowedInPrivate(id: String, allowed: Boolean): Result<ExtensionInfo>

    suspend fun refresh()

    /** Clicks the extension's toolbar button; a popup, if any, arrives on [popups]. */
    fun clickAction(id: String)

    /** Tells extensions which tab is in front (for `tabs.onActivated`). */
    fun setActiveSession(session: EngineSession)
}

/** The app side of extension tab requests. */
interface ExtensionTabs {
    /** Show [session] (already created by the engine) in a new tab. Return false to refuse. */
    fun openTab(session: EngineSession, url: String, private: Boolean): Boolean

    /** Close the tab showing [session]. Return false if there is none. */
    fun closeTab(session: EngineSession): Boolean

    /** Bring the tab showing [session] to the front. */
    fun selectTab(session: EngineSession)
}

data class ExtensionInfo(
    val id: String,
    val name: String,
    val description: String?,
    val version: String?,
    val enabled: Boolean,
    val allowedInPrivate: Boolean,
    val optionsUrl: String?,
    val homepageUrl: String?,
    val isBuiltIn: Boolean,
    val permissions: List<String>,
    val origins: List<String>,
)

data class ExtensionAction(
    val title: String?,
    val badgeText: String?,
    val enabled: Boolean,
)

data class ExtensionInstallRequest(
    val name: String,
    val permissions: List<String>,
    val origins: List<String>,
)

/** An extension popup rendered by its own session; close it when done. */
data class ExtensionPopup(
    val extensionId: String,
    val title: String,
    val session: EngineSession,
)
