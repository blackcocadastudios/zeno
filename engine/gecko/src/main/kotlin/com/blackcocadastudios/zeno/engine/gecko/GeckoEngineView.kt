package com.blackcocadastudios.zeno.engine.gecko

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import com.blackcocadastudios.zeno.engine.EngineSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController

/**
 * Hosts the GeckoView that paints the selected tab. One instance lives in the
 * browser screen; switching tabs swaps the session it renders.
 */
@SuppressLint("ViewConstructor")
class GeckoEngineView(context: Context) : FrameLayout(context) {

    /** Finger drags over a page that Gecko scrolls (not a map or a game that keeps the touches). */
    interface ScrollListener {
        /** The finger moved [dy] pixels: positive when it goes up and the page scrolls down. */
        fun onScroll(dy: Float)

        /** The finger pulls down a page that is already at its top; [distance] is the pull so far. */
        fun onPull(distance: Float)

        /** The finger lifted (the page may keep gliding). */
        fun onScrollEnd()
    }

    /** Set by the interface to move a toolbar with the page. */
    var scrollListener: ScrollListener? = null

    private val geckoView: GeckoView = TouchReportingGeckoView(context)
    private var current: GeckoEngineSession? = null
    private var waiting: GeckoEngineSession? = null

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

    /** Releases the session before the view goes away, so it can be shown elsewhere. */
    fun release() = detach()

    internal fun detach() {
        waiting = null
        val session = current ?: return
        current = null
        session.attachedView = null
        runCatching { geckoView.releaseSession() }
    }

    /**
     * Height of the toolbar that slides away while scrolling, in pixels, or 0
     * when the toolbar is fixed. The view must then extend under that toolbar.
     */
    fun setDynamicToolbarMaxHeight(height: Int) = geckoView.setDynamicToolbarMaxHeight(height)

    /** Pixels currently covered by the bottom toolbar (negative), so fixed elements stay above it. */
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

    /**
     * Asks Gecko, at the start of each touch, whether the page will scroll
     * (and in which directions) or keep the touches for itself, then reports
     * the finger's vertical movement only when the page really scrolls.
     */
    private inner class TouchReportingGeckoView(context: Context) : GeckoView(context) {
        private var detail: PanZoomController.InputResultDetail? = null
        private var lastY = 0f
        private var tracking = false
        private var pull = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            follow(event)
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
            // Same as onTouchEvent for Gecko, plus the answer about this touch.
            detail = null
            onTouchEventForDetailResult(event).accept({ result ->
                detail = result
                if (result != null && Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(
                        TAG,
                        "touch: handled=${result.handledResult()} scrollable=${result.scrollableDirections()} " +
                            "overscroll=${result.overscrollDirections()}",
                    )
                }
            }, { detail = null })
            return true
        }

        private fun follow(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    tracking = true
                    pull = 0f
                }
                // A second finger means zooming: the toolbar stays where it is.
                MotionEvent.ACTION_POINTER_DOWN -> if (tracking) {
                    tracking = false
                    if (pull > 0f) {
                        pull = 0f
                        scrollListener?.onPull(0f)
                    }
                }
                MotionEvent.ACTION_MOVE -> if (tracking) {
                    val dy = lastY - event.y
                    lastY = event.y
                    when {
                        dy == 0f -> Unit
                        pull > 0f || (dy < 0f && canPull()) -> {
                            pull = (pull - dy).coerceAtLeast(0f)
                            scrollListener?.onPull(pull)
                        }
                        pageScrolls(dy) -> scrollListener?.onScroll(dy)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (tracking) {
                    tracking = false
                    scrollListener?.onScrollEnd()
                    pull = 0f
                }
            }
        }

        /** At the top already, and Gecko lets the page be pulled further (overscroll). */
        private fun canPull(): Boolean {
            val result = detail ?: return false
            if (result.handledResult() != PanZoomController.INPUT_RESULT_HANDLED) return false
            val atTop = result.scrollableDirections() and PanZoomController.SCROLLABLE_FLAG_TOP == 0
            val overscrolls = result.overscrollDirections() and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0
            return atTop && overscrolls
        }

        private fun pageScrolls(dy: Float): Boolean {
            val result = detail ?: return false
            if (result.handledResult() != PanZoomController.INPUT_RESULT_HANDLED) return false
            val towards = if (dy > 0) PanZoomController.SCROLLABLE_FLAG_BOTTOM else PanZoomController.SCROLLABLE_FLAG_TOP
            return result.scrollableDirections() and towards != 0
        }
    }

    private companion object {
        const val OPEN_WAIT_MILLIS = 16L
        const val MAX_OPEN_WAITS = 120

        /** `adb shell setprop log.tag.ZenoTouch DEBUG` shows what Gecko says about each touch. */
        const val TAG = "ZenoTouch"
    }
}
