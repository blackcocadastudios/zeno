package com.blackcocadastudios.zeno.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalZenoColors = staticCompositionLocalOf { DayColors }
val LocalZenoTypography = staticCompositionLocalOf { DefaultTypography }

/**
 * Zeno's theme. Material components are mapped onto Zeno's tokens so anything
 * borrowed from Material 3 (sheets, dialogs, switches) already looks like Zeno.
 *
 * @param dark night theme (墨 sumi); defaults to the system setting.
 * @param privateMode private browsing theme (夜 yoru); wins over [dark].
 */
@Composable
fun ZenoTheme(
    dark: Boolean = isSystemInDarkTheme(),
    privateMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        privateMode -> PrivateColors
        dark -> NightColors
        else -> DayColors
    }
    val typography = DefaultTypography
    CompositionLocalProvider(
        LocalZenoColors provides colors,
        LocalZenoTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = Shapes(
                extraSmall = ZenoShapes.small,
                small = ZenoShapes.small,
                medium = ZenoShapes.medium,
                large = ZenoShapes.large,
                extraLarge = ZenoShapes.large,
            ),
            content = content,
        )
    }
}

/** Entry point for Zeno tokens inside composables: `Zeno.colors.ink`. */
object Zeno {
    val colors: ZenoColors
        @Composable @ReadOnlyComposable get() = LocalZenoColors.current

    val type: ZenoTypography
        @Composable @ReadOnlyComposable get() = LocalZenoTypography.current
}

internal fun ZenoColors.toMaterialColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = accentInk,
        secondary = accentInk,
        onSecondary = paper,
        secondaryContainer = accentContainer,
        onSecondaryContainer = onAccentContainer,
        tertiary = seal,
        onTertiary = paper,
        tertiaryContainer = paperSunken,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = paperSunken,
        onSurfaceVariant = inkSoft,
        surfaceTint = paper,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = seal,
        onError = paper,
        errorContainer = paperSunken,
        onErrorContainer = seal,
        outline = inkFaint,
        outlineVariant = line,
        scrim = scrim,
        surfaceBright = paperRaised,
        surfaceDim = paperSunken,
        surfaceContainerLowest = paper,
        surfaceContainerLow = paper,
        surfaceContainer = paperRaised,
        surfaceContainerHigh = paperRaised,
        surfaceContainerHighest = paperSunken,
    )
}

internal fun ZenoTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = title,
    headlineMedium = title,
    headlineSmall = heading,
    titleLarge = title,
    titleMedium = heading,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = bodySmall,
    bodySmall = caption,
    labelLarge = label,
    labelMedium = label,
    labelSmall = caption,
)
