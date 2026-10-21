package com.blackcocadastudios.zeno.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.design.theme.Zeno
import kotlin.math.max

/**
 * 青海波 Seigaiha — "waves of the blue sea": overlapping fans of concentric
 * arcs. Drawn faint and fading upwards, as a quiet horizon.
 */
@Composable
fun Seigaiha(
    modifier: Modifier = Modifier,
    color: Color = Zeno.colors.accent,
    background: Color = Zeno.colors.paper,
    radius: Dp = 22.dp,
    rings: Int = 4,
    lineAlpha: Float = 0.26f,
    fadeFromTop: Boolean = true,
) {
    Canvas(
        modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val r = radius.toPx()
        val stroke = max(1f, 0.8.dp.toPx())
        val line = color.copy(alpha = lineAlpha)
        var row = 0
        var y = 0f
        // Rows step down half a radius; each row shifts by one radius; later rows cover earlier ones.
        while (y - r <= size.height) {
            var x = if (row % 2 == 0) 0f else r
            x -= 2 * r
            while (x - r <= size.width) {
                val center = Offset(x, y)
                drawCircle(background, r, center)
                for (k in 0 until rings) {
                    val ring = r * (1f - k.toFloat() / rings)
                    drawCircle(line, ring - stroke / 2f, center, style = Stroke(stroke))
                }
                x += 2 * r
            }
            y += r / 2f
            row++
        }
        if (fadeFromTop) {
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}
