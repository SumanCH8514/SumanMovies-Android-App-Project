package com.sumanonline.sumanmovies.data.download.remux

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

object MediaRemuxer {

    suspend fun remuxSegments(
        context: Context,
        segmentFiles: List<File>,
        targetFile: File,
        tempDir: File
    ): File = withContext(Dispatchers.IO) {
        if (segmentFiles.isEmpty()) {
            throw IllegalArgumentException("Cannot remux empty segment list")
        }

        val concatenatedFile = File(tempDir, "concatenated.raw")
        if (concatenatedFile.exists()) {
            concatenatedFile.delete()
        }

        FileOutputStream(concatenatedFile).use { out ->
            val buf = ByteArray(128 * 1024)
            for (seg in segmentFiles) {
                if (seg.exists() && seg.length() > 0) {
                    FileInputStream(seg).use { input ->
                        var r: Int
                        while (input.read(buf).also { r = it } != -1) {
                            out.write(buf, 0, r)
                        }
                    }
                }
            }
        }

        if (!concatenatedFile.exists() || concatenatedFile.length() == 0L) {
            throw IllegalStateException("Concatenated media file is missing or zero bytes")
        }

        val isFmp4 = isMp4OrFmp4(concatenatedFile)
        var finalProducedFile = targetFile

        if (isFmp4) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = concatenatedFile.renameTo(targetFile)
            if (!renamed) {
                copyFile(concatenatedFile, targetFile)
                concatenatedFile.delete()
            }
            finalProducedFile = targetFile
        } else {
            var remuxSuccess = false
            try {
                remuxWithMediaMuxer(concatenatedFile, targetFile)
                if (targetFile.exists() && targetFile.length() > 1024) {
                    remuxSuccess = true
                    finalProducedFile = targetFile
                }
            } catch (_: Exception) {
                remuxSuccess = false
            }

            if (!remuxSuccess) {
                val fallbackFile = if (targetFile.name.endsWith(".mp4", ignoreCase = true)) {
                    File(targetFile.parentFile, targetFile.nameWithoutExtension + ".mkv")
                } else {
                    targetFile
                }
                if (fallbackFile.exists()) {
                    fallbackFile.delete()
                }
                val renamed = concatenatedFile.renameTo(fallbackFile)
                if (!renamed) {
                    copyFile(concatenatedFile, fallbackFile)
                    concatenatedFile.delete()
                }
                if (targetFile.exists() && targetFile != fallbackFile) {
                    try { targetFile.delete() } catch (_: Exception) {}
                }
                finalProducedFile = fallbackFile
            } else {
                concatenatedFile.delete()
            }
        }

        cleanupTempDir(tempDir)

        try {
            MediaScannerConnection.scanFile(context, arrayOf(finalProducedFile.absolutePath), null, null)
        } catch (_: Exception) {}

        finalProducedFile
    }

    private fun remuxWithMediaMuxer(sourceFile: File, destFile: File) {
        if (destFile.exists()) {
            destFile.delete()
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(sourceFile.absolutePath)
            muxer = MediaMuxer(destFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackCount = extractor.trackCount
            val trackMap = HashMap<Int, Int>()

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val muxerTrack = muxer.addTrack(format)
                    trackMap[i] = muxerTrack
                }
            }

            if (trackMap.isEmpty()) {
                throw IllegalStateException("No compatible video or audio tracks found in stream")
            }

            muxer.start()

            val maxBufferSize = 1024 * 1024
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val muxerTrack = trackMap[trackIndex]
                if (muxerTrack != null) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize > 0) {
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        val sampleFlags = extractor.sampleFlags
                        var bufferFlags = 0
                        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            bufferFlags = bufferFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                            bufferFlags = bufferFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                        }
                        bufferInfo.flags = bufferFlags
                        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    }
                }
                extractor.advance()
            }
        } finally {
            try {
                muxer?.stop()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private fun isMp4OrFmp4(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        FileInputStream(file).use { fis ->
            val header = ByteArray(12)
            val read = fis.read(header)
            if (read >= 8) {
                val boxType = String(header, 4, 4, Charsets.US_ASCII)
                return boxType == "ftyp" || boxType == "moof" || boxType == "styp" || boxType == "free"
            }
        }
        return false
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { inStream ->
            FileOutputStream(dst).use { outStream ->
                val buf = ByteArray(128 * 1024)
                var r: Int
                while (inStream.read(buf).also { r = it } != -1) {
                    outStream.write(buf, 0, r)
                }
            }
        }
    }

    private fun cleanupTempDir(tempDir: File) {
        try {
            tempDir.listFiles()?.forEach { f ->
                try {
                    f.delete()
                } catch (_: Exception) {}
            }
            tempDir.delete()
        } catch (_: Exception) {}
    }
}
