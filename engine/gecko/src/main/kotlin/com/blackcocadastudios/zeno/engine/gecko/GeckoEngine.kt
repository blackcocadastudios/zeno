package com.blackcocadastudios.zeno.engine.gecko

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import com.blackcocadastudios.zeno.engine.BrowsingData
import com.blackcocadastudios.zeno.engine.DownloadRequest
import com.blackcocadastudios.zeno.engine.Engine
import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.EngineSettings
import com.blackcocadastudios.zeno.engine.HistoryTracker
import com.blackcocadastudios.zeno.engine.HttpsOnly
import com.blackcocadastudios.zeno.engine.RequestInterceptor
import com.blackcocadastudios.zeno.engine.SecureDns
import com.blackcocadastudios.zeno.engine.SitePermissionStore
import com.blackcocadastudios.zeno.engine.TrackingProtection
import com.blackcocadastudios.zeno.engine.WebContentTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebRequest
import java.io.File

/**
 * [Engine] backed by GeckoView. One per process.
 *
 * The runtime starts lazily on first use, so the app can draw its first frame
 * (Zeno's home page is native) before Gecko boots.
 */
class GeckoEngine(context: Context, private val debug: Boolean = false) : Engine {
    internal val appContext: Context = context.applicationContext
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal var settings = EngineSettings()
        private set

    private var runtimeOrNull: GeckoRuntime? = null

    /** Created on first access; must be the main thread. Extensions start with it. */
    internal val runtime: GeckoRuntime
        get() = runtimeOrNull ?: createRuntime().also {
            runtimeOrNull = it
            extensions.start()
        }

    /** Gecko's version, e.g. "158.0.1". */
    val version: String get() = org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION

    override var requestInterceptor: RequestInterceptor? = null
    override var historyTracker: HistoryTracker? = null

    private val liveSessions = LinkedHashSet<GeckoEngineSession>()

    override val extensions: GeckoExtensionController by lazy { GeckoExtensionController(this) }

    internal val permissionStore by lazy { GeckoSitePermissions(this) }

    override val sitePermissions: SitePermissionStore get() = permissionStore

    internal val oneTimeGrants by lazy { OneTimeGrants(File(appContext.noBackupFilesDir, ONE_TIME_GRANTS_FILE), scope) }

    /** Starts Gecko ahead of the first page load. */
    fun warmUp() {
        runtime
    }

    /**
     * Zeno's activity handles configuration changes itself (no restart on
     * rotation or dark mode), so Gecko is told directly: pages see the new
     * `prefers-color-scheme` and screen orientation.
     */
    fun onConfigurationChanged(configuration: Configuration) {
        val rt = runtimeOrNull ?: return
        rt.configurationChanged(configuration)
        rt.orientationChanged()
    }

    override fun createSession(private: Boolean): EngineSession = GeckoEngineSession(this, private, open = true)

    /** For windows the engine opens itself: GeckoView opens these sessions. */
    internal fun createUnopenedSession(private: Boolean) = GeckoEngineSession(this, private, open = false)

    internal fun onSessionCreated(session: GeckoEngineSession) {
        liveSessions += session
    }

    internal fun onSessionClosed(session: GeckoEngineSession) {
        liveSessions -= session
    }

    internal fun sessions(): Collection<GeckoEngineSession> = liveSessions

    override fun applySettings(settings: EngineSettings) {
        val previous = this.settings
        this.settings = settings
        val rt = runtimeOrNull ?: return
        val s = rt.settings
        s.setJavaScriptEnabled(settings.javascriptEnabled)
        s.setGlobalPrivacyControl(settings.globalPrivacyControl)
        s.setPreferredColorScheme(colorScheme(settings.webContentTheme))
        s.setAllowInsecureConnections(httpsMode(settings.httpsOnly))
        s.setAutomaticFontSizeAdjustment(settings.automaticFontSizeAdjustment)
        if (!settings.automaticFontSizeAdjustment) s.setFontSizeFactor(settings.fontSizeFactor)
        s.setForceUserScalableEnabled(settings.forceUserScalable)
        s.setRemoteDebuggingEnabled(settings.remoteDebugging)
        applySecureDns(s, settings.secureDns)
        if (previous.trackingProtection != settings.trackingProtection) {
            applyTrackingProtection(s.contentBlocking, settings.trackingProtection)
            liveSessions.forEach { it.setTrackingProtection(settings.trackingProtection != TrackingProtection.Off) }
        }
    }

    override suspend fun clearData(data: Set<BrowsingData>) {
        val flags = data.fold(0L) { acc, item ->
            acc or when (item) {
                BrowsingData.Cookies -> StorageController.ClearFlags.COOKIES
                BrowsingData.Cache -> StorageController.ClearFlags.ALL_CACHES
                BrowsingData.SiteStorage -> StorageController.ClearFlags.DOM_STORAGES
                BrowsingData.SitePermissions -> StorageController.ClearFlags.SITE_SETTINGS
                BrowsingData.AuthSessions -> StorageController.ClearFlags.AUTH_SESSIONS
                BrowsingData.All -> StorageController.ClearFlags.ALL
            }
        }
        runtime.storageController.clearData(flags).await()
    }

    override suspend fun clearSiteData(host: String) {
        runtime.storageController
            .clearDataFromBaseDomain(host, StorageController.ClearFlags.ALL)
            .await()
    }

    override fun speculativeConnect(url: String) {
        runCatching { GeckoWebExecutor(runtime).speculativeConnect(url) }
    }

    // GeckoWebExecutor.FetchFlags lacks `flag = true`, so Lint rejects combined flags.
    @SuppressLint("WrongConstant")
    override suspend fun download(url: String, private: Boolean): DownloadRequest? {
        val flags = if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        val response = runCatching {
            GeckoWebExecutor(runtime).fetch(WebRequest.Builder(url).build(), flags).await()
        }.getOrNull() ?: return null
        if (response.statusCode !in 200..299) {
            runCatching { response.body?.close() }
            return null
        }
        return response.toDownloadRequest(private)
    }

    @SuppressLint("WrongConstant")
    override suspend fun fetchText(url: String, private: Boolean, timeoutMillis: Long): String? {
        val executor = GeckoWebExecutor(runtime)
        val flags = GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or
            (if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE)
        return withTimeoutOrNull(timeoutMillis) {
            val response = runCatching {
                executor.fetch(WebRequest.Builder(url).header("Accept", "application/json").build(), flags).await()
            }.getOrNull() ?: return@withTimeoutOrNull null
            if (response.statusCode !in 200..299) {
                runCatching { response.body?.close() }
                return@withTimeoutOrNull null
            }
            withContext(Dispatchers.IO) {
                runCatching { response.body?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
        }
    }

    private fun createRuntime(): GeckoRuntime {
        val contentBlocking = ContentBlocking.Settings.Builder()
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()
        applyTrackingProtection(contentBlocking, settings.trackingProtection)

        val builder = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(settings.javascriptEnabled)
            .globalPrivacyControlEnabled(settings.globalPrivacyControl)
            .preferredColorScheme(colorScheme(settings.webContentTheme))
            .allowInsecureConnections(httpsMode(settings.httpsOnly))
            .automaticFontSizeAdjustment(settings.automaticFontSizeAdjustment)
            .forceUserScalableEnabled(settings.forceUserScalable)
            .remoteDebuggingEnabled(settings.remoteDebugging)
            .contentBlocking(contentBlocking)
            // Lets addons.mozilla.org install extensions with its own buttons.
            .extensionsWebAPIEnabled(true)
            .extensionsProcessEnabled(true)
            .aboutConfigEnabled(true)
            .translationsOfferPopup(false)
            .consoleOutput(debug)
            .debugLogging(debug)
        if (!settings.automaticFontSizeAdjustment) builder.fontSizeFactor(settings.fontSizeFactor)

        val rt = GeckoRuntime.create(appContext, builder.build())
        applySecureDns(rt.settings, settings.secureDns)
        oneTimeGrants.revokeLeftovers(rt.storageController)
        return rt
    }

    private fun applySecureDns(s: GeckoRuntimeSettings, dns: SecureDns) {
        when (dns) {
            SecureDns.Off -> s.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_DISABLED)
            SecureDns.Default -> s.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF)
            is SecureDns.Increased -> {
                s.setTrustedRecursiveResolverUri(dns.providerUrl)
                s.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_FIRST)
            }
            is SecureDns.Max -> {
                s.setTrustedRecursiveResolverUri(dns.providerUrl)
                s.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_ONLY)
            }
        }
    }

    private companion object {
        const val ONE_TIME_GRANTS_FILE = "one-time-permissions.json"

        fun colorScheme(theme: WebContentTheme) = when (theme) {
            WebContentTheme.System -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
            WebContentTheme.Light -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            WebContentTheme.Dark -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
        }

        fun httpsMode(mode: HttpsOnly) = when (mode) {
            HttpsOnly.Off -> GeckoRuntimeSettings.ALLOW_ALL
            HttpsOnly.PrivateTabs -> GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE
            HttpsOnly.Everywhere -> GeckoRuntimeSettings.HTTPS_ONLY
        }

        /**
         * Mirrors Firefox's levels. Total Cookie Protection (cross-site cookies
         * isolated per site) stays on in every level except Off.
         */
        fun applyTrackingProtection(cb: ContentBlocking.Settings, level: TrackingProtection) {
            val tcp = ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
            when (level) {
                TrackingProtection.Off -> {
                    cb.setAntiTracking(ContentBlocking.AntiTracking.NONE)
                    cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE)
                    cb.setStrictSocialTrackingProtection(false)
                    cb.setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
                    cb.setCookieBehaviorPrivateMode(tcp)
                    cb.setQueryParameterStrippingEnabled(false)
                }
                TrackingProtection.Standard -> {
                    cb.setAntiTracking(
                        ContentBlocking.AntiTracking.DEFAULT or
                            ContentBlocking.AntiTracking.CRYPTOMINING or
                            ContentBlocking.AntiTracking.FINGERPRINTING or
                            ContentBlocking.AntiTracking.STP,
                    )
                    cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                    cb.setEnhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STANDARD)
                    cb.setStrictSocialTrackingProtection(false)
                    cb.setCookieBehavior(tcp)
                    cb.setCookieBehaviorPrivateMode(tcp)
                    cb.setQueryParameterStrippingEnabled(false)
                    cb.setQueryParameterStrippingPrivateBrowsingEnabled(true)
                }
                TrackingProtection.Strict -> {
                    cb.setAntiTracking(ContentBlocking.AntiTracking.STRICT or ContentBlocking.AntiTracking.STP)
                    cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    cb.setEnhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STRICT)
                    cb.setStrictSocialTrackingProtection(true)
                    cb.setCookieBehavior(tcp)
                    cb.setCookieBehaviorPrivateMode(tcp)
                    cb.setQueryParameterStrippingEnabled(true)
                    cb.setQueryParameterStrippingPrivateBrowsingEnabled(true)
                }
            }
        }
    }
}
