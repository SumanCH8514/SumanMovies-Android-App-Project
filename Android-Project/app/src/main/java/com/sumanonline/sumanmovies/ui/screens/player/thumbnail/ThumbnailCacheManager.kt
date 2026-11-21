package com.sumanonline.sumanmovies.ui.screens.player.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream

class ThumbnailCacheManager(context: Context) {
    private val diskCacheDir = File(context.applicationContext.cacheDir, "player_thumbnail_cache").apply {
        if (!exists()) mkdirs()
    }

    private val maxMemoryBytes = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(4 * 1024 * 1024, 24 * 1024 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun getBitmap(key: String): Bitmap? {
        val mem = memoryCache.get(key)
        if (mem != null && !mem.isRecycled) {
            return mem
        }

        val diskFile = File(diskCacheDir, sanitizeFileName(key))
        if (diskFile.exists() && diskFile.length() > 0) {
            return try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                }
                bitmap
            } catch (_: Throwable) {
                null
            }
        }
        return null
    }

    fun putBitmap(key: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        memoryCache.put(key, bitmap)

        try {
            val diskFile = File(diskCacheDir, sanitizeFileName(key))
            FileOutputStream(diskFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.flush()
            }
            trimDiskCacheIfNeeded()
        } catch (_: Throwable) {}
    }

    fun clearCache() {
        memoryCache.evictAll()
        try {
            if (diskCacheDir.exists()) {
                diskCacheDir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Throwable) {}
    }

    private fun trimDiskCacheIfNeeded() {
        val maxDiskBytes = 30L * 1024L * 1024L
        val files = diskCacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize > maxDiskBytes) {
            val sorted = files.sortedBy { it.lastModified() }
            for (f in sorted) {
                totalSize -= f.length()
                f.delete()
                if (totalSize <= maxDiskBytes * 0.75) break
            }
        }
    }

    private fun sanitizeFileName(key: String): String {
        return key.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(80) + ".jpg"
    }
}
