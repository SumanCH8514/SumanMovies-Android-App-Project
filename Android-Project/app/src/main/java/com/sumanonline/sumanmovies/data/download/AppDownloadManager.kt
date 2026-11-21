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
import com.sumanonline.sumanmovies.data.download.hls.HlsPlaylistParser
import com.sumanonline.sumanmovies.data.download.hls.HlsSegmentDownloader
import com.sumanonline.sumanmovies.data.download.hls.HlsVariant
import com.sumanonline.sumanmovies.data.download.remux.MediaRemuxer
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
    val failureReason: String? = null,
    val downloadedSegments: Int = 0,
    val totalSegments: Int = 0
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

    private fun sanitizeTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
            .replace(Regex("""(?i)\s*season\s*\d+.*$"""), "")
            .replace(Regex("""\s*\(\s*(19\d\d|20\d\d)\s*\)"""), "")
            .replace(":", " -")
            .replace(Regex("""[\\/*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "Untitled" }
    }

    private fun extractYear(title: String, itemYear: String?): String? {
        if (!itemYear.isNullOrBlank()) {
            val cleanY = itemYear.trim()
            if (cleanY.length == 4 && cleanY.toIntOrNull() != null) {
                return cleanY
            }
        }
        val match = Regex("""\b(19\d\d|20\d\d)\b""").find(title)
        return match?.value
    }

    private fun sanitizeQuality(rawQuality: String): String {
        val clean = rawQuality
            .replace(Regex("""[\\/*?"<>|:]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val standardMatch = Regex("""(?i)\b(4k|2160p|1080p|720p|480p|360p)\b""").find(clean)
        return when {
            standardMatch != null -> standardMatch.value
            clean.isNotBlank() -> clean
            else -> "1080p"
        }
    }

    fun getMediaDownloadDir(
        mediaType: String,
        rawTitle: String,
        year: String?
    ): File {
        val cleanTitle = sanitizeTitle(rawTitle)
        val resolvedYear = extractYear(rawTitle, year)
        val isSeries = mediaType.equals("SERIES", ignoreCase = true) ||
                mediaType.equals("ANIME", ignoreCase = true) ||
                mediaType.equals("DRAMA", ignoreCase = true) ||
                mediaType.equals("SHOW", ignoreCase = true) ||
                mediaType.equals("TV", ignoreCase = true)

        val typeFolder = if (isSeries) "Series" else "Movies"
        val mediaFolder = if (!resolvedYear.isNullOrBlank()) "$cleanTitle ($resolvedYear)" else cleanTitle

        val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val primaryDir = File(publicDownloadDir, "SumanOnline Web Services/SumanMovies/$typeFolder/$mediaFolder")

        val canUsePrimary = try {
            if (!primaryDir.exists()) {
                primaryDir.mkdirs()
            }
            primaryDir.exists() && primaryDir.canWrite()
        } catch (_: Exception) {
            false
        }

        if (canUsePrimary) {
            return primaryDir
        }

        val fallbackBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val fallbackDir = File(fallbackBase, "SumanOnline Web Services/SumanMovies/$typeFolder/$mediaFolder")
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return fallbackDir
    }

    fun buildFormattedFileName(
        rawTitle: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        rawQuality: String,
        rawYear: String?,
        ext: String
    ): String {
        val cleanTitle = sanitizeTitle(rawTitle)
        val resolvedYear = extractYear(rawTitle, rawYear)
        val cleanQuality = sanitizeQuality(rawQuality)
        val cleanExt = if (ext.startsWith(".")) ext else ".$ext"

        val isSeries = mediaType.equals("SERIES", ignoreCase = true) ||
                mediaType.equals("ANIME", ignoreCase = true) ||
                mediaType.equals("DRAMA", ignoreCase = true) ||
                mediaType.equals("SHOW", ignoreCase = true) ||
                mediaType.equals("TV", ignoreCase = true) ||
                (season != null && episode != null)

        val yearPart = if (!resolvedYear.isNullOrBlank()) " ($resolvedYear)" else ""

        return if (isSeries) {
            val sNum = season ?: 1
            val eNum = episode ?: 1
            val epCode = String.format(java.util.Locale.US, "S%02dE%02d", sNum, eNum)
            "$cleanTitle $epCode$yearPart $cleanQuality - SumanMovies$cleanExt"
        } else {
            "$cleanTitle$yearPart $cleanQuality - SumanMovies$cleanExt"
        }
    }

    suspend fun enqueueDownload(
        item: MediaItem,
        stream: StreamSource,
        episode: Episode? = null
    ): Long = withContext(Dispatchers.IO) {
        val ext = if (stream.format.equals("MKV", ignoreCase = true) || stream.url.contains(".mkv", ignoreCase = true)) ".mkv" else ".mp4"
        val seasonNum = episode?.seasonNumber
        val episodeNum = episode?.episodeNumber
        val mediaTypeStr = if (episode != null) "SERIES" else item.mediaType.name

        val targetDir = getMediaDownloadDir(mediaTypeStr, item.title, item.year)
        val fileName = buildFormattedFileName(
            rawTitle = item.title,
            mediaType = mediaTypeStr,
            season = seasonNum,
            episode = episodeNum,
            rawQuality = stream.quality,
            rawYear = item.year,
            ext = ext
        )
        val entityId = if (episode != null) "${item.id}-S${episode.seasonNumber}E${episode.episodeNumber}" else item.id

        val targetFile = File(targetDir, fileName)

        val enrichedHeaders = stream.headers.toMutableMap()
        if (stream.format.isNotBlank()) {
            enrichedHeaders["__stream_format"] = stream.format
        }
        if (stream.provider.isNotBlank()) {
            enrichedHeaders["__stream_provider"] = stream.provider
        }
        if (!stream.audioLangs.isNullOrBlank()) {
            enrichedHeaders["__stream_audio_lang"] = stream.audioLangs!!
        }

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
            headersJson = serializeHeaders(enrichedHeaders)
        )

        downloadDao.insert(downloadEntity)

        val initialProgress = DownloadProgress(
            id = entityId,
            downloadedBytes = 0L,
            totalBytes = stream.sizeBytes ?: 0L,
            speedBytesPerSec = 0L,
            status = "DOWNLOADING",
            failureReason = null
        )
        val pMap = _progressFlow.value.toMutableMap()
        pMap[entityId] = initialProgress
        _progressFlow.value = pMap

        DownloadForegroundService.startService(context)

        activeJobs[entityId]?.cancel()
        val job = downloadScope.launch {
            runDownloadLoop(downloadEntity, targetFile, enrichedHeaders)
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

        val targetFile = if (!entity.localFilePath.isNullOrBlank()) {
            File(entity.localFilePath)
        } else {
            val dir = getMediaDownloadDir(entity.mediaType, entity.title, null)
            File(dir, "${entity.id}.mp4")
        }
        val headers = deserializeHeaders(entity.headersJson).toMutableMap()
        val isMovieBoxStream = entity.streamUrl.contains("aoneroom.com", ignoreCase = true) ||
                entity.streamUrl.contains("inmoviebox.com", ignoreCase = true) ||
                entity.streamUrl.contains("wefeed", ignoreCase = true) ||
                entity.quality.contains("HLS", ignoreCase = true)
        if (isMovieBoxStream) {
            headers["__stream_format"] = "HLS"
            headers["__stream_provider"] = "moviebox"
        }

        val initialProgress = DownloadProgress(
            id = id,
            downloadedBytes = entity.downloadedBytes,
            totalBytes = entity.fileSizeBytes,
            speedBytesPerSec = 0L,
            status = "DOWNLOADING",
            failureReason = null
        )
        val pMap = _progressFlow.value.toMutableMap()
        pMap[id] = initialProgress
        _progressFlow.value = pMap

        DownloadForegroundService.startService(context)

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

            val parent = targetFile?.parentFile
            if (parent != null && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
                try { parent.delete() } catch (_: Exception) {}
            }

            val hlsTemp = File(context.filesDir, "hls_temp/$id")
            try { hlsTemp.deleteRecursively() } catch (_: Exception) {}

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

    private fun isFileM3u8OrHtml(file: File): Boolean {
        if (!file.exists() || file.length() == 0L || file.length() > 500_000L) return false
        return try {
            file.bufferedReader().use { reader ->
                val line = reader.readLine()?.trim() ?: ""
                line.startsWith("#EXTM3U") ||
                        line.startsWith("<!DOCTYPE", ignoreCase = true) ||
                        line.startsWith("<html", ignoreCase = true) ||
                        line.startsWith("<?xml", ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun runDownloadLoop(
        entity: DownloadEntity,
        targetFile: File,
        headers: Map<String, String>
    ) = withContext(Dispatchers.IO) {
        val tempFile = File("${targetFile.absolutePath}.part")
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val isTinyFile = targetFile.exists() && targetFile.length() < 1_000_000L
        val isM3u8OrHtml = targetFile.exists() && isFileM3u8OrHtml(targetFile)

        if (targetFile.exists() && !isTinyFile && !isM3u8OrHtml) {
            val isExpectedSize = entity.fileSizeBytes > 0L && targetFile.length() >= (entity.fileSizeBytes * 0.9)
            val isLargeFallback = entity.fileSizeBytes <= 0L && targetFile.length() >= 5_000_000L
            if (isExpectedSize || isLargeFallback) {
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
        } else if (isTinyFile || isM3u8OrHtml) {
            try { targetFile.delete() } catch (_: Exception) {}
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
            if (!currentHeaders.containsKey("__stream_format") && !entity.headersJson.isNullOrBlank()) {
                val stored = deserializeHeaders(entity.headersJson)
                currentHeaders.putAll(stored)
            }
            val formatMeta = currentHeaders["__stream_format"] ?: ""
            val providerMeta = currentHeaders["__stream_provider"] ?: ""

            val isMovieBox = providerMeta.equals("moviebox", ignoreCase = true) ||
                    currentUrl.contains("aoneroom.com", ignoreCase = true) ||
                    currentUrl.contains("inmoviebox.com", ignoreCase = true) ||
                    currentUrl.contains("wefeed", ignoreCase = true)

            val isPreHls = formatMeta.equals("HLS", ignoreCase = true) ||
                    HlsPlaylistParser.isHlsUrl(currentUrl) ||
                    currentUrl.contains(".m3u8", ignoreCase = true) ||
                    entity.quality.contains("HLS", ignoreCase = true) ||
                    (isMovieBox && !currentUrl.contains(".mp4", ignoreCase = true) && !currentUrl.contains(".mkv", ignoreCase = true))

            if (isPreHls) {
                runHlsDownload(entity, targetFile, currentUrl, currentHeaders)
                return@withContext
            }

            if (currentUrl.endsWith(".mpd", ignoreCase = true) || currentUrl.contains("/dash/")) {
                val directMedia = resolveDirectMediaUrlFromManifest(currentUrl, currentHeaders, entity.quality)
                if (directMedia != null) {
                    currentUrl = directMedia.first
                    currentHeaders.putAll(directMedia.second)
                }
            }

            var resp = httpClient.newCall(buildRequest(currentUrl, currentHeaders, existingBytes)).execute()

            var rateLimitRetries = 0
            while (resp.code == 429 && rateLimitRetries < 3) {
                resp.close()
                val retryAfterHeader = resp.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 10L)
                val backoffSec = retryAfterHeader ?: ((rateLimitRetries + 1) * 2L)
                kotlinx.coroutines.delay(backoffSec * 1000L)
                rateLimitRetries++
                resp = httpClient.newCall(buildRequest(currentUrl, currentHeaders, existingBytes)).execute()
            }

            if (!resp.isSuccessful && (resp.code == 401 || resp.code == 403 || resp.code == 404 || resp.code == 410 || resp.code == 429)) {
                resp.close()
                val refreshed = refreshStreamSource(entity)
                if (refreshed != null && refreshed.url.isNotBlank()) {
                    currentUrl = refreshed.url
                    currentHeaders.clear()
                    currentHeaders.putAll(refreshed.headers)
                    if (refreshed.format.isNotBlank()) {
                        currentHeaders["__stream_format"] = refreshed.format
                    }
                    if (refreshed.provider.isNotBlank()) {
                        currentHeaders["__stream_provider"] = refreshed.provider
                    }
                    if (!refreshed.audioLangs.isNullOrBlank()) {
                        currentHeaders["__stream_audio_lang"] = refreshed.audioLangs!!
                    }
                    val updatedEntity = entity.copy(
                        streamUrl = currentUrl,
                        headersJson = serializeHeaders(currentHeaders)
                    )
                    downloadDao.insert(updatedEntity)

                    val isRefreshedHls = currentHeaders["__stream_format"].equals("HLS", ignoreCase = true) ||
                            HlsPlaylistParser.isHlsUrl(currentUrl) ||
                            currentUrl.contains(".m3u8", ignoreCase = true)
                    if (isRefreshedHls) {
                        runHlsDownload(updatedEntity, targetFile, currentUrl, currentHeaders)
                        return@withContext
                    }

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

            val finalUrl = resp.request.url.toString()
            val contentType = resp.header("Content-Type")?.lowercase() ?: ""
            val isHlsByHeadersOrUrl = HlsPlaylistParser.isHlsUrl(finalUrl) ||
                    finalUrl.contains(".m3u8", ignoreCase = true) ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("application/x-mpegurl") ||
                    contentType.contains("audio/mpegurl") ||
                    contentType.contains("application/vnd.apple.mpegurl")

            val isHlsByBody = try {
                val peek = resp.peekBody(2048).string().trim().removePrefix("\uFEFF")
                peek.startsWith("#EXTM3U") || peek.contains("#EXT-X-STREAM-INF") || peek.contains("#EXT-X-TARGETDURATION")
            } catch (_: Exception) {
                false
            }

            if (isHlsByHeadersOrUrl || isHlsByBody) {
                resp.close()
                runHlsDownload(entity, targetFile, finalUrl, currentHeaders)
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
                if (isFileM3u8OrHtml(tempFile)) {
                    val fileContent = try { tempFile.readText() } catch (_: Exception) { "" }
                    if (fileContent.trim().removePrefix("\uFEFF").startsWith("#EXTM3U")) {
                        try { tempFile.delete() } catch (_: Exception) {}
                        runHlsDownload(entity, targetFile, currentUrl, currentHeaders)
                        return@withContext
                    } else {
                        try { tempFile.delete() } catch (_: Exception) {}
                        throw IllegalStateException("Downloaded stream was text/HTML rather than valid media")
                    }
                }

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
        try {
            val allDownloads = downloadDao.getAllDownloadsList()
            for (dl in allDownloads) {
                if (dl.status == "COMPLETED") {
                    var file = dl.localFilePath?.let { File(it) }
                    if ((file == null || !file.exists() || file.length() < 100_000L) && dl.localFilePath != null) {
                        val altExtPath = if (dl.localFilePath.endsWith(".mp4", ignoreCase = true)) {
                            dl.localFilePath.removeSuffix(".mp4") + ".mkv"
                        } else if (dl.localFilePath.endsWith(".mkv", ignoreCase = true)) {
                            dl.localFilePath.removeSuffix(".mkv") + ".mp4"
                        } else null
                        if (altExtPath != null) {
                            val altFile = File(altExtPath)
                            if (altFile.exists() && altFile.length() >= 100_000L && !isFileM3u8OrHtml(altFile)) {
                                file = altFile
                                downloadDao.updateProgressById(dl.id, "COMPLETED", altFile.length(), altFile.length(), altFile.absolutePath)
                            }
                        }
                    }
                    val isCorrupt = file == null || !file.exists() || file.length() < 100_000L || isFileM3u8OrHtml(file)
                    if (isCorrupt) {
                        try { file?.delete() } catch (_: Exception) {}
                        downloadDao.updateFailureStatusById(
                            dl.id,
                            "FAILED",
                            "Incomplete download (playlist text was saved). Tap Resume to redownload full video."
                        )
                        val updated = _progressFlow.value.toMutableMap()
                        updated[dl.id] = DownloadProgress(
                            id = dl.id,
                            downloadedBytes = 0L,
                            totalBytes = 0L,
                            speedBytesPerSec = 0L,
                            status = "FAILED",
                            failureReason = "Incomplete download (playlist text was saved). Tap Resume to redownload full video."
                        )
                        _progressFlow.value = updated
                    }
                }
            }
        } catch (_: Exception) {}

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
            val entityHeaders = deserializeHeaders(entity.headersJson)
            val providerName = entityHeaders["__stream_provider"] ?: ""
            val wantAudioLang = entityHeaders["__stream_audio_lang"] ?: ""
            val targetServer = when {
                providerName.contains("4khdhub", ignoreCase = true) -> com.sumanonline.sumanmovies.data.models.StreamingServer.FOURKHDHUB
                providerName.contains("hdhub4u", ignoreCase = true) -> com.sumanonline.sumanmovies.data.models.StreamingServer.HDHUB4U
                providerName.contains("play4u", ignoreCase = true) -> com.sumanonline.sumanmovies.data.models.StreamingServer.PLAY4U
                providerName.contains("dramachi", ignoreCase = true) -> com.sumanonline.sumanmovies.data.models.StreamingServer.DRAMACHI
                providerName.contains("reanime", ignoreCase = true) -> com.sumanonline.sumanmovies.data.models.StreamingServer.REANIME
                else -> com.sumanonline.sumanmovies.data.models.StreamingServer.MOVIEBOX
            }

            val resolved = app.playbackResolver.resolveServer(
                server = targetServer,
                title = cleanTitle,
                season = if (isSeries) entity.seasonNumber else 0,
                episode = if (isSeries) entity.episodeNumber else 0,
                providerId = entity.mediaId,
                forceRefresh = true
            )

            val freshStreams = resolved.streams
            if (freshStreams.isNotEmpty()) {
                val candidateStreams = if (wantAudioLang.isNotBlank()) {
                    val matching = freshStreams.filter { it.audioLangs.equals(wantAudioLang, ignoreCase = true) }
                    matching.ifEmpty { freshStreams }
                } else freshStreams

                val wantFormat = entityHeaders["__stream_format"] ?: ""
                val isHlsPreferred = wantFormat.equals("HLS", ignoreCase = true) ||
                        entity.quality.contains("HLS", ignoreCase = true) ||
                        entity.streamUrl.contains("aoneroom.com", ignoreCase = true) ||
                        entity.streamUrl.contains("inmoviebox.com", ignoreCase = true) ||
                        entity.streamUrl.contains("wefeed", ignoreCase = true)

                val matched = if (isHlsPreferred) {
                    candidateStreams.firstOrNull { it.format.equals("HLS", ignoreCase = true) && it.quality.contains(entity.quality.take(4), ignoreCase = true) }
                        ?: candidateStreams.firstOrNull { it.format.equals("HLS", ignoreCase = true) }
                        ?: candidateStreams.firstOrNull { it.quality.contains(entity.quality.take(4), ignoreCase = true) }
                        ?: candidateStreams.firstOrNull()
                } else {
                    candidateStreams.firstOrNull {
                        (it.directFile || it.format.equals("MP4", ignoreCase = true)) &&
                                it.quality.contains(entity.quality.take(4), ignoreCase = true)
                    } ?: candidateStreams.firstOrNull {
                        it.directFile || it.format.equals("MP4", ignoreCase = true)
                    } ?: candidateStreams.firstOrNull {
                        it.format.equals("HLS", ignoreCase = true) && it.quality.contains(entity.quality.take(4), ignoreCase = true)
                    } ?: candidateStreams.firstOrNull {
                        it.quality.contains(entity.quality.take(4), ignoreCase = true)
                    } ?: candidateStreams.firstOrNull()
                }

                return@withContext matched
            }
        } catch (_: Exception) {}
        null
    }

    private fun buildRequest(url: String, hdrs: Map<String, String>, rangeBytes: Long): Request {
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
                !k.equals("User-Agent", ignoreCase = true) &&
                !k.startsWith("__")
            ) {
                reqBuilder.header(k, v)
            }
        }
        return reqBuilder.build()
    }

    suspend fun fetchHlsVariants(url: String, headers: Map<String, String>): List<HlsVariant> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(url, headers, 0L)
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return@withContext emptyList()
            }
            val content = resp.body?.string() ?: return@withContext emptyList()
            if (HlsPlaylistParser.isMasterPlaylist(content)) {
                HlsPlaylistParser.parseMasterPlaylist(content, url).variants
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun runHlsDownload(
        entity: DownloadEntity,
        targetFile: File,
        manifestUrl: String,
        headers: Map<String, String>
    ) = withContext(Dispatchers.IO) {
        val tempDir = File(context.filesDir, "hls_temp/${entity.id}")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        try {
            downloadDao.updateStatusById(entity.id, "FETCHING_PLAYLIST")
            val pMap = _progressFlow.value.toMutableMap()
            pMap[entity.id] = DownloadProgress(
                id = entity.id,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBytesPerSec = 0L,
                status = "FETCHING_PLAYLIST",
                failureReason = null
            )
            _progressFlow.value = pMap

            DownloadForegroundService.startService(context)

            var content: String
            var resolvedMediaPlaylistUrl = manifestUrl
            var currentHeaders = headers.toMutableMap()

            var manifestReq = buildRequest(resolvedMediaPlaylistUrl, currentHeaders, 0L)
            var manifestResp = httpClient.newCall(manifestReq).execute()

            if (!manifestResp.isSuccessful && (manifestResp.code in listOf(401, 403, 404, 410, 429))) {
                if (manifestResp.code == 429) {
                    kotlinx.coroutines.delay(2000L)
                }
                manifestResp.close()
                val refreshed = refreshStreamSource(entity)
                if (refreshed != null && refreshed.url.isNotBlank()) {
                    resolvedMediaPlaylistUrl = refreshed.url
                    currentHeaders.clear()
                    currentHeaders.putAll(refreshed.headers)
                    if (refreshed.format.isNotBlank()) currentHeaders["__stream_format"] = refreshed.format
                    if (refreshed.provider.isNotBlank()) currentHeaders["__stream_provider"] = refreshed.provider
                    if (!refreshed.audioLangs.isNullOrBlank()) currentHeaders["__stream_audio_lang"] = refreshed.audioLangs!!
                    val updatedEntity = entity.copy(
                        streamUrl = resolvedMediaPlaylistUrl,
                        headersJson = serializeHeaders(currentHeaders)
                    )
                    downloadDao.insert(updatedEntity)
                    manifestReq = buildRequest(resolvedMediaPlaylistUrl, currentHeaders, 0L)
                    manifestResp = httpClient.newCall(manifestReq).execute()
                }
            }

            if (!manifestResp.isSuccessful) {
                val code = manifestResp.code
                manifestResp.close()
                throw IllegalStateException("Failed to fetch HLS manifest (HTTP $code)")
            }
            resolvedMediaPlaylistUrl = manifestResp.request.url.toString()
            content = manifestResp.body?.string() ?: throw IllegalStateException("Empty HLS manifest response")

            if (HlsPlaylistParser.isMasterPlaylist(content)) {
                val master = HlsPlaylistParser.parseMasterPlaylist(content, resolvedMediaPlaylistUrl)
                if (master.variants.isEmpty()) {
                    throw IllegalStateException("Master playlist contains no streaming variants")
                }

                val matchedVariant = master.variants.firstOrNull {
                    it.qualityLabel.equals(entity.quality, ignoreCase = true) ||
                            (entity.quality.contains("1080") && it.qualityLabel == "1080p") ||
                            (entity.quality.contains("720") && it.qualityLabel == "720p") ||
                            (entity.quality.contains("4K") && it.qualityLabel == "4K") ||
                            (entity.quality.contains("480") && it.qualityLabel == "480p") ||
                            (entity.quality.contains("360") && it.qualityLabel == "360p")
                } ?: master.variants.first()

                resolvedMediaPlaylistUrl = matchedVariant.url
                var mediaReq = buildRequest(resolvedMediaPlaylistUrl, currentHeaders, 0L)
                var mediaResp = httpClient.newCall(mediaReq).execute()

                if (!mediaResp.isSuccessful && (mediaResp.code in listOf(401, 403, 404, 410, 429))) {
                    if (mediaResp.code == 429) {
                        kotlinx.coroutines.delay(2000L)
                    }
                    mediaResp.close()
                    val refreshed = refreshStreamSource(entity)
                    if (refreshed != null && refreshed.url.isNotBlank()) {
                        currentHeaders.clear()
                        currentHeaders.putAll(refreshed.headers)
                        if (refreshed.format.isNotBlank()) currentHeaders["__stream_format"] = refreshed.format
                        if (refreshed.provider.isNotBlank()) currentHeaders["__stream_provider"] = refreshed.provider
                        if (!refreshed.audioLangs.isNullOrBlank()) currentHeaders["__stream_audio_lang"] = refreshed.audioLangs!!
                        val updatedEntity = entity.copy(
                            streamUrl = refreshed.url,
                            headersJson = serializeHeaders(currentHeaders)
                        )
                        downloadDao.insert(updatedEntity)
                        mediaReq = buildRequest(resolvedMediaPlaylistUrl, currentHeaders, 0L)
                        mediaResp = httpClient.newCall(mediaReq).execute()
                    }
                }

                if (!mediaResp.isSuccessful) {
                    val code = mediaResp.code
                    mediaResp.close()
                    throw IllegalStateException("Failed to fetch media playlist from variant (HTTP $code)")
                }
                resolvedMediaPlaylistUrl = mediaResp.request.url.toString()
                content = mediaResp.body?.string() ?: throw IllegalStateException("Empty media playlist response")
            }

            val mediaPlaylist = HlsPlaylistParser.parseMediaPlaylist(content, resolvedMediaPlaylistUrl)
            if (mediaPlaylist.segments.isEmpty()) {
                throw IllegalStateException("HLS media playlist contains 0 downloadable segments")
            }

            downloadDao.updateStatusById(entity.id, "DOWNLOADING")
            var lastDbSave = System.currentTimeMillis()

            val downloader = HlsSegmentDownloader(httpClient = httpClient, concurrency = 4)
            val downloadedSegments = downloader.downloadSegments(
                playlist = mediaPlaylist,
                headers = currentHeaders,
                tempDir = tempDir
            ) { downloadedBytes, totalEstimatedBytes, downloadedSegs, totalSegs, speedBytesPerSec ->
                val progress = DownloadProgress(
                    id = entity.id,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalEstimatedBytes,
                    speedBytesPerSec = speedBytesPerSec,
                    status = "DOWNLOADING",
                    failureReason = null,
                    downloadedSegments = downloadedSegs,
                    totalSegments = totalSegs
                )
                val updated = _progressFlow.value.toMutableMap()
                updated[entity.id] = progress
                _progressFlow.value = updated

                val now = System.currentTimeMillis()
                if (now - lastDbSave >= 2000) {
                    lastDbSave = now
                    downloadScope.launch {
                        downloadDao.updateProgressById(
                            entity.id,
                            "DOWNLOADING",
                            downloadedBytes,
                            totalEstimatedBytes,
                            targetFile.absolutePath
                        )
                    }
                }
            }

            downloadDao.updateStatusById(entity.id, "MERGING")
            val totalSegLength = downloadedSegments.sumOf { it.length() }
            val mergingProgress = DownloadProgress(
                id = entity.id,
                downloadedBytes = totalSegLength,
                totalBytes = totalSegLength,
                speedBytesPerSec = 0L,
                status = "MERGING",
                failureReason = null,
                downloadedSegments = downloadedSegments.size,
                totalSegments = downloadedSegments.size
            )
            val updatedMerging = _progressFlow.value.toMutableMap()
            updatedMerging[entity.id] = mergingProgress
            _progressFlow.value = updatedMerging

            val finalMediaFile = MediaRemuxer.remuxSegments(
                context = context,
                segmentFiles = downloadedSegments,
                targetFile = targetFile,
                tempDir = tempDir
            )

            val finalSize = finalMediaFile.length()
            if (finalSize < 100_000L) {
                try { finalMediaFile.delete() } catch (_: Exception) {}
                throw IllegalStateException("Remuxed video file is too small ($finalSize bytes), download failed")
            }

            downloadDao.updateProgressById(
                entity.id,
                "COMPLETED",
                finalSize,
                finalSize,
                finalMediaFile.absolutePath
            )

            val finalProgress = DownloadProgress(
                id = entity.id,
                downloadedBytes = finalSize,
                totalBytes = finalSize,
                speedBytesPerSec = 0L,
                status = "COMPLETED",
                failureReason = null,
                downloadedSegments = downloadedSegments.size,
                totalSegments = downloadedSegments.size
            )
            val updatedFinal = _progressFlow.value.toMutableMap()
            updatedFinal[entity.id] = finalProgress
            _progressFlow.value = updatedFinal

            showToast("${entity.title} download completed!")
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
                val reason = classifyFailureReason(e = e, targetFile = targetFile)
                downloadDao.updateFailureStatusById(entity.id, "FAILED", reason)
                val updated = _progressFlow.value.toMutableMap()
                val curr = updated[entity.id]
                if (curr != null) {
                    updated[entity.id] = curr.copy(status = "FAILED", speedBytesPerSec = 0L, failureReason = reason)
                } else {
                    updated[entity.id] = DownloadProgress(entity.id, 0L, entity.fileSizeBytes, 0L, "FAILED", reason)
                }
                _progressFlow.value = updated
                showDownloadFailedNotification(entity.title, reason, entity.downloadId)
                showToast("${entity.title} download failed: $reason")
            }
        } finally {
            activeJobs.remove(entity.id)
        }
    }
}
