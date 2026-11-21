package com.sumanonline.sumanmovies.ui.screens.player.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ThumbnailRepository(
    private val context: Context? = null,
    val cacheManager: ThumbnailCacheManager? = context?.let { ThumbnailCacheManager(it) }
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val vttCuesByStream = ConcurrentHashMap<String, List<ThumbnailSpriteCue>>()

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

        val extractedBitmap = extractVideoFrame(streamUrl, headers, bucketTimestampMs)
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

    private fun extractVideoFrame(
        streamUrl: String,
        headers: Map<String, String>,
        timestampMs: Long
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (headers.isNotEmpty()) {
                retriever.setDataSource(streamUrl, headers)
            } else {
                retriever.setDataSource(streamUrl, HashMap<String, String>())
            }

            val timeUs = timestampMs * 1000L
            val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)

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
        }
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
}
