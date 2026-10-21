package com.blackcocadastudios.zeno.engine

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A notification a site shows with the Notifications API. Sites get here only
 * with the user's permission, and only while their page or service worker runs:
 * there is no push service.
 */
class WebNotification(
    /** Identifies it: a notification with the same tag replaces this one. */
    val tag: String,
    val title: String,
    val body: String?,
    /** The site that shows it, e.g. `https://chat.example`. */
    val origin: String?,
    /** The page (or service worker) that showed it. */
    val source: String?,
    /** The site asked for no sound or vibration. */
    val silent: Boolean,
    val private: Boolean,
    private val onClick: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private val done = AtomicBoolean(false)

    /** The user tapped it: the site's page gets a click event (and usually asks to be shown). */
    fun click() {
        if (done.compareAndSet(false, true)) onClick()
    }

    /** It is gone without a tap (swiped away, or not shown at all). */
    fun dismiss() {
        if (done.compareAndSet(false, true)) onDismiss()
    }
}

/** Shows sites' notifications; the app decides whether and how. */
interface WebNotificationHandler {
    fun show(notification: WebNotification)

    /** The site closed it. */
    fun close(tag: String)
}

/** A site's service worker opens a window, e.g. after a click on its notification. */
fun interface WindowOpener {
    /** Shows [session], which will load [url], in a new tab. False if it cannot be shown. */
    fun open(session: EngineSession, url: String): Boolean
}
