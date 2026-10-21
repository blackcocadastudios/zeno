package com.blackcocadastudios.zeno.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackcocadastudios.zeno.design.theme.SealTones
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes

/** Stable tone for a key (e.g. a site's host): the same site always gets the same seal. */
fun sealTone(key: String): Color = SealTones[Math.floorMod(key.hashCode(), SealTones.size)]

/**
 * 印 A site's seal: one mincho letter on a soft tint, like a hanko stamp.
 * Zeno shows seals instead of fetching favicons — quieter, faster, private.
 *
 * @param letter usually the first letter of the site's name.
 * @param key decides the tone; use the host so a site's seal never changes.
 */
@Composable
fun Seal(
    letter: String,
    key: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = Zeno.colors
    val tone = remember(key) { sealTone(key) }
    val container = tone.copy(alpha = if (colors.isDark) 0.24f else 0.12f).compositeOver(colors.paperRaised)
    val ink = if (colors.isDark) lerp(tone, Color.White, 0.55f) else lerp(tone, Color.Black, 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .clip(ZenoShapes.seal)
            .background(container)
            .border(1.dp, ink.copy(alpha = 0.16f), ZenoShapes.seal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.take(1).uppercase(),
            style = Zeno.type.display.copy(
                fontSize = (size.value * 0.44f).sp,
                lineHeight = (size.value * 0.5f).sp,
                color = ink,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
