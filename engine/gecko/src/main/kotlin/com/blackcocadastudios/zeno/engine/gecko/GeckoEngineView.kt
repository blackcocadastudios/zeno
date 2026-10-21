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
    private var current: GeckoEngineSession? = null

    init {
        addView(geckoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Gecko keeps its own state; the view must not try to restore a session after recreation.
        geckoView.isSaveEnabled = false
    }

    /**
     * Shows [session], or nothing when null. [coverColor] is painted until the
     * new page draws, so switching tabs in a dark theme does not flash white.
     */
    fun render(session: EngineSession?, coverColor: Int? = null) {
        val next = session as? GeckoEngineSession
        if (next === current || (next != null && next === waiting)) return
        waiting = null
        detach()
        if (next == null || next.isClosed) return
        if (!next.geckoSession.isOpen) {
            // A window the page just opened: GeckoView opens its session a moment later.
            waitForOpen(next, coverColor, attempt = 0)
            return
        }
        // A session can be shown by one view at a time.
        next.attachedView?.detach()
        coverColor?.let(geckoView::coverUntilFirstPaint)
        geckoView.setSession(next.geckoSession)
        next.attachedView = this
        current = next
    }

    private var waiting: GeckoEngineSession? = null

    private fun waitForOpen(session: GeckoEngineSession, coverColor: Int?, attempt: Int) {
        waiting = session
        postDelayed({
            if (waiting !== session) return@postDelayed
            when {
                session.isClosed -> waiting = null
                session.geckoSession.isOpen -> {
                    waiting = null
                    render(session, coverColor)
                }
                attempt < MAX_OPEN_WAITS -> waitForOpen(session, coverColor, attempt + 1)
                else -> waiting = null
            }
        }, OPEN_WAIT_MILLIS)
    }

    private companion object {
        const val OPEN_WAIT_MILLIS = 16L
        const val MAX_OPEN_WAITS = 120
    }

    /** Releases the session before the view goes away, so it can be shown elsewhere. */
    fun release() = detach()

    internal fun detach() {
        waiting = null
        val session = current ?: return
        current = null
        session.attachedView = null
        runCatching { geckoView.releaseSession() }
    }

    /** Total height of toolbars that hide while scrolling; lets pages lay out under them. */
    fun setDynamicToolbarMaxHeight(height: Int) = geckoView.setDynamicToolbarMaxHeight(height)

    /** Height currently covered by the bottom toolbar, so fixed elements stay visible above it. */
    fun setVerticalClipping(clippingHeight: Int) = geckoView.setVerticalClipping(clippingHeight)

    /** Captures what is on screen, for the tab grid. Delivered on the main thread. */
    fun capture(onResult: (Bitmap?) -> Unit) {
        if (current == null) {
            onResult(null)
            return
        }
        runCatching {
            geckoView.capturePixels().accept({ onResult(it) }, { onResult(null) })
        }.onFailure { onResult(null) }
    }
}
