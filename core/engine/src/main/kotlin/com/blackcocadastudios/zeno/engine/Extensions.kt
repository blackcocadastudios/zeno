package com.blackcocadastudios.zeno.engine

import kotlinx.coroutines.flow.StateFlow

/** WebExtensions (uBlock Origin and friends). */
interface ExtensionController {
    /** Installed extensions, refreshed after every change. */
    val installed: StateFlow<List<ExtensionInfo>>

    /** Toolbar actions (title, badge) per extension id, for the active tab. */
    val actions: StateFlow<Map<String, ExtensionAction>>

    /** Asks the user to approve permissions before installing. Set by the app. */
    var installPrompt: (suspend (ExtensionInstallRequest) -> Boolean)?

    /** Opens tabs that extensions create (dashboards, options pages). Set by the app. */
    var tabOpener: ((url: String, private: Boolean) -> EngineSession?)?

    /** Installs from an `https://` XPI url (e.g. addons.mozilla.org). */
    suspend fun install(url: String): Result<ExtensionInfo>

    suspend fun uninstall(id: String): Result<Unit>

    suspend fun setEnabled(id: String, enabled: Boolean): Result<ExtensionInfo>

    suspend fun setAllowedInPrivate(id: String, allowed: Boolean): Result<ExtensionInfo>

    suspend fun refresh()

    /**
     * Clicks the extension's toolbar button. If it has a popup, the returned
     * session renders it; otherwise null.
     */
    suspend fun clickAction(id: String): EngineSession?

    /** Tells extensions which tab is in front (for `tabs.onActivated`). */
    fun setActiveSession(session: EngineSession)
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
