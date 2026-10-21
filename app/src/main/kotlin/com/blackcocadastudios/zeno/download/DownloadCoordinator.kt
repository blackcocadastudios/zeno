package com.blackcocadastudios.zeno.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.blackcocadastudios.zeno.data.downloads.DownloadStatus
import com.blackcocadastudios.zeno.data.downloads.DownloadsRepository
import com.blackcocadastudios.zeno.engine.DownloadRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** What the interface shows about downloads (toasts, the notification). */
sealed interface DownloadEvent {
    data class Started(val fileName: String) : DownloadEvent
    data class Completed(val id: Long, val fileName: String, val uri: Uri, val mimeType: String) : DownloadEvent
    data class Failed(val fileName: String) : DownloadEvent
}

data class ActiveDownload(val id: Long, val fileName: String, val bytes: Long, val total: Long?)

/**
 * Saves files the engine hands over into the public Downloads folder. The
 * bytes come straight from the response Gecko already made (with the page's
 * cookies), so nothing is downloaded twice.
 */
class DownloadCoordinator(
    private val context: Context,
    private val repository: DownloadsRepository,
    private val scope: CoroutineScope,
) {
    private val jobs = HashMap<Long, Job>()

    private val _active = MutableStateFlow<Map<Long, ActiveDownload>>(emptyMap())
    val active: StateFlow<Map<Long, ActiveDownload>> = _active.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    fun enqueue(request: DownloadRequest) {
        val body = request.body ?: return
        scope.launch {
            val fileName = FileNames.resolve(request.fileName, request.url, request.contentType)
            val mimeType = FileNames.mimeTypeFor(fileName, request.contentType)
            val id = repository.start(request.url, fileName, mimeType, request.contentLength)
            _active.update { it + (id to ActiveDownload(id, fileName, 0, request.contentLength)) }
            _events.tryEmit(DownloadEvent.Started(fileName))
            DownloadService.start(context)
            val job = scope.launch(Dispatchers.IO) { save(id, fileName, mimeType, request.contentLength, body) }
            jobs[id] = job
            job.invokeOnCompletion {
                scope.launch {
                    jobs.remove(id)
                    _active.update { it - id }
                }
            }
        }
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
    }

    private suspend fun save(id: Long, fileName: String, mimeType: String, total: Long?, body: InputStream) {
        var target: Target? = null
        try {
            target = Target.create(context, fileName, mimeType)
            repository.setTarget(id, target.uri.toString(), target.displayName)
            body.use { input ->
                target.open(context).use { output -> copy(id, input, output, total) }
            }
            target.publish(context)
            withContext(NonCancellable) { repository.finish(id, DownloadStatus.Completed, target.uri.toString()) }
            _events.tryEmit(DownloadEvent.Completed(id, target.displayName, target.uri, mimeType))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                target?.discard(context)
                repository.finish(id, DownloadStatus.Cancelled)
            }
            throw cancelled
        } catch (error: Exception) {
            withContext(NonCancellable) {
                target?.discard(context)
                repository.finish(id, DownloadStatus.Failed)
            }
            _events.tryEmit(DownloadEvent.Failed(fileName))
        }
    }

    private suspend fun copy(id: Long, input: InputStream, output: OutputStream, total: Long?) {
        val buffer = ByteArray(64 * 1024)
        var written = 0L
        var lastReport = 0L
        while (currentCoroutineContextActive()) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read
            val now = System.currentTimeMillis()
            if (now - lastReport > 500) {
                lastReport = now
                repository.progress(id, written, total)
                _active.update { map -> map[id]?.let { map + (id to it.copy(bytes = written)) } ?: map }
            }
        }
        if (!currentCoroutineContextActive()) throw CancellationException("Download cancelled")
        repository.progress(id, written, total ?: written)
    }

    private suspend fun currentCoroutineContextActive(): Boolean = kotlin.coroutines.coroutineContext.isActive

    /** Where a download is written: MediaStore on Android 10+, the app's folder before. */
    private class Target(val uri: Uri, val displayName: String, private val file: File?) {

        fun open(context: Context): OutputStream =
            if (file != null) file.outputStream()
            else context.contentResolver.openOutputStream(uri) ?: error("Cannot open $uri")

        fun publish(context: Context) {
            if (file == null && Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
            }
        }

        fun discard(context: Context) {
            runCatching {
                if (file != null) file.delete() else context.contentResolver.delete(uri, null, null)
            }
        }

        companion object {
            fun create(context: Context, fileName: String, mimeType: String): Target =
                if (Build.VERSION.SDK_INT >= 29) mediaStore(context, fileName, mimeType) else legacy(context, fileName)

            private fun mediaStore(context: Context, fileName: String, mimeType: String): Target {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore refused $fileName")
                // MediaStore renames on conflicts ("file (1).pdf"); show the real name.
                val actualName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: fileName
                return Target(uri, actualName, file = null)
            }

            private fun legacy(context: Context, fileName: String): Target {
                val directory = ContextCompat.getExternalFilesDirs(context, Environment.DIRECTORY_DOWNLOADS).firstOrNull()
                    ?: File(context.filesDir, "downloads")
                directory.mkdirs()
                var file = File(directory, fileName)
                var counter = 1
                val base = fileName.substringBeforeLast('.')
                val extension = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
                while (file.exists()) file = File(directory, "$base (${counter++})$extension")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                return Target(uri, file.name, file)
            }
        }
    }
}
