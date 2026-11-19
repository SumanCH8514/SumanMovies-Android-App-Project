package com.sumanonline.sumanmovies.data.download.hls

data class HlsMasterPlaylist(
    val variants: List<HlsVariant>
)

data class HlsVariant(
    val url: String,
    val resolution: String?,
    val bandwidth: Long,
    val codecs: String?,
    val qualityLabel: String
)

data class HlsMediaPlaylist(
    val targetDurationSec: Double,
    val mediaSequence: Long,
    val initSegmentUrl: String?,
    val segments: List<HlsSegment>,
    val isEndlist: Boolean
)

data class HlsSegment(
    val sequence: Long,
    val url: String,
    val durationSec: Double,
    val byteRangeOffset: Long? = null,
    val byteRangeLength: Long? = null,
    val encryption: HlsEncryption? = null
)

data class HlsEncryption(
    val method: String,
    val keyUrl: String,
    val iv: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HlsEncryption
        if (method != other.method) return false
        if (keyUrl != other.keyUrl) return false
        if (iv != null) {
            if (other.iv == null) return false
            if (!iv.contentEquals(other.iv)) return false
        } else if (other.iv != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + keyUrl.hashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        return result
    }
}
