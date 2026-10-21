package com.blackcocadastudios.zeno.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.search.SearchEngines
import com.blackcocadastudios.zeno.data.settings.AppTheme
import com.blackcocadastudios.zeno.data.settings.DnsProvider
import com.blackcocadastudios.zeno.data.settings.SecureDnsChoice
import com.blackcocadastudios.zeno.data.settings.UserSettings
import com.blackcocadastudios.zeno.design.component.SectionLabel
import com.blackcocadastudios.zeno.design.component.ZenoListRow
import com.blackcocadastudios.zeno.design.component.ZenoSwitchRow
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.engine.HttpsOnly
import com.blackcocadastudios.zeno.engine.TrackingProtection
import com.blackcocadastudios.zeno.engine.WebContentTheme
import com.blackcocadastudios.zeno.ui.common.ChoiceDialog
import com.blackcocadastudios.zeno.ui.common.ZenoDialog
import com.blackcocadastudios.zeno.ui.common.ZenoPage
import kotlin.math.roundToInt

/** Settings actions that leave this screen or need the system. */
interface SettingsActions {
    fun update(transform: (UserSettings) -> UserSettings)
    fun clearBrowsingData()
    fun openUrl(url: String)
    fun makeDefaultBrowser()
    fun back()
}

private enum class Choice { SearchEngine, Theme, WebTheme, Tracking, Https, Dns, DnsProvider }

@Composable
fun SettingsScreen(settings: UserSettings, versions: String, actions: SettingsActions) {
    var choice by remember { mutableStateOf<Choice?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    ZenoPage(title = stringResource(R.string.settings_title), onBack = actions::back) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Ma.xxl),
        ) {
            SectionLabel(stringResource(R.string.settings_general))
            ZenoListRow(
                title = stringResource(R.string.settings_search_engine),
                subtitle = SearchEngines.byId(settings.searchEngineId).name,
                icon = ZenoIcons.Search,
                onClick = { choice = Choice.SearchEngine },
            )
            ZenoSwitchRow(
                title = stringResource(R.string.settings_search_suggestions),
                subtitle = stringResource(R.string.settings_search_suggestions_desc),
                checked = settings.searchSuggestions,
                onCheckedChange = { on -> actions.update { it.copy(searchSuggestions = on) } },
            )
            ZenoSwitchRow(
                title = stringResource(R.string.settings_shortcuts),
                checked = settings.showShortcuts,
                onCheckedChange = { on -> actions.update { it.copy(showShortcuts = on) } },
            )
            ZenoSwitchRow(
                title = stringResource(R.string.settings_restore_tabs),
                checked = settings.restoreTabs,
                onCheckedChange = { on -> actions.update { it.copy(restoreTabs = on) } },
            )
            ZenoListRow(
                title = stringResource(R.string.settings_default_browser),
                icon = ZenoIcons.Globe,
                onClick = actions::makeDefaultBrowser,
            )

            SectionLabel(stringResource(R.string.settings_appearance))
            ZenoListRow(
                title = stringResource(R.string.settings_theme),
                subtitle = themeLabel(settings.appTheme),
                icon = ZenoIcons.Sun,
                onClick = { choice = Choice.Theme },
            )
            ZenoListRow(
                title = stringResource(R.string.settings_web_theme),
                subtitle = webThemeLabel(settings.webContentTheme),
                icon = ZenoIcons.Moon,
                onClick = { choice = Choice.WebTheme },
            )

            SectionLabel(stringResource(R.string.settings_privacy))
            ZenoListRow(
                title = stringResource(R.string.settings_tracking),
                subtitle = trackingLabel(settings.trackingProtection),
                icon = ZenoIcons.Shield,
                onClick = { choice = Choice.Tracking },
            )
            ZenoListRow(
                title = stringResource(R.string.settings_https_only),
                subtitle = httpsLabel(settings.httpsOnly),
                icon = ZenoIcons.Lock,
                onClick = { choice = Choice.Https },
            )
            ZenoSwitchRow(
                title = stringResource(R.string.settings_gpc),
                subtitle = stringResource(R.string.settings_gpc_desc),
                checked = settings.globalPrivacyControl,
                onCheckedChange = { on -> actions.update { it.copy(globalPrivacyControl = on) } },
            )
            ZenoListRow(
                title = stringResource(R.string.settings_secure_dns),
                subtitle = dnsLabel(settings.secureDns),
                icon = ZenoIcons.Key,
                onClick = { choice = Choice.Dns },
            )
            if (settings.secureDns == SecureDnsChoice.Increased || settings.secureDns == SecureDnsChoice.Max) {
                ZenoListRow(
                    title = stringResource(R.string.settings_dns_provider),
                    subtitle = settings.secureDnsProvider.label,
                    onClick = { choice = Choice.DnsProvider },
                )
            }
            ZenoSwitchRow(
                title = stringResource(R.string.settings_autoplay),
                subtitle = stringResource(R.string.settings_autoplay_desc),
                checked = settings.blockAudibleAutoplay,
                onCheckedChange = { on -> actions.update { it.copy(blockAudibleAutoplay = on) } },
            )
            ZenoSwitchRow(
                title = stringResource(R.string.settings_clear_on_exit),
                subtitle = stringResource(R.string.settings_clear_on_exit_desc),
                checked = settings.clearOnExit,
                onCheckedChange = { on -> actions.update { it.copy(clearOnExit = on) } },
            )
            ZenoListRow(
                title = stringResource(R.string.settings_clear_now),
                subtitle = stringResource(R.string.settings_clear_now_desc),
                icon = ZenoIcons.Delete,
                titleColor = Zeno.colors.seal,
                onClick = { confirmClear = true },
            )

            SectionLabel(stringResource(R.string.settings_sites))
            ZenoSwitchRow(
                title = stringResource(R.string.settings_javascript),
                checked = settings.javascriptEnabled,
                onCheckedChange = { on -> actions.update { it.copy(javascriptEnabled = on) } },
            )
            ZenoSwitchRow(
                title = stringResource(R.string.settings_desktop_default),
                checked = settings.desktopModeByDefault,
                onCheckedChange = { on -> actions.update { it.copy(desktopModeByDefault = on) } },
            )
            ZenoSwitchRow(
                title = stringResource(R.string.settings_font_auto),
                subtitle = stringResource(R.string.settings_font_auto_desc),
                checked = settings.automaticFontSize,
                onCheckedChange = { on -> actions.update { it.copy(automaticFontSize = on) } },
            )
            if (!settings.automaticFontSize) {
                FontSizeSlider(settings.fontSizeFactor) { factor -> actions.update { it.copy(fontSizeFactor = factor) } }
            }
            ZenoSwitchRow(
                title = stringResource(R.string.settings_force_zoom),
                checked = settings.forceZoom,
                onCheckedChange = { on -> actions.update { it.copy(forceZoom = on) } },
            )

            SectionLabel(stringResource(R.string.settings_advanced))
            ZenoSwitchRow(
                title = stringResource(R.string.settings_remote_debugging),
                checked = settings.remoteDebugging,
                onCheckedChange = { on -> actions.update { it.copy(remoteDebugging = on) } },
            )
            ZenoListRow(
                title = stringResource(R.string.settings_about_config),
                icon = ZenoIcons.Sliders,
                onClick = { actions.openUrl("about:config") },
            )

            SectionLabel(stringResource(R.string.settings_about))
            ZenoListRow(title = "Zeno", subtitle = versions, icon = ZenoIcons.Info)
            ZenoListRow(
                title = stringResource(R.string.settings_source),
                subtitle = SOURCE_URL.removePrefix("https://"),
                icon = ZenoIcons.OpenExternal,
                onClick = { actions.openUrl(SOURCE_URL) },
            )
            ZenoListRow(
                title = stringResource(R.string.settings_licenses),
                icon = ZenoIcons.File,
                onClick = { showLicenses = true },
            )
        }
    }

    when (choice) {
        Choice.SearchEngine -> {
            val engines = SearchEngines.all
            ChoiceDialog(
                title = stringResource(R.string.settings_search_engine),
                options = engines.map { it.name to null },
                selected = engines.indexOfFirst { it.id == settings.searchEngineId },
                onSelect = { i -> actions.update { it.copy(searchEngineId = engines[i].id) }; choice = null },
                onDismiss = { choice = null },
            )
        }
        Choice.Theme -> EnumChoice(
            title = stringResource(R.string.settings_theme),
            values = AppTheme.entries,
            current = settings.appTheme,
            label = { themeLabel(it) to null },
            onSelect = { v -> actions.update { it.copy(appTheme = v) } },
            onDismiss = { choice = null },
        )
        Choice.WebTheme -> EnumChoice(
            title = stringResource(R.string.settings_web_theme),
            values = WebContentTheme.entries,
            current = settings.webContentTheme,
            label = { webThemeLabel(it) to null },
            onSelect = { v -> actions.update { it.copy(webContentTheme = v) } },
            onDismiss = { choice = null },
        )
        Choice.Tracking -> EnumChoice(
            title = stringResource(R.string.settings_tracking),
            values = listOf(TrackingProtection.Standard, TrackingProtection.Strict, TrackingProtection.Off),
            current = settings.trackingProtection,
            label = { trackingLabel(it) to trackingDescription(it) },
            onSelect = { v -> actions.update { it.copy(trackingProtection = v) } },
            onDismiss = { choice = null },
        )
        Choice.Https -> EnumChoice(
            title = stringResource(R.string.settings_https_only),
            values = listOf(HttpsOnly.PrivateTabs, HttpsOnly.Everywhere, HttpsOnly.Off),
            current = settings.httpsOnly,
            label = { httpsLabel(it) to null },
            onSelect = { v -> actions.update { it.copy(httpsOnly = v) } },
            onDismiss = { choice = null },
        )
        Choice.Dns -> EnumChoice(
            title = stringResource(R.string.settings_secure_dns),
            values = listOf(SecureDnsChoice.Default, SecureDnsChoice.Increased, SecureDnsChoice.Max, SecureDnsChoice.Off),
            current = settings.secureDns,
            label = { dnsLabel(it) to dnsDescription(it) },
            onSelect = { v -> actions.update { it.copy(secureDns = v) } },
            onDismiss = { choice = null },
        )
        Choice.DnsProvider -> EnumChoice(
            title = stringResource(R.string.settings_dns_provider),
            values = DnsProvider.entries,
            current = settings.secureDnsProvider,
            label = { it.label to null },
            onSelect = { v -> actions.update { it.copy(secureDnsProvider = v) } },
            onDismiss = { choice = null },
        )
        null -> Unit
    }

    if (confirmClear) {
        ZenoDialog(
            onDismiss = { confirmClear = false },
            title = stringResource(R.string.settings_clear_now),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmClear = false
                actions.clearBrowsingData()
            },
            dismissLabel = stringResource(R.string.prompt_cancel),
            destructive = true,
        ) { Text(stringResource(R.string.settings_clear_now_desc), style = Zeno.type.bodySmall) }
    }
    if (showLicenses) {
        ZenoDialog(
            onDismiss = { showLicenses = false },
            title = stringResource(R.string.settings_licenses),
            confirmLabel = stringResource(R.string.action_close),
            onConfirm = { showLicenses = false },
        ) { Text(stringResource(R.string.settings_licenses_body), style = Zeno.type.bodySmall) }
    }
}

@Composable
private fun <E> EnumChoice(
    title: String,
    values: List<E>,
    current: E,
    label: @Composable (E) -> Pair<String, String?>,
    onSelect: (E) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = values.map { label(it) }
    ChoiceDialog(
        title = title,
        options = options,
        selected = values.indexOf(current),
        onSelect = { i ->
            onSelect(values[i])
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun FontSizeSlider(factor: Float, onChange: (Float) -> Unit) {
    var value by remember(factor) { mutableFloatStateOf(factor) }
    Column(Modifier.padding(horizontal = Ma.xl, vertical = Ma.s)) {
        Text(
            stringResource(R.string.settings_font_size, (value * 100).roundToInt()),
            style = Zeno.type.bodySmall,
            color = Zeno.colors.inkSoft,
        )
        Slider(
            value = value,
            onValueChange = { value = (it * 10).roundToInt() / 10f },
            onValueChangeFinished = { onChange(value) },
            valueRange = 0.5f..2f,
            steps = 14,
            colors = SliderDefaults.colors(
                thumbColor = Zeno.colors.accent,
                activeTrackColor = Zeno.colors.accent,
                inactiveTrackColor = Zeno.colors.paperSunken,
            ),
        )
    }
}

@Composable
private fun themeLabel(theme: AppTheme) = stringResource(
    when (theme) {
        AppTheme.System -> R.string.settings_theme_system
        AppTheme.Light -> R.string.settings_theme_light
        AppTheme.Dark -> R.string.settings_theme_dark
    },
)

@Composable
private fun webThemeLabel(theme: WebContentTheme) = stringResource(
    when (theme) {
        WebContentTheme.System -> R.string.settings_theme_system
        WebContentTheme.Light -> R.string.settings_theme_light
        WebContentTheme.Dark -> R.string.settings_theme_dark
    },
)

@Composable
private fun trackingLabel(level: TrackingProtection) = stringResource(
    when (level) {
        TrackingProtection.Standard -> R.string.settings_tracking_standard
        TrackingProtection.Strict -> R.string.settings_tracking_strict
        TrackingProtection.Off -> R.string.settings_tracking_off
    },
)

@Composable
private fun trackingDescription(level: TrackingProtection) = stringResource(
    when (level) {
        TrackingProtection.Standard -> R.string.settings_tracking_standard_desc
        TrackingProtection.Strict -> R.string.settings_tracking_strict_desc
        TrackingProtection.Off -> R.string.settings_tracking_off_desc
    },
)

@Composable
private fun httpsLabel(mode: HttpsOnly) = stringResource(
    when (mode) {
        HttpsOnly.PrivateTabs -> R.string.settings_https_private
        HttpsOnly.Everywhere -> R.string.settings_https_always
        HttpsOnly.Off -> R.string.settings_https_off
    },
)

@Composable
private fun dnsLabel(choice: SecureDnsChoice) = stringResource(
    when (choice) {
        SecureDnsChoice.Default -> R.string.settings_dns_default
        SecureDnsChoice.Increased -> R.string.settings_dns_increased
        SecureDnsChoice.Max -> R.string.settings_dns_max
        SecureDnsChoice.Off -> R.string.settings_dns_off
    },
)

@Composable
private fun dnsDescription(choice: SecureDnsChoice): String? = when (choice) {
    SecureDnsChoice.Increased -> stringResource(R.string.settings_dns_increased_desc)
    SecureDnsChoice.Max -> stringResource(R.string.settings_dns_max_desc)
    else -> null
}

const val SOURCE_URL = "https://github.com/blackcocadastudios/zeno"
