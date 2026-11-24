package com.sumanonline.sumanmovies.data.cache

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object PlayerCacheManager {
    private const val CACHE_DIR_NAME = "exo_video_cache"
    private const val DEFAULT_CACHE_MAX_BYTES = 300L * 1024L * 1024L

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    @Volatile
    private var databaseProviderInstance: DatabaseProvider? = null

    private val lock = Any()

    @Volatile
    var configuredMaxCacheSizeBytes: Long = DEFAULT_CACHE_MAX_BYTES
        private set

    fun setMaxCacheSizeBytes(bytes: Long) {
        synchronized(lock) {
            configuredMaxCacheSizeBytes = bytes.coerceAtLeast(50L * 1024L * 1024L)
        }
    }

    fun getCacheDir(context: Context): File {
        return File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
    }

    fun getCache(context: Context): Cache? {
        val current = simpleCacheInstance
        if (current != null) {
            return current
        }
        return synchronized(lock) {
            simpleCacheInstance ?: try {
                val appContext = context.applicationContext
                val cacheDir = getCacheDir(appContext)
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                if (SimpleCache.isCacheFolderLocked(cacheDir)) {
                    simpleCacheInstance
                } else {
                    val dbProvider = databaseProviderInstance ?: StandaloneDatabaseProvider(appContext).also {
                        databaseProviderInstance = it
                    }
                    val evictor = LeastRecentlyUsedCacheEvictor(configuredMaxCacheSizeBytes)
                    val newCache = SimpleCache(cacheDir, evictor, dbProvider)
                    simpleCacheInstance = newCache
                    newCache
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun generateCacheKey(url: String): String {
        return try {
            val javaUri = java.net.URI(url)
            val scheme = javaUri.scheme?.lowercase() ?: "https"
            val host = javaUri.host?.lowercase() ?: ""
            val port = if (javaUri.port != -1) ":${javaUri.port}" else ""
            val path = javaUri.path ?: ""

            val ephemeralParams = setOf(
                "token", "expires", "expiry", "expire", "sig", "signature", "hmac",
                "auth", "key-pair-id", "policy", "ts", "t", "st", "e", "download",
                "session", "stream_token", "hash", "sign", "validto", "ip"
            )

            val rawQuery = javaUri.rawQuery
            val stableQuery = if (!rawQuery.isNullOrBlank()) {
                val queryParts = rawQuery.split("&")
                    .mapNotNull { pair ->
                        val parts = pair.split("=", limit = 2)
                        val key = parts[0]
                        val value = if (parts.size > 1) parts[1] else ""
                        if (!ephemeralParams.contains(key.lowercase())) {
                            "$key=$value"
                        } else null
                    }
                    .sorted()
                if (queryParts.isNotEmpty()) "?" + queryParts.joinToString("&") else ""
            } else ""

            if (host.isNotBlank() && path.isNotBlank()) {
                "$scheme://$host$port$path$stableQuery"
            } else {
                url.substringBefore("?")
            }
        } catch (_: Throwable) {
            url.substringBefore("?")
        }
    }

    fun generateCacheKey(uri: Uri): String {
        return try {
            val urlStr = uri.toString()
            if (urlStr.isNotBlank()) {
                generateCacheKey(urlStr)
            } else {
                ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    fun buildCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): DataSource.Factory {
        val cache = getCache(context) ?: return upstreamFactory

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun getCacheSizeBytes(context: Context): Long {
        val cache = simpleCacheInstance
        if (cache != null) {
            return try {
                cache.cacheSpace
            } catch (_: Throwable) {
                0L
            }
        }
        return try {
            val cacheDir = getCacheDir(context)
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                0L
            }
        } catch (_: Throwable) {
            0L
        }
    }

    fun clearCache(context: Context) {
        synchronized(lock) {
            try {
                val cache = simpleCacheInstance
                if (cache != null) {
                    val keys = cache.keys
                    for (k in keys) {
                        try {
                            cache.removeResource(k)
                        } catch (_: Throwable) {}
                    }
                } else {
                    val cacheDir = getCacheDir(context)
                    if (cacheDir.exists() && cacheDir.isDirectory) {
                        cacheDir.listFiles()?.forEach { file ->
                            try {
                                file.deleteRecursively()
                            } catch (_: Throwable) {}
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }
}
