package com.blackcocadastudios.zeno.data.settings

import com.blackcocadastudios.zeno.engine.EngineSettings
import com.blackcocadastudios.zeno.engine.HttpsOnly
import com.blackcocadastudios.zeno.engine.SecureDns
import com.blackcocadastudios.zeno.engine.TrackingProtection
import com.blackcocadastudios.zeno.engine.WebContentTheme

/** Every user preference, with Zeno's defaults. */
data class UserSettings(
    val searchEngineId: String = "duckduckgo",
    /** Sends each typed letter to the search engine; off by default for privacy. */
    val searchSuggestions: Boolean = false,
    val hideToolbarOnScroll: Boolean = true,
    val appTheme: AppTheme = AppTheme.System,
    val webContentTheme: WebContentTheme = WebContentTheme.System,
    val trackingProtection: TrackingProtection = TrackingProtection.Standard,
    val httpsOnly: HttpsOnly = HttpsOnly.PrivateTabs,
    val globalPrivacyControl: Boolean = true,
    val blockAudibleAutoplay: Boolean = true,
    /** Sites the user allows may notify while they are open. Off by default: silence. */
    val siteNotifications: Boolean = false,
    /** Offers to save a login after a sign-in form is sent. */
    val askToSavePasswords: Boolean = true,
    /** Fills sign-in forms with a saved login as the page loads. */
    val autofillPasswords: Boolean = true,
    val javascriptEnabled: Boolean = true,
    val desktopModeByDefault: Boolean = false,
    val automaticFontSize: Boolean = true,
    /** 0.5 … 2.0, used when [automaticFontSize] is off. */
    val fontSizeFactor: Float = 1f,
    val forceZoom: Boolean = false,
    val secureDns: SecureDnsChoice = SecureDnsChoice.Default,
    val secureDnsProvider: DnsProvider = DnsProvider.Cloudflare,
    val showShortcuts: Boolean = true,
    val restoreTabs: Boolean = true,
    /** Clears history, cookies and tabs when leaving through "Sair". */
    val clearOnExit: Boolean = false,
    val remoteDebugging: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    fun toEngineSettings() = EngineSettings(
        trackingProtection = trackingProtection,
        httpsOnly = httpsOnly,
        globalPrivacyControl = globalPrivacyControl,
        javascriptEnabled = javascriptEnabled,
        webContentTheme = webContentTheme,
        fontSizeFactor = fontSizeFactor,
        automaticFontSizeAdjustment = automaticFontSize,
        forceUserScalable = forceZoom,
        blockAudibleAutoplay = blockAudibleAutoplay,
        loginAutofill = autofillPasswords,
        secureDns = when (secureDns) {
            SecureDnsChoice.Default -> SecureDns.Default
            SecureDnsChoice.Increased -> SecureDns.Increased(secureDnsProvider.url)
            SecureDnsChoice.Max -> SecureDns.Max(secureDnsProvider.url)
            SecureDnsChoice.Off -> SecureDns.Off
        },
        remoteDebugging = remoteDebugging,
    )
}

enum class AppTheme { System, Light, Dark }

enum class SecureDnsChoice { Default, Increased, Max, Off }

enum class DnsProvider(val label: String, val url: String) {
    Cloudflare("Cloudflare", "https://mozilla.cloudflare-dns.com/dns-query"),
    Quad9("Quad9", "https://dns.quad9.net/dns-query"),
    Mullvad("Mullvad", "https://dns.mullvad.net/dns-query"),
    NextDns("NextDNS", "https://firefox.dns.nextdns.io/"),
}
