package com.blackcocadastudios.zeno.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.blackcocadastudios.zeno.design.theme.Zeno
import kotlin.math.min

/**
 * The ensō (円相) brush stroke in a 108-unit viewport — the same geometry as the
 * launcher icon. Regenerate with tools/design/enso.py.
 */
internal const val ENSO_PATH_DATA =
    "M32.79 66.32 C32.42 65.72 31.29 63.99 30.58 62.74 C29.87 61.50 29.09 60.21 28.56 58.84 C28.02 57.46 27.55 55.95 27.40 54.47 C27.24 52.99 27.41 51.43 27.64 49.95 C27.88 48.47 28.29 46.98 28.80 45.58 C29.32 44.17 29.97 42.79 30.72 41.51 C31.48 40.23 32.36 39.00 33.32 37.89 C34.29 36.77 35.36 35.73 36.50 34.81 C37.63 33.89 38.86 33.07 40.12 32.37 C41.38 31.67 42.71 31.08 44.05 30.61 C45.40 30.15 46.79 29.80 48.18 29.57 C49.56 29.35 50.97 29.24 52.36 29.25 C53.74 29.25 55.13 29.38 56.48 29.61 C57.82 29.85 59.15 30.20 60.43 30.64 C61.70 31.08 62.94 31.64 64.11 32.27 C65.29 32.91 66.41 33.64 67.46 34.45 C68.50 35.26 69.49 36.16 70.38 37.11 C71.28 38.06 72.10 39.10 72.83 40.18 C73.56 41.26 74.20 42.40 74.75 43.58 C75.29 44.75 75.75 45.98 76.10 47.22 C76.45 48.46 76.70 49.75 76.85 51.03 C76.99 52.31 77.04 53.61 76.97 54.90 C76.91 56.18 76.74 57.48 76.47 58.74 C76.20 59.99 75.83 61.25 75.35 62.44 C74.88 63.64 74.30 64.81 73.64 65.92 C72.97 67.02 72.21 68.08 71.37 69.07 C70.52 70.05 69.56 70.99 68.54 71.80 C67.52 72.61 66.41 73.36 65.25 73.93 C64.09 74.51 62.84 74.96 61.60 75.25 C60.36 75.55 59.06 75.67 57.82 75.69 C56.57 75.71 55.32 75.58 54.13 75.37 C52.94 75.16 51.78 74.83 50.68 74.43 C49.58 74.04 48.53 73.55 47.53 73.02 C46.53 72.49 45.58 71.89 44.67 71.27 C43.76 70.64 42.50 69.61 42.06 69.27 L42.26 69.04 C42.70 69.34 43.98 70.34 44.92 70.85 C45.87 71.37 46.88 71.81 47.90 72.15 C48.92 72.48 50.00 72.73 51.07 72.88 C52.13 73.03 53.23 73.08 54.30 73.05 C55.37 73.01 56.45 72.88 57.50 72.67 C58.54 72.46 59.57 72.16 60.56 71.80 C61.55 71.44 62.52 71.01 63.45 70.51 C64.37 70.01 65.27 69.44 66.08 68.78 C66.89 68.12 67.64 67.35 68.31 66.54 C68.98 65.73 69.58 64.82 70.09 63.90 C70.61 62.98 71.04 62.00 71.38 61.01 C71.72 60.02 71.97 58.99 72.13 57.96 C72.30 56.94 72.37 55.88 72.35 54.86 C72.34 53.83 72.23 52.79 72.05 51.78 C71.86 50.78 71.59 49.79 71.25 48.83 C70.90 47.88 70.47 46.95 69.98 46.08 C69.49 45.21 68.92 44.37 68.29 43.59 C67.67 42.81 66.98 42.08 66.25 41.41 C65.51 40.75 64.72 40.14 63.89 39.60 C63.07 39.07 62.19 38.59 61.29 38.19 C60.39 37.80 59.45 37.47 58.51 37.22 C57.56 36.97 56.58 36.80 55.61 36.70 C54.64 36.61 53.64 36.59 52.67 36.65 C51.69 36.71 50.71 36.85 49.75 37.07 C48.80 37.29 47.85 37.59 46.94 37.96 C46.03 38.33 45.14 38.79 44.30 39.30 C43.47 39.82 42.66 40.42 41.92 41.07 C41.17 41.72 40.47 42.44 39.85 43.21 C39.22 43.98 38.65 44.81 38.16 45.68 C37.67 46.54 37.25 47.46 36.91 48.40 C36.57 49.34 36.32 50.33 36.13 51.31 C35.95 52.30 35.88 53.31 35.81 54.33 C35.75 55.35 35.68 56.39 35.73 57.43 C35.79 58.47 35.89 59.56 36.16 60.59 C36.43 61.61 37.16 63.07 37.36 63.57 A2.67 2.67 0 0 1 32.79 66.32Z"

/** Where the brush lands, in degrees on screen (clockwise from 3 o'clock). */
private const val ENSO_START_DEGREES = 150f
private const val ENSO_SWEEP_DEGREES = 345f
private val ENSO_CENTER = Offset(54f, 54f)

private val EnsoPath: Path by lazy { PathParser().parsePathString(ENSO_PATH_DATA).toPath() }
private val EnsoBounds: Rect by lazy { EnsoPath.getBounds() }

/**
 * Zeno's mark. [drawProgress] reveals the stroke along the brush direction,
 * so the mark can be drawn in, the way it was painted.
 */
@Composable
fun Enso(
    modifier: Modifier = Modifier,
    color: Color = Zeno.colors.accent,
    drawProgress: Float = 1f,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.then(semantics)) {
        drawEnso(size, color, drawProgress)
    }
}

/** Draws the ensō fitted and centered inside [area], starting at the current origin. */
fun DrawScope.drawEnso(area: Size, color: Color, drawProgress: Float = 1f) {
    val bounds = EnsoBounds
    val scale = min(area.width / bounds.width, area.height / bounds.height)
    val dx = (area.width - bounds.width * scale) / 2f - bounds.left * scale
    val dy = (area.height - bounds.height * scale) / 2f - bounds.top * scale
    withTransform({
        translate(left = dx, top = dy)
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    }) {
        val progress = drawProgress.coerceIn(0f, 1f)
        if (progress >= 1f) {
            drawPath(EnsoPath, color)
        } else if (progress > 0f) {
            val reach = 70f
            val wedge = Path().apply {
                moveTo(ENSO_CENTER.x, ENSO_CENTER.y)
                arcTo(
                    rect = Rect(center = ENSO_CENTER, radius = reach),
                    startAngleDegrees = ENSO_START_DEGREES - 8f,
                    sweepAngleDegrees = (ENSO_SWEEP_DEGREES + 8f) * progress,
                    forceMoveTo = false,
                )
                close()
            }
            clipPath(wedge) { drawPath(EnsoPath, color) }
        }
    }
}
