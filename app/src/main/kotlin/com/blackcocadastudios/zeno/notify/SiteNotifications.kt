package com.blackcocadastudios.zeno.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blackcocadastudios.zeno.IntentProcessor
import com.blackcocadastudios.zeno.MainActivity
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.engine.WebNotification
import com.blackcocadastudios.zeno.engine.WebNotificationHandler
import java.util.UUID

/**
 * Shows notifications from sites the user allowed, while the setting is on.
 * Zeno has no push service, so a site can only notify while its page (or its
 * service worker) is running; notifications from private tabs are never shown.
 */
class SiteNotifications(
    private val context: Context,
    private val enabled: () -> Boolean,
) : WebNotificationHandler {
    /** Shown and not yet tapped, by tag. Kept small: old ones are simply forgotten. */
    private val shown = LinkedHashMap<String, WebNotification>()

    /** Proves a tap came from one of Zeno's own notifications, not from another app. */
    private val token = UUID.randomUUID().toString()

    override fun show(notification: WebNotification) {
        if (!enabled() || notification.private) {
            notification.dismiss()
            return
        }
        ensureChannel()
        shown.remove(notification.tag)
        shown[notification.tag] = notification
        if (shown.size > MAX_KEPT) shown.remove(shown.keys.first())

        val site = notification.origin?.let(UrlDisplay::shortHost)
        val open = PendingIntent.getActivity(
            context,
            notification.tag.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(IntentProcessor.ACTION_SITE_NOTIFICATION)
                .putExtra(IntentProcessor.EXTRA_NOTIFICATION_TAG, notification.tag)
                .putExtra(IntentProcessor.EXTRA_NOTIFICATION_TOKEN, token),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val built = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title.ifBlank { site })
            .setContentText(notification.body)
            .setStyle(notification.body?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setSubText(site)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(notification.silent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notification.tag, NOTIFICATION_ID, built)
        } catch (_: SecurityException) {
            // Android does not let Zeno notify (13+ without the permission): nothing to show.
        }
    }

    override fun close(tag: String) {
        shown.remove(tag)
        NotificationManagerCompat.from(context).cancel(tag, NOTIFICATION_ID)
    }

    /** The notification the user tapped, if [token] proves it is one of Zeno's. */
    fun tapped(tag: String?, token: String?): WebNotification? {
        if (tag == null || token != this.token) return null
        return shown.remove(tag)
    }

    /** The setting was turned off: nothing from sites stays on screen. */
    fun cancelAll() {
        val manager = NotificationManagerCompat.from(context)
        shown.keys.forEach { manager.cancel(it, NOTIFICATION_ID) }
        shown.values.forEach { it.dismiss() }
        shown.clear()
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_sites))
            .setDescription(context.getString(R.string.notification_channel_sites_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "sites"

        /** Site notifications are told apart by their tag; the id is the same for all. */
        const val NOTIFICATION_ID = 3001
        const val MAX_KEPT = 50
    }
}
