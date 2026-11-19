package com.sumanonline.sumanmovies.data.download.hls

import java.net.URI

object HlsPlaylistParser {

    fun isHlsUrl(url: String): Boolean {
        val clean = url.substringBefore("?").lowercase()
        return clean.endsWith(".m3u8") || url.contains(".m3u8?", ignoreCase = true) || url.contains("/hls/", ignoreCase = true)
    }

    fun isMasterPlaylist(content: String): Boolean {
        return content.contains("#EXT-X-STREAM-INF")
    }

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val trimmed = relativeUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(trimmed).toString()
        } catch (_: Exception) {
            if (trimmed.startsWith("/")) {
                val scheme = baseUrl.substringBefore("://", "https")
                val host = baseUrl.removePrefix("$scheme://").substringBefore("/")
                "$scheme://$host$trimmed"
            } else {
                val parent = baseUrl.substringBeforeLast("/")
                "$parent/$trimmed"
            }
        }
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").removePrefix("0X")
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            val high = Character.digit(clean[i], 16)
            val low = Character.digit(clean[i + 1], 16)
            if (high != -1 && low != -1) {
                data[i / 2] = ((high shl 4) + low).toByte()
            }
            i += 2
        }
        return data
    }

    fun sequenceToIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var seq = sequence
        for (i in 15 downTo 8) {
            iv[i] = (seq and 0xFF).toByte()
            seq = seq ushr 8
        }
        return iv
    }

    fun parseMasterPlaylist(content: String, baseUrl: String): HlsMasterPlaylist {
        val variants = mutableListOf<HlsVariant>()
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val params = line.removePrefix("#EXT-X-STREAM-INF:")
                var bandwidth = 0L
                val bwMatch = Regex("""BANDWIDTH=([0-9]+)""").find(params)
                if (bwMatch != null) {
                    bandwidth = bwMatch.groupValues[1].toLongOrNull() ?: 0L
                }

                var resolution: String? = null
                val resMatch = Regex("""RESOLUTION=([0-9]+x[0-9]+)""").find(params)
                if (resMatch != null) {
                    resolution = resMatch.groupValues[1]
                }

                var codecs: String? = null
                val codecMatch = Regex("""CODECS="([^"]+)"""").find(params)
                if (codecMatch != null) {
                    codecs = codecMatch.groupValues[1]
                }

                var qualityLabel = "HD"
                if (resolution != null) {
                    val h = resolution.substringAfter("x").toIntOrNull() ?: 0
                    qualityLabel = when {
                        h >= 2160 -> "4K"
                        h >= 1080 -> "1080p"
                        h >= 720 -> "720p"
                        h >= 480 -> "480p"
                        h > 0 -> "360p"
                        else -> "HD"
                    }
                } else if (bandwidth > 0) {
                    qualityLabel = when {
                        bandwidth >= 8_000_000L -> "4K"
                        bandwidth >= 4_000_000L -> "1080p"
                        bandwidth >= 2_000_000L -> "720p"
                        bandwidth >= 1_000_000L -> "480p"
                        else -> "360p"
                    }
                }

                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) {
                    j++
                }

                if (j < lines.size) {
                    val rawUrl = lines[j].trim()
                    val absUrl = resolveUrl(baseUrl, rawUrl)
                    variants.add(
                        HlsVariant(
                            url = absUrl,
                            resolution = resolution,
                            bandwidth = bandwidth,
                            codecs = codecs,
                            qualityLabel = qualityLabel
                        )
                    )
                    i = j
                }
            }
            i++
        }

        variants.sortByDescending { it.bandwidth }
        return HlsMasterPlaylist(variants)
    }

    fun parseMediaPlaylist(content: String, baseUrl: String): HlsMediaPlaylist {
        val segments = mutableListOf<HlsSegment>()
        var targetDuration = 10.0
        var currentSequence = 0L
        var initSegmentUrl: String? = null
        var isEndlist = false
        var currentEncryption: HlsEncryption? = null
        var currentByterangeLength: Long? = null
        var currentByterangeOffset: Long? = null
        var previousByterangeEnd: Long = 0L

        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                targetDuration = line.removePrefix("#EXT-X-TARGETDURATION:").trim().toDoubleOrNull() ?: 10.0
            } else if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                currentSequence = line.removePrefix("#EXT-X-MEDIA-SEQUENCE:").trim().toLongOrNull() ?: 0L
            } else if (line.startsWith("#EXT-X-MAP:")) {
                val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                if (uriMatch != null) {
                    initSegmentUrl = resolveUrl(baseUrl, uriMatch.groupValues[1])
                }
            } else if (line.startsWith("#EXT-X-KEY:")) {
                val methodMatch = Regex("""METHOD=([^,]+)""").find(line)
                val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                val ivMatch = Regex("""IV=0[xX]([0-9a-fA-F]+)""").find(line)

                val method = methodMatch?.groupValues?.get(1) ?: "NONE"
                if (method.equals("NONE", ignoreCase = true)) {
                    currentEncryption = null
                } else if (method.equals("AES-128", ignoreCase = true) && uriMatch != null) {
                    val keyUrl = resolveUrl(baseUrl, uriMatch.groupValues[1])
                    val ivBytes = ivMatch?.let { hexToBytes(it.groupValues[1]) }
                    currentEncryption = HlsEncryption(
                        method = "AES-128",
                        keyUrl = keyUrl,
                        iv = ivBytes
                    )
                }
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                val rangeStr = line.removePrefix("#EXT-X-BYTERANGE:").trim()
                val parts = rangeStr.split("@")
                val length = parts[0].toLongOrNull()
                val offset = if (parts.size > 1) parts[1].toLongOrNull() else previousByterangeEnd
                currentByterangeLength = length
                currentByterangeOffset = offset
                if (length != null && offset != null) {
                    previousByterangeEnd = offset + length
                }
            } else if (line.startsWith("#EXTINF:")) {
                val durStr = line.removePrefix("#EXTINF:").substringBefore(",").trim()
                val duration = durStr.toDoubleOrNull() ?: targetDuration

                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || (lines[j].startsWith("#") && !lines[j].startsWith("#EXT-X-BYTERANGE:")))) {
                    if (lines[j].startsWith("#EXT-X-BYTERANGE:")) {
                        val rangeStr = lines[j].removePrefix("#EXT-X-BYTERANGE:").trim()
                        val parts = rangeStr.split("@")
                        val length = parts[0].toLongOrNull()
                        val offset = if (parts.size > 1) parts[1].toLongOrNull() else previousByterangeEnd
                        currentByterangeLength = length
                        currentByterangeOffset = offset
                        if (length != null && offset != null) {
                            previousByterangeEnd = offset + length
                        }
                    }
                    j++
                }

                if (j < lines.size) {
                    val segRaw = lines[j].trim()
                    if (!segRaw.startsWith("#")) {
                        val segAbs = resolveUrl(baseUrl, segRaw)
                        val enc = currentEncryption?.let { baseEnc ->
                            if (baseEnc.iv != null) {
                                baseEnc
                            } else {
                                baseEnc.copy(iv = sequenceToIv(currentSequence))
                            }
                        }

                        segments.add(
                            HlsSegment(
                                sequence = currentSequence,
                                url = segAbs,
                                durationSec = duration,
                                byteRangeOffset = currentByterangeOffset,
                                byteRangeLength = currentByterangeLength,
                                encryption = enc
                            )
                        )
                        currentSequence++
                        currentByterangeLength = null
                        currentByterangeOffset = null
                        i = j
                    }
                }
            } else if (line.startsWith("#EXT-X-ENDLIST")) {
                isEndlist = true
            }
            i++
        }

        return HlsMediaPlaylist(
            targetDurationSec = targetDuration,
            mediaSequence = currentSequence - segments.size,
            initSegmentUrl = initSegmentUrl,
            segments = segments,
            isEndlist = isEndlist
        )
    }
}
