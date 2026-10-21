package com.blackcocadastudios.zeno.engine

/** Engine-wide settings. The app stores user preferences and maps them here. */
data class EngineSettings(
    val trackingProtection: TrackingProtection = TrackingProtection.Standard,
    val httpsOnly: HttpsOnly = HttpsOnly.PrivateTabs,
    val globalPrivacyControl: Boolean = true,
    val javascriptEnabled: Boolean = true,
    val webContentTheme: WebContentTheme = WebContentTheme.System,
    val fontSizeFactor: Float = 1f,
    val automaticFontSizeAdjustment: Boolean = true,
    val forceUserScalable: Boolean = false,
    val blockAudibleAutoplay: Boolean = true,
    val secureDns: SecureDns = SecureDns.Default,
    val remoteDebugging: Boolean = false,
)

enum class TrackingProtection { Off, Standard, Strict }

enum class HttpsOnly { Off, PrivateTabs, Everywhere }

/** Which `prefers-color-scheme` sites see. */
enum class WebContentTheme { System, Light, Dark }

/** DNS over HTTPS. [Default] follows the engine's own heuristics. */
sealed interface SecureDns {
    data object Off : SecureDns
    data object Default : SecureDns
    data class Increased(val providerUrl: String) : SecureDns
    data class Max(val providerUrl: String) : SecureDns
}
