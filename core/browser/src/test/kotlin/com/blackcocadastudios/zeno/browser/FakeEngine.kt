package com.blackcocadastudios.zeno.browser

import com.blackcocadastudios.zeno.engine.BrowsingData
import com.blackcocadastudios.zeno.engine.DownloadRequest
import com.blackcocadastudios.zeno.engine.Engine
import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.EngineSessionState
import com.blackcocadastudios.zeno.engine.EngineSettings
import com.blackcocadastudios.zeno.engine.ExtensionAction
import com.blackcocadastudios.zeno.engine.ExtensionController
import com.blackcocadastudios.zeno.engine.ExtensionInfo
import com.blackcocadastudios.zeno.engine.ExtensionInstallRequest
import com.blackcocadastudios.zeno.engine.ExtensionPopup
import com.blackcocadastudios.zeno.engine.ExtensionTabs
import com.blackcocadastudios.zeno.engine.HistoryTracker
import com.blackcocadastudios.zeno.engine.LoadFlags
import com.blackcocadastudios.zeno.engine.LoginStore
import com.blackcocadastudios.zeno.engine.ReaderMode
import com.blackcocadastudios.zeno.engine.RequestInterceptor
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.engine.SitePermissionStore
import com.blackcocadastudios.zeno.engine.WebNotificationHandler
import com.blackcocadastudios.zeno.engine.WindowOpener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory engine that records what the controller asked of it. */
class FakeEngine : Engine {
    val created = mutableListOf<FakeSession>()
    override var requestInterceptor: RequestInterceptor? = null
    override var historyTracker: HistoryTracker? = null
    override val extensions = FakeExtensions()
    override val sitePermissions = FakeSitePermissions()
    override val reader = object : ReaderMode {
        override suspend fun readerUrl(url: String) = "reader:$url"
        override fun originalUrl(url: String) = url.removePrefix("reader:").takeIf { it != url }
    }
    override var notificationHandler: WebNotificationHandler? = null
    override var loginStore: LoginStore? = null
    override var windowOpener: WindowOpener? = null

    override fun createSession(private: Boolean): EngineSession = FakeSession(private).also { created += it }
    override fun applySettings(settings: EngineSettings) = Unit
    override suspend fun clearData(data: Set<BrowsingData>) = Unit
    override suspend fun clearSiteData(host: String) = Unit
    override fun speculativeConnect(url: String) = Unit
    override suspend fun download(url: String, private: Boolean): DownloadRequest? = null
    override suspend fun fetchText(url: String, private: Boolean, timeoutMillis: Long): String? = null
}

class FakeSession(override val isPrivate: Boolean) : EngineSession {
    override var observer: EngineSession.Observer? = null
    val loaded = mutableListOf<String>()
    var restored: EngineSessionState? = null
    var closed = false
    var active: Boolean? = null
    var backCalls = 0
    var desktop = false
    var exitedFullscreen = false
    var protectionException: Boolean? = null

    override fun load(url: String, flags: LoadFlags) {
        loaded += url
    }
    override fun reload(bypassCache: Boolean) = Unit
    override fun stop() = Unit
    override fun goBack() {
        backCalls++
    }
    override fun goForward() = Unit
    override fun restoreState(state: EngineSessionState) {
        restored = state
    }
    override fun setDesktopMode(enabled: Boolean, reload: Boolean) {
        desktop = enabled
    }
    override fun find(text: String, backwards: Boolean) = Unit
    override fun clearFind() = Unit
    override fun exitFullscreen() {
        exitedFullscreen = true
    }
    override fun setActive(active: Boolean) {
        this.active = active
    }
    override fun purgeHistory() = Unit
    override fun translate(fromLanguage: String, toLanguage: String) = Unit
    override fun restoreOriginalLanguage() = Unit
    override fun setProtectionException(excepted: Boolean) {
        protectionException = excepted
    }
    override fun close() {
        closed = true
    }
}

class FakeExtensions : ExtensionController {
    var lastActive: EngineSession? = null
    override val installed: StateFlow<List<ExtensionInfo>> = MutableStateFlow(emptyList())
    override val actions: StateFlow<Map<String, ExtensionAction>> = MutableStateFlow(emptyMap())
    override val popups: SharedFlow<ExtensionPopup> = MutableSharedFlow()
    override var installPrompt: (suspend (ExtensionInstallRequest) -> Boolean)? = null
    override var tabs: ExtensionTabs? = null
    override suspend fun install(url: String): Result<ExtensionInfo> = Result.failure(UnsupportedOperationException())
    override suspend fun uninstall(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun setEnabled(id: String, enabled: Boolean): Result<ExtensionInfo> = Result.failure(UnsupportedOperationException())
    override suspend fun setAllowedInPrivate(id: String, allowed: Boolean): Result<ExtensionInfo> = Result.failure(UnsupportedOperationException())
    override suspend fun refresh() = Unit
    override fun clickAction(id: String) = Unit
    override fun setActiveSession(session: EngineSession) {
        lastActive = session
    }
}

class FakeSitePermissions : SitePermissionStore {
    val stored = mutableListOf<SitePermission>()
    override suspend fun all(): List<SitePermission> = stored.filter { !it.private }
    override suspend fun forSite(url: String, private: Boolean): List<SitePermission> =
        stored.filter { it.private == private && url.startsWith(it.origin) }
    override suspend fun set(permission: SitePermission, allowed: Boolean?) {
        stored.removeAll { it.origin == permission.origin && it.kind == permission.kind && it.private == permission.private }
        if (allowed != null) stored += permission.copy(allowed = allowed)
    }
}
