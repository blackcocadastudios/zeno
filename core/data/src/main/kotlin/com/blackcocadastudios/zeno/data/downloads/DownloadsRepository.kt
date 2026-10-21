package com.blackcocadastudios.zeno.data.downloads

import com.blackcocadastudios.zeno.data.db.DownloadDao
import com.blackcocadastudios.zeno.data.db.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DownloadStatus(val code: Int) {
    Running(0),
    Completed(1),
    Failed(2),
    Cancelled(3),
    ;

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: Failed
    }
}

data class DownloadRecord(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val contentUri: String?,
    val createdAt: Long,
    val completedAt: Long?,
) {
    /** 0..1, or null when the size is unknown. */
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

class DownloadsRepository(
    private val dao: DownloadDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val all: Flow<List<DownloadRecord>> = dao.all().map { list -> list.map { it.toRecord() } }

    suspend fun start(url: String, fileName: String, mimeType: String?, totalBytes: Long?): Long =
        dao.insert(
            DownloadEntity(
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                totalBytes = totalBytes,
                downloadedBytes = 0,
                status = DownloadStatus.Running.code,
                contentUri = null,
                createdAt = clock(),
                completedAt = null,
            ),
        )

    suspend fun setTarget(id: Long, contentUri: String, fileName: String) = dao.setTarget(id, contentUri, fileName)

    suspend fun progress(id: Long, bytes: Long, total: Long?) = dao.updateProgress(id, bytes, total)

    suspend fun finish(id: Long, status: DownloadStatus, contentUri: String? = null) =
        dao.finish(id, status.code, contentUri, if (status == DownloadStatus.Running) null else clock())

    suspend fun get(id: Long): DownloadRecord? = dao.get(id)?.toRecord()

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearFinished() = dao.clearFinished()

    /** Call once at startup: anything still "running" died with the previous process. */
    suspend fun markInterrupted() = dao.failInterrupted()
}

private fun DownloadEntity.toRecord() = DownloadRecord(
    id = id,
    url = url,
    fileName = fileName,
    mimeType = mimeType,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    status = DownloadStatus.of(status),
    contentUri = contentUri,
    createdAt = createdAt,
    completedAt = completedAt,
)
