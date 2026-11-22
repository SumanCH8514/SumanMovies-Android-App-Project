package com.sumanonline.sumanmovies.ui.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.sumanonline.sumanmovies.data.models.StreamSource
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object PlaybackNetworkConfig {
    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 20L

    val streamingHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
    }

    fun buildHttpDataSourceFactory(
        stream: StreamSource
    ): DataSource.Factory {
        val defaultUa = stream.headers["User-Agent"]
            ?: if (stream.provider == "moviebox") {
                "com.community.oneroom/50020120 (Linux; U; Android 12; en_US; 22101316G)"
            } else {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
            }

        val sanitizedHeaders = sanitizeHeaders(stream, defaultUa)

        return try {
            val okHttpFactory = OkHttpDataSource.Factory(streamingHttpClient)
                .setUserAgent(defaultUa)
                .setDefaultRequestProperties(sanitizedHeaders)
            okHttpFactory
        } catch (_: Throwable) {
            DefaultHttpDataSource.Factory()
                .setUserAgent(defaultUa)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(sanitizedHeaders)
        }
    }

    private fun sanitizeHeaders(stream: StreamSource, defaultUa: String): Map<String, String> {
        val headers = stream.headers.toMutableMap()
        if (!headers.containsKey("User-Agent")) {
            headers["User-Agent"] = defaultUa
        }
        if (!headers.containsKey("Accept")) {
            headers["Accept"] = "*/*"
        }
        if (!headers.containsKey("Accept-Encoding")) {
            headers["Accept-Encoding"] = "identity"
        }
        if (!headers.containsKey("Connection")) {
            headers["Connection"] = "keep-alive"
        }

        val uriHost = try {
            android.net.Uri.parse(stream.url).host?.lowercase() ?: ""
        } catch (_: Throwable) {
            ""
        }

        val isDirectCdn = uriHost.contains("pixeldrain") ||
                uriHost.contains("googleusercontent") ||
                uriHost.contains("r2.cloudflarestorage") ||
                uriHost.contains("r2.dev") ||
                uriHost.contains("workers.dev") ||
                uriHost.contains("drive.google.com") ||
                uriHost.contains("video-gen.xyz") ||
                uriHost.contains("driveseed.org") ||
                uriHost.contains("fastdl") ||
                uriHost.contains("10gbps") ||
                uriHost.contains("stream") ||
                uriHost.contains("workers") ||
                uriHost.contains("mediafire") ||
                stream.provider == "moviesleech" ||
                stream.provider == "animexin"

        if (isDirectCdn || stream.provider == "hdhub4u" || stream.provider == "fourkhdhub" || stream.provider == "moviesleech" || stream.provider == "animexin") {
            val referer = headers["Referer"]
            if (referer != null) {
                val refererHost = try {
                    android.net.Uri.parse(referer).host?.lowercase() ?: ""
                } catch (_: Throwable) {
                    ""
                }
                if (uriHost.isBlank() || refererHost.isBlank() || !uriHost.endsWith(refererHost)) {
                    headers.remove("Referer")
                }
            }
        }

        if (stream.provider == "moviebox" && !headers.containsKey("Referer")) {
            if (stream.format.equals("DASH", ignoreCase = true) || stream.url.contains(".mpd")) {
                headers["Referer"] = "https://sportslive.wine"
            }
        }

        val cookie = headers["Cookie"]
        val edgeCookie = headers["Edge-Cache-Cookie"]
        if (cookie != null && edgeCookie == null && cookie.contains("Edge-Cache-Cookie=")) {
            headers["Edge-Cache-Cookie"] = cookie.substringAfter("Edge-Cache-Cookie=").substringBefore(";")
        } else if (edgeCookie != null && cookie == null) {
            headers["Cookie"] = if (edgeCookie.startsWith("Edge-Cache-Cookie=")) edgeCookie else "Edge-Cache-Cookie=$edgeCookie"
        }

        return headers
    }
}
