package com.sumanonline.sumanmovies.ui.screens.player.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.sumanonline.sumanmovies.ui.screens.player.PlaybackNetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class ThumbnailRepository(
    private val context: Context? = null,
    val cacheManager: ThumbnailCacheManager? = context?.let { ThumbnailCacheManager(it) }
) {
    private val httpClient = PlaybackNetworkConfig.streamingHttpClient

    private val vttCuesByStream = ConcurrentHashMap<String, List<ThumbnailSpriteCue>>()
    private val hlsSegmentsByStream = ConcurrentHashMap<String, List<HlsSegmentEntry>>()

    @Volatile
    private var activeRetrieverUrl: String? = null
    private var activeRetriever: MediaMetadataRetriever? = null
    private val retrieverLock = Any()

    fun registerWebVttCues(streamKey: String, cues: List<ThumbnailSpriteCue>) {
        vttCuesByStream[streamKey] = cues
    }

    suspend fun getThumbnail(
        mediaId: String,
        streamUrl: String?,
        headers: Map<String, String>,
        timestampMs: Long,
        posterUrl: String? = null
    ): ThumbnailFrame = withContext(Dispatchers.IO) {
        if (streamUrl.isNullOrBlank() || timestampMs < 0L) {
            return@withContext ThumbnailFrame(
                timestampMs = timestampMs.coerceAtLeast(0L),
                bitmap = null,
                isPlaceholder = true,
                placeholderUrl = posterUrl
            )
        }

        val bucketIndex = timestampMs / 10000L
        val bucketTimestampMs = bucketIndex * 10000L
        val cacheKey = "${mediaId}_${streamUrl.hashCode()}_${bucketIndex}"

        val cachedBitmap = cacheManager?.getBitmap(cacheKey)
        if (cachedBitmap != null) {
            return@withContext ThumbnailFrame(
                timestampMs = timestampMs,
                bitmap = cachedBitmap,
                isPlaceholder = false
            )
        }

        val vttCues = vttCuesByStream[streamUrl]
        if (!vttCues.isNullOrEmpty()) {
            val matchedCue = vttCues.firstOrNull { it.contains(timestampMs) }
            if (matchedCue != null) {
                val spriteBitmap = loadAndCropSprite(matchedCue)
                if (spriteBitmap != null) {
                    cacheManager?.putBitmap(cacheKey, spriteBitmap)
                    return@withContext ThumbnailFrame(
                        timestampMs = timestampMs,
                        bitmap = spriteBitmap,
                        isPlaceholder = false
                    )
                }
            }
        }

        val extractedBitmap = if (isHlsStream(streamUrl)) {
            extractHlsFrame(streamUrl, headers, bucketTimestampMs)
        } else {
            extractProgressiveFrame(streamUrl, headers, bucketTimestampMs)
        }

        if (extractedBitmap != null) {
            cacheManager?.putBitmap(cacheKey, extractedBitmap)
            return@withContext ThumbnailFrame(
                timestampMs = timestampMs,
                bitmap = extractedBitmap,
                isPlaceholder = false
            )
        }

        return@withContext ThumbnailFrame(
            timestampMs = timestampMs,
            bitmap = null,
            isPlaceholder = true,
            placeholderUrl = posterUrl
        )
    }

    private fun isHlsStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains("m3u8")
    }

    private fun extractHlsFrame(
        streamUrl: String,
        headers: Map<String, String>,
        timestampMs: Long
    ): Bitmap? {
        val segments = hlsSegmentsByStream.getOrPut(streamUrl) {
            fetchHlsSegments(streamUrl, headers)
        }
        if (segments.isEmpty()) {
            return null
        }

        val targetSegment = segments.firstOrNull { it.contains(timestampMs) }
            ?: segments.minByOrNull { Math.abs(it.startMs - timestampMs) }
            ?: return null

        return downloadAndExtractSegmentFrame(targetSegment.segmentUrl, headers, timestampMs - targetSegment.startMs)
    }

    private fun fetchHlsSegments(playlistUrl: String, headers: Map<String, String>): List<HlsSegmentEntry> {
        return try {
            val safeHeaders = sanitizeHeaders(headers)
            val reqBuilder = Request.Builder().url(playlistUrl)
            safeHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
            val content = httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: ""
            }

            if (content.isBlank()) return emptyList()

            if (content.contains("#EXT-X-STREAM-INF")) {
                val mediaPlaylistUrl = extractLowestBitrateVariant(content, playlistUrl)
                if (mediaPlaylistUrl != null && mediaPlaylistUrl != playlistUrl) {
                    return fetchHlsSegments(mediaPlaylistUrl, headers)
                }
            }

            parseMediaPlaylist(content, playlistUrl)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun extractLowestBitrateVariant(masterContent: String, baseUrl: String): String? {
        val lines = masterContent.lines()
        var lowestBandwidth = Long.MAX_VALUE
        var bestUrl: String? = null

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(line)
                val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: Long.MAX_VALUE
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (nextLine.isNotBlank() && !nextLine.startsWith("#")) {
                        if (bw < lowestBandwidth) {
                            lowestBandwidth = bw
                            bestUrl = resolveUrl(baseUrl, nextLine)
                        }
                    }
                }
            }
            i++
        }
        return bestUrl
    }

    private fun parseMediaPlaylist(content: String, baseUrl: String): List<HlsSegmentEntry> {
        val result = mutableListOf<HlsSegmentEntry>()
        val lines = content.lines()
        var currentStartMs = 0L
        var currentDurationMs = 0L

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                val secStr = trimmed.substringAfter("#EXTINF:").substringBefore(",")
                val sec = secStr.toDoubleOrNull() ?: 0.0
                currentDurationMs = (sec * 1000.0).toLong()
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val segUrl = resolveUrl(baseUrl, trimmed)
                val endMs = currentStartMs + currentDurationMs.coerceAtLeast(1000L)
                result.add(HlsSegmentEntry(startMs = currentStartMs, endMs = endMs, segmentUrl = segUrl))
                currentStartMs = endMs
                currentDurationMs = 0L
            }
        }
        return result
    }

    private fun downloadAndExtractSegmentFrame(
        segmentUrl: String,
        headers: Map<String, String>,
        offsetMsInSegment: Long
    ): Bitmap? {
        val tempDir = context?.cacheDir ?: return null
        val tempFile = File(tempDir, "hls_thumb_${segmentUrl.hashCode()}.tmp")
        val retriever = MediaMetadataRetriever()

        return try {
            val safeHeaders = sanitizeHeaders(headers).toMutableMap()
            if (!safeHeaders.containsKey("Range")) {
                safeHeaders["Range"] = "bytes=0-524287"
            }
            val reqBuilder = Request.Builder().url(segmentUrl)
            safeHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                FileOutputStream(tempFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
            }

            retriever.setDataSource(tempFile.absolutePath)
            val timeUs = (offsetMsInSegment.coerceAtLeast(0L)) * 1000L
            val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)

            if (raw != null) {
                scaleDownThumbnail(raw, 240, 135)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Throwable) {}
        }
    }

    private fun extractProgressiveFrame(
        streamUrl: String,
        headers: Map<String, String>,
        timestampMs: Long
    ): Bitmap? {
        val safeHeaders = sanitizeHeaders(headers)
        val finalUrl = resolveRedirectUrl(streamUrl, safeHeaders)

        return synchronized(retrieverLock) {
            try {
                if (activeRetrieverUrl != finalUrl || activeRetriever == null) {
                    try {
                        activeRetriever?.release()
                    } catch (_: Throwable) {}

                    val newRetriever = MediaMetadataRetriever()
                    newRetriever.setDataSource(finalUrl, safeHeaders)
                    activeRetriever = newRetriever
                    activeRetrieverUrl = finalUrl
                }

                val current = activeRetriever ?: return null
                val timeUs = timestampMs * 1000L
                val raw = current.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: current.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                if (raw != null) {
                    scaleDownThumbnail(raw, 240, 135)
                } else {
                    null
                }
            } catch (_: Throwable) {
                try {
                    activeRetriever?.release()
                } catch (_: Throwable) {}
                activeRetriever = null
                activeRetrieverUrl = null
                null
            }
        }
    }

    private fun resolveRedirectUrl(url: String, headers: Map<String, String>): String {
        return try {
            val reqBuilder = Request.Builder().url(url).head()
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                resp.request.url.toString()
            }
        } catch (_: Throwable) {
            url
        }
    }

    private fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
        val map = headers.toMutableMap()
        if (!map.containsKey("User-Agent")) {
            map["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        }
        if (!map.containsKey("Accept")) {
            map["Accept"] = "*/*"
        }
        if (!map.containsKey("Connection")) {
            map["Connection"] = "keep-alive"
        }
        return map
    }

    private fun scaleDownThumbnail(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcW = source.width
        val srcH = source.height
        if (srcW <= targetW && srcH <= targetH) {
            return source
        }
        val aspectRatio = srcW.toFloat() / srcH.toFloat()
        val finalW = if (aspectRatio > 1f) targetW else (targetH * aspectRatio).toInt()
        val finalH = if (aspectRatio > 1f) (targetW / aspectRatio).toInt() else targetH
        return Bitmap.createScaledBitmap(source, finalW.coerceAtLeast(1), finalH.coerceAtLeast(1), true)
    }

    private fun loadAndCropSprite(cue: ThumbnailSpriteCue): Bitmap? {
        return try {
            val fullSpriteKey = "sprite_${cue.imageUrl.hashCode()}"
            var fullBitmap = cacheManager?.getBitmap(fullSpriteKey)
            if (fullBitmap == null) {
                val req = Request.Builder().url(cue.imageUrl).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null) {
                            fullBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (fullBitmap != null) {
                                cacheManager?.putBitmap(fullSpriteKey, fullBitmap!!)
                            }
                        }
                    }
                }
            }

            val sprite = fullBitmap ?: return null
            if (cue.width > 0 && cue.height > 0 &&
                cue.x + cue.width <= sprite.width &&
                cue.y + cue.height <= sprite.height
            ) {
                Bitmap.createBitmap(sprite, cue.x, cue.y, cue.width, cue.height)
            } else {
                sprite
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun parseWebVtt(vttContent: String, basePlaylistUrl: String): List<ThumbnailSpriteCue> {
        val cues = mutableListOf<ThumbnailSpriteCue>()
        val lines = vttContent.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val timeParts = line.split("-->").map { it.trim() }
                if (timeParts.size == 2) {
                    val startMs = parseVttTimestamp(timeParts[0])
                    val endMs = parseVttTimestamp(timeParts[1])

                    if (i + 1 < lines.size) {
                        val payload = lines[i + 1].trim()
                        if (payload.isNotBlank() && !payload.contains("-->")) {
                            val cue = parseCuePayload(startMs, endMs, payload, basePlaylistUrl)
                            if (cue != null) {
                                cues.add(cue)
                            }
                            i++
                        }
                    }
                }
            }
            i++
        }
        return cues
    }

    private fun parseCuePayload(
        startMs: Long,
        endMs: Long,
        payload: String,
        basePlaylistUrl: String
    ): ThumbnailSpriteCue? {
        val parts = payload.split("#xywh=")
        val rawUrl = parts[0].trim()
        val fullUrl = resolveUrl(basePlaylistUrl, rawUrl)

        if (parts.size > 1) {
            val coords = parts[1].split(",").mapNotNull { it.trim().toIntOrNull() }
            if (coords.size == 4) {
                return ThumbnailSpriteCue(
                    startMs = startMs,
                    endMs = endMs,
                    imageUrl = fullUrl,
                    x = coords[0],
                    y = coords[1],
                    width = coords[2],
                    height = coords[3]
                )
            }
        }
        return ThumbnailSpriteCue(
            startMs = startMs,
            endMs = endMs,
            imageUrl = fullUrl
        )
    }

    private fun parseVttTimestamp(timestampStr: String): Long {
        return try {
            val parts = timestampStr.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val sParts = parts[2].split(".")
                val s = sParts[0].toLong()
                val ms = if (sParts.size > 1) sParts[1].padEnd(3, '0').take(3).toLong() else 0L
                (h * 3600000L) + (m * 60000L) + (s * 1000L) + ms
            } else if (parts.size == 2) {
                val m = parts[0].toLong()
                val sParts = parts[1].split(".")
                val s = sParts[0].toLong()
                val ms = if (sParts.size > 1) sParts[1].padEnd(3, '0').take(3).toLong() else 0L
                (m * 60000L) + (s * 1000L) + ms
            } else {
                0L
            }
        } catch (_: Throwable) {
            0L
        }
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return try {
            if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
                relativeOrAbsolute
            } else {
                URL(URL(baseUrl), relativeOrAbsolute).toString()
            }
        } catch (_: Throwable) {
            relativeOrAbsolute
        }
    }

    fun releaseSession() {
        synchronized(retrieverLock) {
            try {
                activeRetriever?.release()
            } catch (_: Throwable) {}
            activeRetriever = null
            activeRetrieverUrl = null
        }
        hlsSegmentsByStream.clear()
        vttCuesByStream.clear()
    }
}