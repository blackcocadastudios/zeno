package com.blackcocadastudios.zeno.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.blackcocadastudios.zeno.data.downloads.DownloadRecord
import com.blackcocadastudios.zeno.data.downloads.DownloadStatus
import com.blackcocadastudios.zeno.data.downloads.DownloadsRepository
import com.blackcocadastudios.zeno.engine.DownloadRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 *
 * Downloads from private tabs are listed from memory only: the file is saved
 * (that is what the user asked for), but no record of it reaches the disk, and
 * the list forgets them when the private tabs are closed.
 */
class DownloadCoordinator(
    private val context: Context,
    private val repository: DownloadsRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val jobs = HashMap<Long, Job>()

    private val privateRecords = MutableStateFlow<List<DownloadRecord>>(emptyList())
    private var nextPrivateId = -1L

    /** Every download to list: saved ones and, while they last, private ones. */
    val records: Flow<List<DownloadRecord>> = combine(repository.all, privateRecords) { saved, private ->
        (private + saved).sortedByDescending { it.createdAt }
    }

    private val _active = MutableStateFlow<Map<Long, ActiveDownload>>(emptyMap())
    val active: StateFlow<Map<Long, ActiveDownload>> = _active.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    fun enqueue(request: DownloadRequest) {
        val body = request.body ?: return
        scope.launch {
            val fileName = FileNames.resolve(request.fileName, request.url, request.contentType)
            val mimeType = FileNames.mimeTypeFor(fileName, request.contentType)
            val log = if (request.isPrivate) memoryLog else databaseLog
            val id = log.start(request.url, fileName, mimeType, request.contentLength)
            _active.update { it + (id to ActiveDownload(id, fileName, 0, request.contentLength)) }
            _events.tryEmit(DownloadEvent.Started(fileName))
            DownloadService.start(context)
            val job = scope.launch(Dispatchers.IO) { save(log, id, fileName, mimeType, request.contentLength, body) }
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

    /** Removes a download from the list (the file stays). */
    suspend fun delete(id: Long) {
        if (id < 0) privateRecords.update { list -> list.filterNot { it.id == id } } else repository.delete(id)
    }

    /** Removes every finished download from the list (the files stay). */
    suspend fun clearFinished() {
        privateRecords.update { list -> list.filter { it.status == DownloadStatus.Running } }
        repository.clearFinished()
    }

    /** The private tabs are gone: so is the list of what they downloaded. */
    fun forgetPrivate() {
        privateRecords.update { list -> list.filter { it.status == DownloadStatus.Running } }
    }

    private suspend fun save(log: DownloadLog, id: Long, fileName: String, mimeType: String, total: Long?, body: InputStream) {
        var target: Target? = null
        try {
            target = Target.create(context, fileName, mimeType)
            log.setTarget(id, target.uri.toString(), target.displayName)
            body.use { input ->
                target.open(context).use { output -> copy(log, id, input, output, total) }
            }
            target.publish(context)
            withContext(NonCancellable) { log.finish(id, DownloadStatus.Completed, target.uri.toString()) }
            _events.tryEmit(DownloadEvent.Completed(id, target.displayName, target.uri, mimeType))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                target?.discard(context)
                log.finish(id, DownloadStatus.Cancelled)
            }
            throw cancelled
        } catch (error: Exception) {
            withContext(NonCancellable) {
                target?.discard(context)
                log.finish(id, DownloadStatus.Failed)
            }
            _events.tryEmit(DownloadEvent.Failed(fileName))
        }
    }

    private suspend fun copy(log: DownloadLog, id: Long, input: InputStream, output: OutputStream, total: Long?) {
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
                log.progress(id, written, total)
                _active.update { map -> map[id]?.let { map + (id to it.copy(bytes = written)) } ?: map }
            }
        }
        if (!currentCoroutineContextActive()) throw CancellationException("Download cancelled")
        log.progress(id, written, total ?: written)
    }

    /** Where a download's progress is written down. */
    private interface DownloadLog {
        suspend fun start(url: String, fileName: String, mimeType: String, total: Long?): Long
        suspend fun setTarget(id: Long, uri: String, fileName: String)
        suspend fun progress(id: Long, bytes: Long, total: Long?)
        suspend fun finish(id: Long, status: DownloadStatus, uri: String? = null)
    }

    private val databaseLog = object : DownloadLog {
        override suspend fun start(url: String, fileName: String, mimeType: String, total: Long?) =
            repository.start(url, fileName, mimeType, total)

        override suspend fun setTarget(id: Long, uri: String, fileName: String) = repository.setTarget(id, uri, fileName)

        override suspend fun progress(id: Long, bytes: Long, total: Long?) = repository.progress(id, bytes, total)

        override suspend fun finish(id: Long, status: DownloadStatus, uri: String?) = repository.finish(id, status, uri)
    }

    /** Private tabs: memory only, and without the address the file came from. */
    private val memoryLog = object : DownloadLog {
        override suspend fun start(url: String, fileName: String, mimeType: String, total: Long?): Long {
            val id = nextPrivateId--
            val record = DownloadRecord(
                id = id,
                url = "",
                fileName = fileName,
                mimeType = mimeType,
                totalBytes = total,
                downloadedBytes = 0,
                status = DownloadStatus.Running,
                contentUri = null,
                createdAt = clock(),
                completedAt = null,
            )
            privateRecords.update { it + record }
            return id
        }

        override suspend fun setTarget(id: Long, uri: String, fileName: String) =
            change(id) { it.copy(contentUri = uri, fileName = fileName) }

        override suspend fun progress(id: Long, bytes: Long, total: Long?) =
            change(id) { it.copy(downloadedBytes = bytes, totalBytes = total ?: it.totalBytes) }

        override suspend fun finish(id: Long, status: DownloadStatus, uri: String?) = change(id) {
            it.copy(status = status, contentUri = uri ?: it.contentUri, completedAt = if (status == DownloadStatus.Running) null else clock())
        }

        private fun change(id: Long, transform: (DownloadRecord) -> DownloadRecord) {
            privateRecords.update { list -> list.map { if (it.id == id) transform(it) else it } }
        }
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

            @RequiresApi(Build.VERSION_CODES.Q)
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
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
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
