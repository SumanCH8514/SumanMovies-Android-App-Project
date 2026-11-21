package com.sumanonline.sumanmovies.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object StreamHealthValidator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val healthCache = ConcurrentHashMap<String, Boolean>()

    fun normalizeDirectUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.contains("pixeldrain.com") || trimmed.contains("pixeldrain.dev")) {
            val idMatch = Regex("""pixeldrain\.(?:com|dev)/(?:u|api/file)/([a-zA-Z0-9_-]+)""").find(trimmed)
            val fileId = idMatch?.groupValues?.get(1)
            if (!fileId.isNullOrBlank()) {
                return "https://pixeldrain.com/api/file/$fileId?download"
            }
        }
        return trimmed
    }

    suspend fun isStreamableMediaUrl(url: String, headers: Map<String, String> = emptyMap()): Boolean = withContext(Dispatchers.IO) {
        val normalized = normalizeDirectUrl(url)
        if (normalized.isBlank() || !normalized.startsWith("http")) return@withContext false

        healthCache[normalized]?.let { return@withContext it }

        val isValid = checkUrlDirect(normalized, headers)
        healthCache[normalized] = isValid
        healthCache[url.trim()] = isValid
        isValid
    }

    private fun checkUrlDirect(url: String, headers: Map<String, String>): Boolean {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Range", "bytes=0-1024")

            headers.forEach { (k, v) ->
                if (!k.equals("Range", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            val response = client.newCall(reqBuilder.build()).execute()
            response.use { resp ->
                val code = resp.code
                if (code !in 200..299 && code != 206) {
                    return false
                }

                val contentType = resp.header("Content-Type")?.lowercase() ?: ""
                if (contentType.contains("application/json") || contentType.contains("text/html")) {
                    return false
                }

                val contentDisposition = resp.header("Content-Disposition")?.lowercase() ?: ""
                val hasMediaDisposition = contentDisposition.contains(".mkv") || contentDisposition.contains(".mp4") || contentDisposition.contains(".webm") || contentDisposition.contains(".avi")
                val isMediaContent = contentType.contains("video/") || contentType.contains("octet-stream") || contentType.contains("application/x-matroska") || contentType.contains("audio/") || contentType.isBlank()

                if (!isMediaContent && !hasMediaDisposition) {
                    return false
                }

                val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                if (contentLength in 1..2048 && (contentType.contains("text/") || contentType.contains("json"))) {
                    return false
                }

                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun markHealth(url: String, isOnline: Boolean) {
        val norm = normalizeDirectUrl(url)
        healthCache[norm] = isOnline
        healthCache[url.trim()] = isOnline
    }

    fun getCachedHealth(url: String): Boolean? {
        val norm = normalizeDirectUrl(url)
        return healthCache[norm] ?: healthCache[url.trim()]
    }
}
