package com.sumanonline.sumanmovies.data.download

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.sumanonline.sumanmovies.data.local.dao.DownloadDao
import com.sumanonline.sumanmovies.data.local.entities.DownloadEntity
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadProgress(
    val id: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val status: String = "DOWNLOADING"
)

class AppDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val preferencesManager: com.sumanonline.sumanmovies.data.local.AppPreferencesManager? = null
) {
    private val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _progressFlow = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progressFlow: StateFlow<Map<String, DownloadProgress>> = _progressFlow.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getDownloadsFlow(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    fun getCompletedDownloadsCountFlow(): Flow<Int> = downloadDao.getCompletedDownloadsCount()
    fun getTotalDownloadsCountFlow(): Flow<Int> = downloadDao.getTotalDownloadsCount()

    private fun getStorageDir(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "SumanMovies")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun buildCleanFileName(title: String, season: Int?, episode: Int?, quality: String, ext: String): String {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(60)
        val epPart = if (season != null && episode != null) "_S${season}E${episode}" else ""
        val cleanQuality = quality.replace(Regex("[^a-zA-Z0-9]"), "")
        return "${cleanTitle}${epPart}_${cleanQuality}$ext"
    }

    suspend fun enqueueDownload(
        item: MediaItem,
        stream: StreamSource,
        episode: Episode? = null
    ): Long = withContext(Dispatchers.IO) {
        val ext = if (stream.format.equals("MKV", ignoreCase = true) || stream.url.contains(".mkv")) ".mkv" else ".mp4"
        val fileName = buildCleanFileName(item.title, episode?.seasonNumber, episode?.episodeNumber, stream.quality, ext)
        val entityId = if (episode != null) "${item.id}-S${episode.seasonNumber}E${episode.episodeNumber}" else item.id

        val targetFile = File(getStorageDir(), fileName)
        val downloadEntity = DownloadEntity(
            id = entityId,
            downloadId = System.currentTimeMillis(),
            mediaId = item.id,
            title = if (episode != null) "${item.title} - S${episode.seasonNumber}E${episode.episodeNumber}" else item.title,
            mediaType = item.mediaType.name,
            posterUrl = episode?.stillUrl ?: item.posterUrl,
            streamUrl = stream.url,
            localFilePath = targetFile.absolutePath,
            quality = stream.quality,
            fileSizeBytes = stream.sizeBytes ?: 0L,
            downloadedBytes = 0L,
            status = "DOWNLOADING",
            seasonNumber = episode?.seasonNumber ?: 1,
            episodeNumber = episode?.episodeNumber ?: 1,
            createdAt = System.currentTimeMillis()
        )

        downloadDao.insert(downloadEntity)

        activeJobs[entityId]?.cancel()
        val job = downloadScope.launch {
            runDownloadLoop(downloadEntity, targetFile, stream.headers)
        }
        activeJobs[entityId] = job

        downloadEntity.downloadId
    }

    suspend fun pauseDownload(id: String) = withContext(Dispatchers.IO) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        downloadDao.updateStatusById(id, "PAUSED")

        val current = _progressFlow.value[id]
        if (current != null) {
            val updated = _progressFlow.value.toMutableMap()
            updated[id] = current.copy(status = "PAUSED", speedBytesPerSec = 0L)
            _progressFlow.value = updated
        }
    }

    suspend fun resumeDownload(id: String) = withContext(Dispatchers.IO) {
        val entity = downloadDao.getById(id) ?: return@withContext
        downloadDao.updateStatusById(id, "DOWNLOADING")

        val targetFile = File(entity.localFilePath ?: File(getStorageDir(), "${entity.id}.mp4").absolutePath)

        activeJobs[id]?.cancel()
        val job = downloadScope.launch {
            runDownloadLoop(entity, targetFile, emptyMap())
        }
        activeJobs[id] = job
    }

    suspend fun deleteDownloadById(id: String) = withContext(Dispatchers.IO) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        val entity = downloadDao.getById(id)
        if (entity != null) {
            val targetFile = entity.localFilePath?.let { File(it) }
            val tempFile = targetFile?.let { File("${it.absolutePath}.part") }
            try { tempFile?.delete() } catch (_: Exception) {}
            try { targetFile?.delete() } catch (_: Exception) {}

            systemDownloadManager?.remove(entity.downloadId)
            downloadDao.deleteById(id)
        }

        val updated = _progressFlow.value.toMutableMap()
        updated.remove(id)
        _progressFlow.value = updated
    }

    suspend fun removeDownload(downloadId: Long) = withContext(Dispatchers.IO) {
        val entity = downloadDao.getByDownloadId(downloadId)
        if (entity != null) {
            deleteDownloadById(entity.id)
        } else {
            systemDownloadManager?.remove(downloadId)
            downloadDao.deleteByDownloadId(downloadId)
        }
    }

    private suspend fun runDownloadLoop(
        entity: DownloadEntity,
        targetFile: File,
        headers: Map<String, String>
    ) = withContext(Dispatchers.IO) {
        val tempFile = File("${targetFile.absolutePath}.part")
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        if (targetFile.exists() && targetFile.length() > 0 && (entity.fileSizeBytes <= 0L || targetFile.length() >= entity.fileSizeBytes)) {
            downloadDao.updateProgressById(
                entity.id,
                "COMPLETED",
                targetFile.length(),
                targetFile.length(),
                targetFile.absolutePath
            )
            val updated = _progressFlow.value.toMutableMap()
            updated[entity.id] = DownloadProgress(entity.id, targetFile.length(), targetFile.length(), 0L, "COMPLETED")
            _progressFlow.value = updated
            return@withContext
        }

        try {
            val reqBuilder = Request.Builder().url(entity.streamUrl)
            if (existingBytes > 0) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }
            reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
            headers.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true) && !k.equals("Range", ignoreCase = true) && !k.equals("User-Agent", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            val resp = httpClient.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful && resp.code != 416) {
                downloadDao.updateStatusById(entity.id, "FAILED")
                val updated = _progressFlow.value.toMutableMap()
                updated[entity.id] = DownloadProgress(entity.id, existingBytes, entity.fileSizeBytes, 0L, "FAILED")
                _progressFlow.value = updated
                return@withContext
            }

            val isPartial = resp.code == 206
            if (!isPartial && existingBytes > 0) {
                existingBytes = 0L
                tempFile.delete()
            }

            val body = resp.body
            val contentLength = body?.contentLength() ?: -1L
            val totalBytes = if (isPartial) {
                if (contentLength > 0) existingBytes + contentLength else entity.fileSizeBytes
            } else {
                if (contentLength > 0) contentLength else entity.fileSizeBytes
            }

            val fos = FileOutputStream(tempFile, isPartial)
            val inputStream = body?.byteStream()

            if (inputStream == null) {
                fos.close()
                downloadDao.updateStatusById(entity.id, "FAILED")
                return@withContext
            }

            var downloadedSoFar = existingBytes
            val buffer = ByteArray(64 * 1024)
            var bytesRead = 0
            var lastDbUpdate = System.currentTimeMillis()
            var lastSpeedCheck = System.currentTimeMillis()
            var bytesInInterval = 0L
            var currentSpeed = 0L

            fos.use { output ->
                inputStream.use { input ->
                    while (coroutineContext.isActive && input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSoFar += bytesRead
                        bytesInInterval += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastSpeedCheck >= 600) {
                            val intervalSec = (now - lastSpeedCheck) / 1000.0
                            currentSpeed = if (intervalSec > 0) (bytesInInterval / intervalSec).toLong() else 0L
                            bytesInInterval = 0L
                            lastSpeedCheck = now

                            val updated = _progressFlow.value.toMutableMap()
                            updated[entity.id] = DownloadProgress(
                                id = entity.id,
                                downloadedBytes = downloadedSoFar,
                                totalBytes = if (totalBytes > 0) totalBytes else downloadedSoFar,
                                speedBytesPerSec = currentSpeed,
                                status = "DOWNLOADING"
                            )
                            _progressFlow.value = updated
                        }

                        if (now - lastDbUpdate >= 2000) {
                            downloadDao.updateProgressById(
                                entity.id,
                                "DOWNLOADING",
                                downloadedSoFar,
                                totalBytes,
                                tempFile.absolutePath
                            )
                            lastDbUpdate = now
                        }
                    }
                }
            }

            if (!coroutineContext.isActive) {
                downloadDao.updateProgressById(
                    entity.id,
                    "PAUSED",
                    downloadedSoFar,
                    totalBytes,
                    tempFile.absolutePath
                )
                val updated = _progressFlow.value.toMutableMap()
                updated[entity.id] = DownloadProgress(entity.id, downloadedSoFar, totalBytes, 0L, "PAUSED")
                _progressFlow.value = updated
                return@withContext
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                targetFile.delete()
                val renamed = tempFile.renameTo(targetFile)
                val finalFile = if (renamed) targetFile else tempFile
                val finalLength = finalFile.length()

                downloadDao.updateProgressById(
                    entity.id,
                    "COMPLETED",
                    finalLength,
                    finalLength,
                    finalFile.absolutePath
                )

                val updated = _progressFlow.value.toMutableMap()
                updated[entity.id] = DownloadProgress(entity.id, finalLength, finalLength, 0L, "COMPLETED")
                _progressFlow.value = updated

                MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                downloadDao.updateStatusById(entity.id, "PAUSED")
                val updated = _progressFlow.value.toMutableMap()
                val curr = updated[entity.id]
                if (curr != null) {
                    updated[entity.id] = curr.copy(status = "PAUSED", speedBytesPerSec = 0L)
                    _progressFlow.value = updated
                }
            } else {
                downloadDao.updateStatusById(entity.id, "FAILED")
                val updated = _progressFlow.value.toMutableMap()
                val curr = updated[entity.id]
                if (curr != null) {
                    updated[entity.id] = curr.copy(status = "FAILED", speedBytesPerSec = 0L)
                    _progressFlow.value = updated
                }
            }
        } finally {
            activeJobs.remove(entity.id)
        }
    }

    suspend fun checkAndUpdateProgress() = withContext(Dispatchers.IO) {
        val query = DownloadManager.Query()
        val cursor = systemDownloadManager?.query(query) ?: return@withContext
        cursor.use { c ->
            val idIdx = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesDownloadedIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalBytesIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localUriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val status = c.getInt(statusIdx)
                val downloaded = if (bytesDownloadedIdx >= 0) c.getLong(bytesDownloadedIdx) else 0L
                val total = if (totalBytesIdx >= 0) c.getLong(totalBytesIdx) else 0L
                val localUri = if (localUriIdx >= 0) c.getString(localUriIdx) else null

                val statusStr = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "COMPLETED"
                    DownloadManager.STATUS_FAILED -> "FAILED"
                    DownloadManager.STATUS_PAUSED -> "PAUSED"
                    else -> "DOWNLOADING"
                }

                downloadDao.updateProgress(id, statusStr, downloaded, localUri)
            }
        }
    }
}
