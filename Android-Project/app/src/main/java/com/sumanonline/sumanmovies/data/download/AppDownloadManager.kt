package com.sumanonline.sumanmovies.data.download

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.sumanonline.sumanmovies.MainActivity
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
    val status: String = "DOWNLOADING",
    val failureReason: String? = null
)

class AppDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val preferencesManager: com.sumanonline.sumanmovies.data.local.AppPreferencesManager? = null
) {
    private val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val CHANNEL_ID = "sumanmovies_downloads"

    private val _progressFlow = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progressFlow: StateFlow<Map<String, DownloadProgress>> = _progressFlow.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = com.google.gson.Gson()

    private fun serializeHeaders(headers: Map<String, String>): String {
        return try {
            gson.toJson(headers)
        } catch (_: Exception) {
            ""
        }
    }

    private fun deserializeHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Download status and completion notifications"
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun showDownloadFailedNotification(title: String, failureReason: String, downloadId: Long) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "LIBRARY_DOWNLOADS")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                downloadId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("$title • Download Failed")
                .setContentText(failureReason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(failureReason))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager?.notify(downloadId.toInt(), notification)
        } catch (_: Exception) {}
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }
    }

    private fun classifyFailureReason(
        e: Throwable? = null,
        httpCode: Int? = null,
        targetFile: File? = null,
        requiredBytes: Long = 0L
    ): String {
        if (httpCode != null) {
            return when (httpCode) {
                401, 403 -> "Stream link expired or access forbidden by host (HTTP $httpCode). Try another server."
                404, 410 -> "Video source file no longer found on host server (HTTP $httpCode)."
                429 -> "Too many requests to host server. Rate limit reached (HTTP 429)."
                in 500..599 -> "Host streaming server error (HTTP $httpCode). Server is down or overloaded."
                else -> "Host server rejected download request (HTTP $httpCode)."
            }
        }

        if (targetFile != null) {
            val parent = targetFile.parentFile ?: targetFile
            val freeSpace = parent.freeSpace
            if (freeSpace in 1 until 50 * 1024 * 1024 || (requiredBytes > 0 && freeSpace < requiredBytes)) {
                return "Insufficient storage space on device. Free up storage."
            }
        }

        if (e != null) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("enospc") || msg.contains("no space left") || msg.contains("disk full")) {
                return "Insufficient storage space on device. Free up storage."
            }
            if (e is java.net.UnknownHostException) {
                return "Connection issue: Unable to resolve host server. Check internet connection."
            }
            if (e is java.net.SocketTimeoutException) {
                return "Connection timed out while downloading from host. Check network."
            }
            if (e is java.net.ConnectException || e is java.net.NoRouteToHostException) {
                return "Connection failed: Host server unreachable. Check internet connection."
            }
            if (e is javax.net.ssl.SSLException) {
                return "Secure connection error (SSL handshake failed) with host server."
            }
            if (e is java.io.FileNotFoundException) {
                return "Unable to create local file. Storage permission or path error."
            }
            if (msg.isNotBlank()) {
                return "Download failed: ${e.message}"
            }
        }

        return "Download failed due to an unexpected server or connection error."
    }

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
            createdAt = System.currentTimeMillis(),
            failureReason = null,
            headersJson = serializeHeaders(stream.headers)
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
        downloadDao.updateFailureStatusById(id, "DOWNLOADING", null)

        val targetFile = File(entity.localFilePath ?: File(getStorageDir(), "${entity.id}.mp4").absolutePath)
        val headers = deserializeHeaders(entity.headersJson)

        activeJobs[id]?.cancel()
        val job = downloadScope.launch {
            runDownloadLoop(entity, targetFile, headers)
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

        val freeSpace = targetFile.parentFile?.freeSpace ?: targetFile.freeSpace
        if (freeSpace in 1 until 30 * 1024 * 1024) {
            val reason = "Insufficient storage space on device. Free up storage."
            downloadDao.updateFailureStatusById(entity.id, "FAILED", reason)
            val updated = _progressFlow.value.toMutableMap()
            updated[entity.id] = DownloadProgress(entity.id, existingBytes, entity.fileSizeBytes, 0L, "FAILED", reason)
            _progressFlow.value = updated
            showDownloadFailedNotification(entity.title, reason, entity.downloadId)
            showToast("${entity.title} download failed: $reason")
            return@withContext
        }

        try {
            var currentUrl = entity.streamUrl
            var currentHeaders = headers.toMutableMap()

            // If it's a DASH manifest (.mpd), resolve direct progressive media BaseURL if present in the manifest
            if (currentUrl.endsWith(".mpd", ignoreCase = true) || currentUrl.contains("/dash/")) {
                val directMedia = resolveDirectMediaUrlFromManifest(currentUrl, currentHeaders, entity.quality)
                if (directMedia != null) {
                    currentUrl = directMedia.first
                    currentHeaders.putAll(directMedia.second)
                }
            }

            fun buildRequest(url: String, hdrs: Map<String, String>, rangeBytes: Long): Request {
                val reqBuilder = Request.Builder().url(url)
                if (rangeBytes > 0) {
                    reqBuilder.header("Range", "bytes=$rangeBytes-")
                }
                val ua = hdrs["User-Agent"]
                    ?: hdrs["user-agent"]
                    ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                reqBuilder.header("User-Agent", ua)

                hdrs.forEach { (k, v) ->
                    if (!k.equals("Host", ignoreCase = true) &&
                        !k.equals("Range", ignoreCase = true) &&
                        !k.equals("User-Agent", ignoreCase = true)
                    ) {
                        reqBuilder.header(k, v)
                    }
                }
                return reqBuilder.build()
            }

            var resp = httpClient.newCall(buildRequest(currentUrl, currentHeaders, existingBytes)).execute()

            // If CloudFront cookie or link expired (401, 403, 404, 410), attempt automatic stream refresh
            if (!resp.isSuccessful && (resp.code == 401 || resp.code == 403 || resp.code == 404 || resp.code == 410)) {
                resp.close()
                val refreshed = refreshStreamSource(entity)
                if (refreshed != null && refreshed.url.isNotBlank()) {
                    currentUrl = refreshed.url
                    currentHeaders.clear()
                    currentHeaders.putAll(refreshed.headers)
                    val updatedEntity = entity.copy(
                        streamUrl = currentUrl,
                        headersJson = serializeHeaders(currentHeaders)
                    )
                    downloadDao.insert(updatedEntity)
                    resp = httpClient.newCall(buildRequest(currentUrl, currentHeaders, existingBytes)).execute()
                }
            }

            if (!resp.isSuccessful && resp.code != 416) {
                val reason = classifyFailureReason(httpCode = resp.code, targetFile = targetFile)
                downloadDao.updateFailureStatusById(entity.id, "FAILED", reason)
                val updated = _progressFlow.value.toMutableMap()
                updated[entity.id] = DownloadProgress(entity.id, existingBytes, entity.fileSizeBytes, 0L, "FAILED", reason)
                _progressFlow.value = updated
                showDownloadFailedNotification(entity.title, reason, entity.downloadId)
                showToast("${entity.title} download failed: $reason")
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
                val reason = "Host response stream was empty. Try another mirror or server."
                downloadDao.updateFailureStatusById(entity.id, "FAILED", reason)
                val updated = _progressFlow.value.toMutableMap()
                updated[entity.id] = DownloadProgress(entity.id, existingBytes, entity.fileSizeBytes, 0L, "FAILED", reason)
                _progressFlow.value = updated
                showDownloadFailedNotification(entity.title, reason, entity.downloadId)
                showToast("${entity.title} download failed: $reason")
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
                                status = "DOWNLOADING",
                                failureReason = null
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
                updated[entity.id] = DownloadProgress(entity.id, downloadedSoFar, totalBytes, 0L, "PAUSED", null)
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
                updated[entity.id] = DownloadProgress(entity.id, finalLength, finalLength, 0L, "COMPLETED", null)
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
                val reason = classifyFailureReason(e = e, targetFile = targetFile, requiredBytes = entity.fileSizeBytes)
                downloadDao.updateFailureStatusById(entity.id, "FAILED", reason)
                val updated = _progressFlow.value.toMutableMap()
                val curr = updated[entity.id]
                if (curr != null) {
                    updated[entity.id] = curr.copy(status = "FAILED", speedBytesPerSec = 0L, failureReason = reason)
                    _progressFlow.value = updated
                } else {
                    updated[entity.id] = DownloadProgress(entity.id, 0L, entity.fileSizeBytes, 0L, "FAILED", reason)
                    _progressFlow.value = updated
                }
                showDownloadFailedNotification(entity.title, reason, entity.downloadId)
                showToast("${entity.title} download failed: $reason")
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

    private suspend fun resolveDirectMediaUrlFromManifest(
        manifestUrl: String,
        headers: Map<String, String>,
        targetQuality: String
    ): Pair<String, Map<String, String>>? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder().url(manifestUrl)
            val ua = headers["User-Agent"]
                ?: headers["user-agent"]
                ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
            reqBuilder.header("User-Agent", ua)
            headers.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true) &&
                    !k.equals("Range", ignoreCase = true) &&
                    !k.equals("User-Agent", ignoreCase = true)
                ) {
                    reqBuilder.header(k, v)
                }
            }
            val resp = httpClient.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful) return@withContext null
            val xml = resp.body?.string() ?: return@withContext null

            val baseUrlRegex = Regex("""<BaseURL[^>]*>([^<]+)</BaseURL>""", RegexOption.IGNORE_CASE)
            val baseUrls = baseUrlRegex.findAll(xml).map { it.groupValues[1].trim() }.toList()

            val mediaCandidate = baseUrls.firstOrNull {
                it.contains(".mp4", ignoreCase = true) || it.contains(".m4v", ignoreCase = true) || it.contains(".mkv", ignoreCase = true)
            } ?: baseUrls.firstOrNull {
                !it.endsWith(".mpd", ignoreCase = true) && !it.endsWith(".m3u8", ignoreCase = true)
            }

            if (mediaCandidate != null) {
                val absoluteMediaUrl = if (mediaCandidate.startsWith("http://") || mediaCandidate.startsWith("https://")) {
                    mediaCandidate
                } else {
                    val parent = manifestUrl.substringBeforeLast("/")
                    "$parent/$mediaCandidate"
                }
                return@withContext Pair(absoluteMediaUrl, headers)
            }
        } catch (_: Exception) {}
        null
    }

    private suspend fun refreshStreamSource(entity: DownloadEntity): StreamSource? = withContext(Dispatchers.IO) {
        try {
            val app = com.sumanonline.sumanmovies.SumanMoviesApp.get()
            val cleanTitle = entity.title
                .substringBefore(" - S")
                .substringBefore(" • ")
                .trim()

            val isSeries = entity.mediaType.equals("SERIES", ignoreCase = true) || entity.seasonNumber > 0

            val resolved = app.playbackResolver.resolveServer(
                server = com.sumanonline.sumanmovies.data.models.StreamingServer.MOVIEBOX,
                title = cleanTitle,
                season = if (isSeries) entity.seasonNumber else 0,
                episode = if (isSeries) entity.episodeNumber else 0,
                providerId = entity.mediaId
            )

            val freshStreams = resolved.streams
            if (freshStreams.isNotEmpty()) {
                val matched = freshStreams.firstOrNull {
                    (it.directFile || it.format.equals("MP4", ignoreCase = true)) &&
                            it.quality.contains(entity.quality.take(4), ignoreCase = true)
                } ?: freshStreams.firstOrNull {
                    it.directFile || it.format.equals("MP4", ignoreCase = true)
                } ?: freshStreams.firstOrNull {
                    it.quality.contains(entity.quality.take(4), ignoreCase = true)
                } ?: freshStreams.firstOrNull()

                return@withContext matched
            }
        } catch (_: Exception) {}
        null
    }
}
