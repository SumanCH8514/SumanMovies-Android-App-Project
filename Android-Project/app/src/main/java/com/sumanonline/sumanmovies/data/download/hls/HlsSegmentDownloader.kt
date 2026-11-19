package com.sumanonline.sumanmovies.data.download.hls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

class HlsSegmentDownloader(
    private val httpClient: OkHttpClient,
    private val concurrency: Int = 3
) {
    private val keyCache = ConcurrentHashMap<String, ByteArray>()

    suspend fun downloadSegments(
        playlist: HlsMediaPlaylist,
        headers: Map<String, String>,
        tempDir: File,
        onProgress: (downloadedBytes: Long, totalEstimatedBytes: Long, downloadedSegments: Int, totalSegments: Int, speedBytesPerSec: Long) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val totalSegmentsCount = (if (playlist.initSegmentUrl != null) 1 else 0) + playlist.segments.size
        val downloadedBytesCounter = AtomicLong(0L)
        val downloadedSegmentsCounter = AtomicLong(0L)

        val orderedFiles = mutableListOf<File>()

        playlist.initSegmentUrl?.let { initUrl ->
            val initFile = File(tempDir, "init.mp4")
            if (initFile.exists() && initFile.length() > 0) {
                downloadedBytesCounter.addAndGet(initFile.length())
                downloadedSegmentsCounter.incrementAndGet()
            } else {
                downloadRawFile(initUrl, headers, initFile, null, null)
                downloadedBytesCounter.addAndGet(initFile.length())
                downloadedSegmentsCounter.incrementAndGet()
            }
            orderedFiles.add(initFile)
        }

        for (seg in playlist.segments) {
            val segFile = File(tempDir, "seg_${String.format("%06d", seg.sequence)}.bin")
            if (segFile.exists() && segFile.length() > 0) {
                downloadedBytesCounter.addAndGet(segFile.length())
                downloadedSegmentsCounter.incrementAndGet()
            }
            orderedFiles.add(segFile)
        }

        val lastTime = AtomicLong(System.currentTimeMillis())
        val bytesSinceLastCheck = AtomicLong(0L)
        val currentSpeed = AtomicLong(0L)

        val updateProgressUi = {
            val now = System.currentTimeMillis()
            val elapsed = now - lastTime.get()
            if (elapsed >= 500) {
                val bytes = bytesSinceLastCheck.getAndSet(0L)
                val sec = elapsed / 1000.0
                if (sec > 0) {
                    currentSpeed.set((bytes / sec).toLong())
                }
                lastTime.set(now)
            }

            val curDownloaded = downloadedBytesCounter.get()
            val curSegs = downloadedSegmentsCounter.get().toInt()
            val totalSegs = totalSegmentsCount
            val estimatedTotal = if (curSegs > 0) {
                (curDownloaded / curSegs.toDouble() * totalSegs).toLong()
            } else {
                0L
            }

            onProgress(curDownloaded, estimatedTotal, curSegs, totalSegs, currentSpeed.get())
        }

        updateProgressUi()

        val semaphore = Semaphore(concurrency)
        val isCancelled = AtomicBoolean(false)

        coroutineScope {
            val tasks = playlist.segments.map { seg ->
                async(Dispatchers.IO) {
                    val segFile = File(tempDir, "seg_${String.format("%06d", seg.sequence)}.bin")
                    if (segFile.exists() && segFile.length() > 0) {
                        return@async
                    }

                    if (!coroutineContext.isActive || isCancelled.get()) {
                        return@async
                    }

                    semaphore.withPermit {
                        if (!coroutineContext.isActive || isCancelled.get()) {
                            return@withPermit
                        }

                        val downloaded = downloadAndDecryptSegment(seg, headers, segFile)
                        if (downloaded > 0) {
                            downloadedBytesCounter.addAndGet(downloaded)
                            bytesSinceLastCheck.addAndGet(downloaded)
                            downloadedSegmentsCounter.incrementAndGet()
                            updateProgressUi()
                        }
                    }
                }
            }

            tasks.awaitAll()
        }

        if (!coroutineContext.isActive) {
            throw kotlinx.coroutines.CancellationException("Download was paused or cancelled")
        }

        val incomplete = orderedFiles.filter { !it.exists() || it.length() == 0L }
        if (incomplete.isNotEmpty()) {
            throw IllegalStateException("Failed to download ${incomplete.size} segments out of $totalSegmentsCount")
        }

        orderedFiles
    }

    private suspend fun downloadAndDecryptSegment(
        segment: HlsSegment,
        headers: Map<String, String>,
        outputFile: File
    ): Long = withContext(Dispatchers.IO) {
        val tempPart = File("${outputFile.absolutePath}.tmp")
        try {
            var rawBytes = fetchSegmentBytes(segment.url, headers, segment.byteRangeOffset, segment.byteRangeLength)

            val enc = segment.encryption
            if (enc != null && enc.method.equals("AES-128", ignoreCase = true)) {
                val key = getKey(enc.keyUrl, headers)
                val iv = enc.iv ?: HlsPlaylistParser.sequenceToIv(segment.sequence)
                rawBytes = decryptAes128(rawBytes, key, iv)
            }

            FileOutputStream(tempPart).use { fos ->
                fos.write(rawBytes)
            }

            if (tempPart.exists() && tempPart.length() > 0) {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                tempPart.renameTo(outputFile)
                outputFile.length()
            } else {
                0L
            }
        } finally {
            if (tempPart.exists()) {
                tempPart.delete()
            }
        }
    }

    private fun fetchSegmentBytes(
        url: String,
        headers: Map<String, String>,
        byteRangeOffset: Long?,
        byteRangeLength: Long?
    ): ByteArray {
        val reqBuilder = Request.Builder().url(url)
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

        if (byteRangeLength != null && byteRangeOffset != null) {
            val end = byteRangeOffset + byteRangeLength - 1
            reqBuilder.header("Range", "bytes=$byteRangeOffset-$end")
        } else if (byteRangeLength != null) {
            reqBuilder.header("Range", "bytes=0-${byteRangeLength - 1}")
        }

        var attempts = 0
        var lastEx: Exception? = null

        while (attempts < 3) {
            attempts++
            try {
                val resp = httpClient.newCall(reqBuilder.build()).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    resp.close()
                    if (bytes != null && bytes.isNotEmpty()) {
                        return bytes
                    }
                } else {
                    resp.close()
                }
            } catch (e: Exception) {
                lastEx = e
                try {
                    Thread.sleep(400L * attempts)
                } catch (_: Exception) {}
            }
        }

        throw lastEx ?: IllegalStateException("Failed to fetch segment from $url after 3 attempts")
    }

    private fun downloadRawFile(
        url: String,
        headers: Map<String, String>,
        outputFile: File,
        byteRangeOffset: Long?,
        byteRangeLength: Long?
    ) {
        val bytes = fetchSegmentBytes(url, headers, byteRangeOffset, byteRangeLength)
        FileOutputStream(outputFile).use { fos ->
            fos.write(bytes)
        }
    }

    private fun getKey(keyUrl: String, headers: Map<String, String>): ByteArray {
        val cached = keyCache[keyUrl]
        if (cached != null) return cached

        val bytes = fetchSegmentBytes(keyUrl, headers, null, null)
        if (bytes.size != 16) {
            throw IllegalStateException("Invalid AES-128 key length: ${bytes.size} bytes (expected 16)")
        }
        keyCache[keyUrl] = bytes
        return bytes
    }

    private fun decryptAes128(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)

        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            try {
                val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                cipher.doFinal(ciphertext)
            } catch (e2: Exception) {
                throw IllegalStateException("AES-128 decryption failed: ${e2.message}", e2)
            }
        }
    }
}
