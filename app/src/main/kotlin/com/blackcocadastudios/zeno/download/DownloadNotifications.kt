package com.blackcocadastudios.zeno.download

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.blackcocadastudios.zeno.R

internal object DownloadNotifications {
    const val CHANNEL = "downloads"
    const val PROGRESS_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.download_channel))
            .setDescription(context.getString(R.string.download_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun progress(context: Context, active: Collection<ActiveDownload>): Notification {
        ensureChannel(context)
        val first = active.firstOrNull()
        val percent = first?.total?.takeIf { it > 0 }?.let { ((first.bytes * 100) / it).toInt().coerceIn(0, 100) }
        val title = first?.fileName ?: context.getString(R.string.downloads_running)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (active.size > 1) "+${active.size - 1}" else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (percent != null) builder.setProgress(100, percent, false) else builder.setProgress(0, 0, true)
        if (first != null) {
            val cancel = PendingIntent.getService(
                context,
                first.id.toInt(),
                DownloadService.cancelIntent(context, first.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.download_cancel), cancel)
        }
        return builder.build()
    }

    fun completed(context: Context, id: Long, fileName: String, uri: Uri, mimeType: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            id.toInt(),
            openIntent(uri, mimeType),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(fileName)
            .setContentText(context.getString(R.string.download_complete))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.toInt() + PROGRESS_ID + 1, notification)
        } catch (_: SecurityException) {
            // Notifications not allowed: the download list still shows it.
        }
    }

    fun openIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
