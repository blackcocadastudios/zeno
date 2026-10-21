package com.blackcocadastudios.zeno.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences on disk. Unknown or corrupt values fall back to the defaults. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<UserSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::read)
        .distinctUntilChanged()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private object Keys {
        val searchEngine = stringPreferencesKey("search_engine")
        val searchSuggestions = booleanPreferencesKey("search_suggestions")
        val hideToolbarOnScroll = booleanPreferencesKey("toolbar_hide_on_scroll")
        val appTheme = stringPreferencesKey("app_theme")
        val webTheme = stringPreferencesKey("web_theme")
        val trackingProtection = stringPreferencesKey("tracking_protection")
        val httpsOnly = stringPreferencesKey("https_only")
        val gpc = booleanPreferencesKey("gpc")
        val blockAutoplay = booleanPreferencesKey("block_audible_autoplay")
        val javascript = booleanPreferencesKey("javascript")
        val desktopDefault = booleanPreferencesKey("desktop_default")
        val automaticFontSize = booleanPreferencesKey("font_auto")
        val fontSize = floatPreferencesKey("font_size")
        val forceZoom = booleanPreferencesKey("force_zoom")
        val secureDns = stringPreferencesKey("secure_dns")
        val dnsProvider = stringPreferencesKey("dns_provider")
        val showShortcuts = booleanPreferencesKey("show_shortcuts")
        val restoreTabs = booleanPreferencesKey("restore_tabs")
        val clearOnExit = booleanPreferencesKey("clear_on_exit")
        val remoteDebugging = booleanPreferencesKey("remote_debugging")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    private fun read(p: Preferences): UserSettings {
        val d = UserSettings()
        return UserSettings(
            searchEngineId = p[Keys.searchEngine] ?: d.searchEngineId,
            searchSuggestions = p[Keys.searchSuggestions] ?: d.searchSuggestions,
            hideToolbarOnScroll = p[Keys.hideToolbarOnScroll] ?: d.hideToolbarOnScroll,
            appTheme = enumOr(p[Keys.appTheme], d.appTheme),
            webContentTheme = enumOr(p[Keys.webTheme], d.webContentTheme),
            trackingProtection = enumOr(p[Keys.trackingProtection], d.trackingProtection),
            httpsOnly = enumOr(p[Keys.httpsOnly], d.httpsOnly),
            globalPrivacyControl = p[Keys.gpc] ?: d.globalPrivacyControl,
            blockAudibleAutoplay = p[Keys.blockAutoplay] ?: d.blockAudibleAutoplay,
            javascriptEnabled = p[Keys.javascript] ?: d.javascriptEnabled,
            desktopModeByDefault = p[Keys.desktopDefault] ?: d.desktopModeByDefault,
            automaticFontSize = p[Keys.automaticFontSize] ?: d.automaticFontSize,
            fontSizeFactor = (p[Keys.fontSize] ?: d.fontSizeFactor).coerceIn(0.5f, 2f),
            forceZoom = p[Keys.forceZoom] ?: d.forceZoom,
            secureDns = enumOr(p[Keys.secureDns], d.secureDns),
            secureDnsProvider = enumOr(p[Keys.dnsProvider], d.secureDnsProvider),
            showShortcuts = p[Keys.showShortcuts] ?: d.showShortcuts,
            restoreTabs = p[Keys.restoreTabs] ?: d.restoreTabs,
            clearOnExit = p[Keys.clearOnExit] ?: d.clearOnExit,
            remoteDebugging = p[Keys.remoteDebugging] ?: d.remoteDebugging,
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
        )
    }

    private fun write(p: MutablePreferences, s: UserSettings) {
        p[Keys.searchEngine] = s.searchEngineId
        p[Keys.searchSuggestions] = s.searchSuggestions
        p[Keys.hideToolbarOnScroll] = s.hideToolbarOnScroll
        p[Keys.appTheme] = s.appTheme.name
        p[Keys.webTheme] = s.webContentTheme.name
        p[Keys.trackingProtection] = s.trackingProtection.name
        p[Keys.httpsOnly] = s.httpsOnly.name
        p[Keys.gpc] = s.globalPrivacyControl
        p[Keys.blockAutoplay] = s.blockAudibleAutoplay
        p[Keys.javascript] = s.javascriptEnabled
        p[Keys.desktopDefault] = s.desktopModeByDefault
        p[Keys.automaticFontSize] = s.automaticFontSize
        p[Keys.fontSize] = s.fontSizeFactor
        p[Keys.forceZoom] = s.forceZoom
        p[Keys.secureDns] = s.secureDns.name
        p[Keys.dnsProvider] = s.secureDnsProvider.name
        p[Keys.showShortcuts] = s.showShortcuts
        p[Keys.restoreTabs] = s.restoreTabs
        p[Keys.clearOnExit] = s.clearOnExit
        p[Keys.remoteDebugging] = s.remoteDebugging
        p[Keys.onboardingDone] = s.onboardingDone
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
