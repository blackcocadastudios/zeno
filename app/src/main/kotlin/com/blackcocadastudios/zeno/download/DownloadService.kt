package com.blackcocadastudios.zeno.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.blackcocadastudios.zeno.ZenoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while files are being saved, with one quiet
 * progress notification. Stops itself as soon as nothing is downloading.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coordinator get() = (application as ZenoApplication).container.downloadCoordinator

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        scope.launch {
            coordinator.active.collect { active ->
                if (active.isEmpty()) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    try {
                        NotificationManagerCompat.from(this@DownloadService)
                            .notify(DownloadNotifications.PROGRESS_ID, DownloadNotifications.progress(this@DownloadService, active.values))
                    } catch (_: SecurityException) {
                        // Notifications not allowed: the foreground service still keeps the download alive.
                    }
                }
            }
        }
        scope.launch {
            coordinator.events.collect { event ->
                if (event is DownloadEvent.Completed) {
                    DownloadNotifications.completed(this@DownloadService, event.id, event.fileName, event.uri, event.mimeType)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent?.action == ACTION_CANCEL) coordinator.cancel(intent.getLongExtra(EXTRA_ID, -1))
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val notification = DownloadNotifications.progress(this, coordinator.active.value.values)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, DownloadNotifications.PROGRESS_ID, notification, type)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_CANCEL = "com.blackcocadastudios.zeno.download.CANCEL"
        private const val EXTRA_ID = "id"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }

        fun cancelIntent(context: Context, id: Long): Intent =
            Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id)
    }
}
