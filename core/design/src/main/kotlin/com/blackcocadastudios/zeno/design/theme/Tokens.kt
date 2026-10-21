package com.blackcocadastudios.zeno.design.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 間 Ma — the meaningful space between things. Every gap in Zeno comes from
 * this scale; if a layout needs another value, the layout is wrong.
 */
object Ma {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val huge = 64.dp

    /** Minimum touch target. */
    val touch = 48.dp
}

object ZenoShapes {
    val pill = RoundedCornerShape(percent = 50)
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val seal = RoundedCornerShape(12.dp)
}

/** Motion is calm: things settle like water, never bounce. */
object ZenoMotion {
    val Calm: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val QUICK = 150
    const val STANDARD = 240
    const val GENTLE = 360
}
