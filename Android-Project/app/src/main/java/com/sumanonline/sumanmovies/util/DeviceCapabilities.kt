package com.sumanonline.sumanmovies.util

import android.media.MediaCodecList
import android.media.MediaFormat

object DeviceCapabilities {
    private var cachedSupports4kHevc: Boolean? = null

    fun supports4KHevc(): Boolean {
        cachedSupports4kHevc?.let { return it }
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var supported = false
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                        val caps = info.getCapabilitiesForType(type)
                        val videoCaps = caps.videoCapabilities
                        if (videoCaps != null && videoCaps.isSizeSupported(3840, 2160)) {
                            supported = true
                            break
                        }
                    }
                }
                if (supported) break
            }
            cachedSupports4kHevc = supported
            supported
        } catch (_: Exception) {
            cachedSupports4kHevc = false
            false
        }
    }
}
