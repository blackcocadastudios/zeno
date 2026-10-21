package com.blackcocadastudios.zeno.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.blackcocadastudios.zeno.design.theme.Zeno

/** Shippori Mincho's x-height, as a fraction of the em (from the font's OS/2 table). */
private const val MINCHO_X_HEIGHT = 0.48f

/**
 * "Zen" set in mincho, with the ensō as its final "o": Zen + ō.
 */
@Composable
fun ZenoWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 40.sp,
    color: Color = Zeno.colors.ink,
    ensoColor: Color = Zeno.colors.accent,
    drawProgress: Float = 1f,
) {
    val measurer = rememberTextMeasurer()
    val style = Zeno.type.display.copy(fontSize = fontSize, lineHeight = fontSize * 1.2f, color = color)
    val layout = remember(style, measurer) { measurer.measure("Zen", style) }
    val density = LocalDensity.current
    val em = with(density) { fontSize.toPx() }
    val xHeight = em * MINCHO_X_HEIGHT
    val ensoSize = xHeight * 1.22f
    val gap = em * 0.05f
    val width = layout.size.width + gap + ensoSize
    val height = layout.size.height.toFloat()

    Canvas(
        modifier
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .semantics { contentDescription = "Zeno" },
    ) {
        drawText(layout)
        // The ensō sits on the baseline and rises a little above the x-height, like a round letter.
        val top = layout.firstBaseline - ensoSize + xHeight * 0.06f
        translate(left = layout.size.width + gap, top = top) {
            drawEnso(Size(ensoSize, ensoSize), ensoColor, drawProgress)
        }
    }
}
