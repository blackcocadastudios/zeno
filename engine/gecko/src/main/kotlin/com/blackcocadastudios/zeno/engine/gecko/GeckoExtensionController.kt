package com.blackcocadastudios.zeno.engine.gecko

import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.ExtensionAction
import com.blackcocadastudios.zeno.engine.ExtensionController
import com.blackcocadastudios.zeno.engine.ExtensionInfo
import com.blackcocadastudios.zeno.engine.ExtensionInstallRequest
import com.blackcocadastudios.zeno.engine.ExtensionPopup
import com.blackcocadastudios.zeno.engine.ExtensionTabs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** WebExtensions through GeckoView's controller. Main thread only. */
class GeckoExtensionController internal constructor(
    private val engine: GeckoEngine,
) : ExtensionController {

    private val _installed = MutableStateFlow<List<ExtensionInfo>>(emptyList())
    override val installed: StateFlow<List<ExtensionInfo>> = _installed.asStateFlow()

    private val _actions = MutableStateFlow<Map<String, ExtensionAction>>(emptyMap())
    override val actions: StateFlow<Map<String, ExtensionAction>> = _actions.asStateFlow()

    private val _popups = MutableSharedFlow<ExtensionPopup>(extraBufferCapacity = 2)
    override val popups: SharedFlow<ExtensionPopup> = _popups.asSharedFlow()

    override var installPrompt: (suspend (ExtensionInstallRequest) -> Boolean)? = null
    override var tabs: ExtensionTabs? = null

    private val extensions = LinkedHashMap<String, WebExtension>()
    private val defaultActions = HashMap<String, WebExtension.Action>()
    private val sessionActions = HashMap<GeckoEngineSession, HashMap<String, WebExtension.Action>>()
    private var activeSession: GeckoEngineSession? = null
    private var started = false

    private val controller: WebExtensionController get() = engine.runtime.webExtensionController

    internal fun start() {
        if (started) return
        started = true
        controller.promptDelegate = InstallPrompts()
        controller.setAddonManagerDelegate(AddonEvents())
        engine.scope.launch { refresh() }
    }

    override suspend fun refresh() {
        start()
        val list = runCatching { controller.list().await() }.getOrNull() ?: return
        val ids = list.map { it.id }.toSet()
        extensions.keys.retainAll(ids)
        defaultActions.keys.retainAll(ids)
        list.forEach(::register)
        publish()
    }

    override suspend fun install(url: String): Result<ExtensionInfo> = runCatching {
        start()
        val extension = controller.install(url, WebExtensionController.INSTALLATION_METHOD_MANAGER).await()
            ?: error("Installation returned nothing")
        register(extension)
        publish()
        extension.toInfo()
    }

    override suspend fun uninstall(id: String): Result<Unit> = runCatching {
        val extension = extensions[id] ?: error("Unknown extension $id")
        controller.uninstall(extension).await()
        extensions.remove(id)
        defaultActions.remove(id)
        sessionActions.values.forEach { it.remove(id) }
        publish()
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): Result<ExtensionInfo> = runCatching {
        val extension = extensions[id] ?: error("Unknown extension $id")
        val updated = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER).await()
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER).await()
        } ?: error("No result")
        register(updated)
        publish()
        updated.toInfo()
    }

    override suspend fun setAllowedInPrivate(id: String, allowed: Boolean): Result<ExtensionInfo> = runCatching {
        val extension = extensions[id] ?: error("Unknown extension $id")
        val updated = controller.setAllowedInPrivateBrowsing(extension, allowed).await() ?: error("No result")
        register(updated)
        publish()
        updated.toInfo()
    }

    override fun clickAction(id: String) {
        val action = activeSession?.let { sessionActions[it]?.get(id) } ?: defaultActions[id] ?: return
        action.click()
    }

    override fun setActiveSession(session: EngineSession) {
        val gecko = session as? GeckoEngineSession ?: return
        if (gecko == activeSession) return
        val previous = activeSession
        activeSession = gecko
        if (started) {
            previous?.let { runCatching { controller.setTabActive(it.geckoSession, false) } }
            runCatching { controller.setTabActive(gecko.geckoSession, true) }
        }
        publishActions()
    }

    internal fun onSessionCreated(session: GeckoEngineSession) {
        extensions.values.forEach { attach(it, session) }
    }

    internal fun onSessionClosed(session: GeckoEngineSession) {
        sessionActions.remove(session)
        if (activeSession == session) activeSession = null
    }

    private fun register(extension: WebExtension) {
        extensions[extension.id] = extension
        extension.setActionDelegate(DefaultActions(extension.id))
        extension.setTabDelegate(NewTabs())
        engine.sessions().forEach { attach(extension, it) }
    }

    private fun attach(extension: WebExtension, session: GeckoEngineSession) {
        val sessionController = session.geckoSession.webExtensionController
        sessionController.setActionDelegate(extension, SessionActions(extension.id, session))
        sessionController.setTabDelegate(extension, SessionTabs(session))
    }

    private fun publish() {
        _installed.value = extensions.values.map { it.toInfo() }.sortedBy { it.name.lowercase() }
        publishActions()
    }

    private fun publishActions() {
        val overrides = activeSession?.let { sessionActions[it] }.orEmpty()
        _actions.value = defaultActions.mapNotNull { (id, default) ->
            val extension = extensions[id] ?: return@mapNotNull null
            if (!extension.metaData.enabled) return@mapNotNull null
            val action = overrides[id]?.withDefault(default) ?: default
            id to ExtensionAction(
                title = action.title ?: extension.metaData.name,
                badgeText = action.badgeText?.takeIf { it.isNotBlank() },
                enabled = action.enabled != false,
            )
        }.toMap()
    }

    private fun openPopup(extension: WebExtension): GeckoResult<GeckoSession> {
        val popup = engine.createSession(private = false) as GeckoEngineSession
        _popups.tryEmit(ExtensionPopup(extension.id, extension.metaData.name ?: extension.id, popup))
        return GeckoResult.fromValue(popup.geckoSession)
    }

    private fun ours(session: GeckoSession): GeckoEngineSession? =
        engine.sessions().firstOrNull { it.geckoSession == session }

    private inner class DefaultActions(private val id: String) : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            record(session, action)
        }

        override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            record(session, action)
        }

        private fun record(session: GeckoSession?, action: WebExtension.Action) {
            if (session == null) {
                defaultActions[id] = action
            } else {
                ours(session)?.let { sessionActions.getOrPut(it) { HashMap() }[id] = action }
            }
            publishActions()
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            openPopup(extension)

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            openPopup(extension)
    }

    private inner class SessionActions(
        private val id: String,
        private val session: GeckoEngineSession,
    ) : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            sessionActions.getOrPut(this.session) { HashMap() }[id] = action
            if (this.session == activeSession) publishActions()
        }

        override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) =
            onBrowserAction(extension, session, action)

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            openPopup(extension)

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            openPopup(extension)
    }

    private inner class NewTabs : WebExtension.TabDelegate {
        override fun onNewTab(source: WebExtension, createDetails: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
            val handler = tabs ?: return null
            val session = engine.createUnopenedSession(private = false)
            val url = createDetails.url ?: "about:blank"
            if (!handler.openTab(session, url, private = false)) {
                session.close()
                return null
            }
            return GeckoResult.fromValue(session.geckoSession)
        }

        override fun onOpenOptionsPage(source: WebExtension) {
            val url = source.metaData.optionsPageUrl ?: return
            val handler = tabs ?: return
            val session = engine.createSession(private = false)
            if (handler.openTab(session, url, private = false)) session.load(url) else session.close()
        }
    }

    private inner class SessionTabs(private val session: GeckoEngineSession) : WebExtension.SessionTabDelegate {
        override fun onCloseTab(source: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> {
            val closed = tabs?.closeTab(this.session) == true
            return if (closed) GeckoResult.allow() else GeckoResult.deny()
        }

        override fun onUpdateTab(
            extension: WebExtension,
            session: GeckoSession,
            details: WebExtension.UpdateTabDetails,
        ): GeckoResult<AllowOrDeny> {
            if (details.active == true) tabs?.selectTab(this.session)
            return GeckoResult.allow()
        }
    }

    private inner class InstallPrompts : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse>? {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            engine.scope.launch {
                val approved = ask(extension, permissions, origins)
                result.complete(WebExtension.PermissionPromptResponse(approved, false, false))
            }
            return result
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<String>,
            newOrigins: Array<String>,
            newDataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny>? {
            if (newPermissions.isEmpty() && newOrigins.isEmpty()) return GeckoResult.allow()
            return decide(extension, newPermissions, newOrigins)
        }

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny>? = decide(extension, permissions, origins)

        private fun decide(extension: WebExtension, permissions: Array<String>, origins: Array<String>): GeckoResult<AllowOrDeny> {
            val result = GeckoResult<AllowOrDeny>()
            engine.scope.launch {
                result.complete(if (ask(extension, permissions, origins)) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            }
            return result
        }

        private suspend fun ask(extension: WebExtension, permissions: Array<String>, origins: Array<String>): Boolean {
            val prompt = installPrompt ?: return false
            val request = ExtensionInstallRequest(
                name = extension.metaData.name ?: extension.id,
                permissions = permissions.toList(),
                origins = origins.toList(),
            )
            return runCatching { prompt(request) }.getOrDefault(false)
        }
    }

    private inner class AddonEvents : WebExtensionController.AddonManagerDelegate {
        private fun changed() {
            engine.scope.launch { refresh() }
        }

        override fun onInstalled(extension: WebExtension) = changed()

        override fun onUninstalled(extension: WebExtension) = changed()

        override fun onEnabled(extension: WebExtension) = changed()

        override fun onDisabled(extension: WebExtension) = changed()

        override fun onReady(extension: WebExtension) = changed()
    }
}

private fun WebExtension.toInfo() = ExtensionInfo(
    id = id,
    name = metaData.name ?: id,
    description = metaData.description,
    version = metaData.version,
    enabled = metaData.enabled,
    allowedInPrivate = metaData.allowedInPrivateBrowsing,
    optionsUrl = metaData.optionsPageUrl,
    homepageUrl = metaData.homepageUrl,
    isBuiltIn = isBuiltIn,
    permissions = metaData.requiredPermissions.toList(),
    origins = metaData.requiredOrigins.toList(),
)
