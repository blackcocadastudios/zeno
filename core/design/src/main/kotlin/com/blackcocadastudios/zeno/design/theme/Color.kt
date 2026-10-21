package com.blackcocadastudios.zeno.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Traditional Japanese colors (伝統色, dentōshoku) that Zeno's palette is drawn from.
 * Values follow common references, tuned for screens.
 */
object Dentoshoku {
    /** 白練 — glossed white silk; Zeno's paper. */
    val Shironeri = Color(0xFFFCFAF2)

    /** 胡粉 — shell white. */
    val Gofun = Color(0xFFFFFFFB)

    /** 水色 — water. */
    val Mizuiro = Color(0xFFBCE2E8)

    /** 甕覗 — "peering into the jar", the palest indigo dye. */
    val Kamenozoki = Color(0xFFA5DEE4)

    /** 空色 — sky. */
    val Sorairo = Color(0xFFA0D8EF)

    /** 縹 — bright indigo-blue. */
    val Hanada = Color(0xFF2792C3)

    /** 藍 — indigo. */
    val Ai = Color(0xFF165E83)

    /** 墨 — ink. */
    val Sumi = Color(0xFF1C1C1C)

    /** 鈍 — dull gray. */
    val Nibi = Color(0xFF656765)

    /** 朱 — vermilion of seal paste. */
    val Shu = Color(0xFFC8442E)
}

/**
 * Semantic colors. Components read these names, never raw palette values,
 * so the day, night and private themes stay consistent.
 */
@Immutable
data class ZenoColors(
    /** App background: paper. */
    val paper: Color,
    /** Sheets, menus, cards: paper laid on paper. */
    val paperRaised: Color,
    /** Fields, chips, pressed areas: a tint of water. */
    val paperSunken: Color,
    /** Primary text and icons. */
    val ink: Color,
    /** Secondary text. */
    val inkSoft: Color,
    /** Placeholders and disabled content. */
    val inkFaint: Color,
    /** Hairlines and borders. */
    val line: Color,
    /** Progress, toggles, focus: the light blue. */
    val accent: Color,
    /** Accent used for text; meets WCAG AA on [paper]. */
    val accentInk: Color,
    /** Selected and highlighted containers. */
    val accentContainer: Color,
    /** Content on [accent]. */
    val onAccent: Color,
    /** Content on [accentContainer]. */
    val onAccentContainer: Color,
    /** 朱 seal red: destructive actions and rare emphasis. */
    val seal: Color,
    /** Dim layer under modal surfaces. */
    val scrim: Color,
    val isDark: Boolean,
    val isPrivate: Boolean,
)

/** 昼 Hiru — day: ink on paper with water-blue accents. */
val DayColors = ZenoColors(
    paper = Dentoshoku.Shironeri,
    paperRaised = Color(0xFFFFFFFC),
    paperSunken = Color(0xFFE7F1F3),
    ink = Color(0xFF22282B),
    inkSoft = Color(0xFF5A6367),
    inkFaint = Color(0xFF959FA3),
    line = Color(0xFFE3DED2),
    accent = Dentoshoku.Hanada,
    accentInk = Dentoshoku.Ai,
    accentContainer = Color(0xFFD3ECF2),
    onAccent = Color(0xFFFFFFFF),
    onAccentContainer = Color(0xFF0F3B52),
    seal = Dentoshoku.Shu,
    scrim = Color(0x52000000),
    isDark = false,
    isPrivate = false,
)

/** 墨 Sumi — night: washi-colored ink on deep ink, sky-blue accents. */
val NightColors = ZenoColors(
    paper = Color(0xFF121416),
    paperRaised = Color(0xFF1A1E21),
    paperSunken = Color(0xFF232A2E),
    ink = Color(0xFFECE9E2),
    inkSoft = Color(0xFFA9B0B4),
    inkFaint = Color(0xFF6E777C),
    line = Color(0xFF2C3236),
    accent = Color(0xFF8CC9E3),
    accentInk = Color(0xFFA8DCF0),
    accentContainer = Color(0xFF1E3A47),
    onAccent = Color(0xFF0B2230),
    onAccentContainer = Color(0xFFCBEAF5),
    seal = Color(0xFFE0705A),
    scrim = Color(0x8C000000),
    isDark = true,
    isPrivate = false,
)

/** 夜 Yoru — private browsing: an indigo night, so it is never mistaken for the day. */
val PrivateColors = ZenoColors(
    paper = Color(0xFF0F1E2A),
    paperRaised = Color(0xFF152836),
    paperSunken = Color(0xFF1C3344),
    ink = Color(0xFFE8EFF2),
    inkSoft = Color(0xFFA3B7C2),
    inkFaint = Color(0xFF6A8291),
    line = Color(0xFF233B4C),
    accent = Dentoshoku.Kamenozoki,
    accentInk = Color(0xFFBDE7EC),
    accentContainer = Color(0xFF1F4456),
    onAccent = Color(0xFF0B1E2A),
    onAccentContainer = Color(0xFFD3F0F4),
    seal = Color(0xFFE07A64),
    scrim = Color(0x99000000),
    isDark = true,
    isPrivate = true,
)

/**
 * Muted traditional tones for site seals (monogram tiles). A site always maps
 * to the same tone, so it is recognizable without loading its favicon.
 */
internal val SealTones = listOf(
    Color(0xFF165E83), // 藍 ai
    Color(0xFF2792C3), // 縹 hanada
    Color(0xFF00A3AF), // 浅葱 asagi
    Color(0xFF5B8930), // 若竹 wakatake
    Color(0xFF8B81C3), // 藤 fuji
    Color(0xFFC8442E), // 朱 shu
    Color(0xFF8B7D3A), // 鶯 uguisu
    Color(0xFF6A4C9C), // 菫 sumire
    Color(0xFFB4866B), // 胡桃 kurumi
    Color(0xFF3A6E6A), // 青磁鼠 seijinezumi
)
