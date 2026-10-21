package com.blackcocadastudios.zeno.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.blackcocadastudios.zeno.design.R

/**
 * Zen Kaku Gothic New: a Japanese gothic whose Latin is light and precise.
 * Used for all interface text. Subset to Latin + a few kanji (see third_party/fonts).
 */
val ZenKakuGothic = FontFamily(
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_medium, FontWeight.Medium),
    Font(R.font.zen_kaku_gothic_new_bold, FontWeight.Bold),
)

/** Shippori Mincho: brush-derived serif for the wordmark and quiet display lines. */
val ShipporiMincho = FontFamily(
    Font(R.font.shippori_mincho_regular, FontWeight.Normal),
    Font(R.font.shippori_mincho_semibold, FontWeight.SemiBold),
)

private val Centered = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Immutable
data class ZenoTypography(
    /** Wordmark-sized mincho. */
    val display: TextStyle,
    /** Screen titles. */
    val title: TextStyle,
    /** Section and sheet headings. */
    val heading: TextStyle,
    /** Default reading text. */
    val body: TextStyle,
    /** Secondary lines, descriptions. */
    val bodySmall: TextStyle,
    /** Buttons, menu items, tabs. */
    val label: TextStyle,
    /** Metadata, counters. */
    val caption: TextStyle,
    /** Address bar text. */
    val url: TextStyle,
    /** Mincho for quiet, poetic lines (empty states, home greeting). */
    val mincho: TextStyle,
)

val DefaultTypography = ZenoTypography(
    display = TextStyle(
        fontFamily = ShipporiMincho,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.02.em,
        lineHeightStyle = Centered,
    ),
    title = TextStyle(
        fontFamily = ZenKakuGothic,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = Centered,
    ),
    heading = TextStyle(
        fontFamily = ZenKakuGothic,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = Centered,
    ),
    body = TextStyle(
        fontFamily = ZenKakuGothic,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = Centered,
    ),
    bodySmall = TextStyle(
        fontFamily = ZenKakuGothic,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = Centered,
    ),
    label = TextStyle(
        fontFamily = ZenKakuGothic,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.02.em,
        lineHeightStyle = Centered,
    ),
    caption = TextStyle(
        fontFamily = ZenKakuGothic,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.03.em,
        lineHeightStyle = Centered,
    ),
    url = TextStyle(
        fontFamily = ZenKakuGothic,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = Centered,
    ),
    mincho = TextStyle(
        fontFamily = ShipporiMincho,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.02.em,
        lineHeightStyle = Centered,
    ),
)
