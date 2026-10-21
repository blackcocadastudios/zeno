package com.blackcocadastudios.zeno.engine.gecko

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import com.blackcocadastudios.zeno.engine.EngineSession
import org.mozilla.geckoview.GeckoView

/**
 * Hosts the GeckoView that paints the selected tab. One instance lives in the
 * browser screen; switching tabs swaps the session it renders.
 */
@SuppressLint("ViewConstructor")
class GeckoEngineView(context: Context) : FrameLayout(context) {

    private val geckoView = GeckoView(context)

    init {
        addView(geckoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Shows [session], or nothing when null. */
    fun render(session: EngineSession?) {
        val gecko = (session as? GeckoEngineSession)?.geckoSession
        if (geckoView.session == gecko) return
        if (gecko == null) {
            geckoView.releaseSession()
        } else {
            geckoView.setSession(gecko)
        }
    }

    /** Releases the session before the view goes away, so it can be shown elsewhere. */
    fun release() {
        geckoView.releaseSession()
    }

    /** Paints [color] until the next page draws, avoiding a white flash in dark themes. */
    fun coverUntilFirstPaint(color: Int) = geckoView.coverUntilFirstPaint(color)

    /** Total height of toolbars that hide while scrolling; lets pages lay out under them. */
    fun setDynamicToolbarMaxHeight(height: Int) = geckoView.setDynamicToolbarMaxHeight(height)

    /** Height currently covered by the bottom toolbar, so fixed elements stay visible above it. */
    fun setVerticalClipping(clippingHeight: Int) = geckoView.setVerticalClipping(clippingHeight)

    /** Captures what is on screen, for the tab grid. Delivered on the main thread. */
    fun capture(onResult: (Bitmap?) -> Unit) {
        if (geckoView.session == null) {
            onResult(null)
            return
        }
        runCatching {
            geckoView.capturePixels().accept({ onResult(it) }, { onResult(null) })
        }.onFailure { onResult(null) }
    }
}
