package com.sumanonline.sumanmovies.ui.screens.player.thumbnail

import android.graphics.Bitmap

data class ThumbnailFrame(
    val timestampMs: Long,
    val bitmap: Bitmap?,
    val isPlaceholder: Boolean = false,
    val placeholderUrl: String? = null
)

data class ThumbnailSpriteCue(
    val startMs: Long,
    val endMs: Long,
    val imageUrl: String,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0
) {
    fun contains(timeMs: Long): Boolean = timeMs in startMs until endMs
}

data class HlsSegmentEntry(
    val startMs: Long,
    val endMs: Long,
    val segmentUrl: String
) {
    fun contains(timeMs: Long): Boolean = timeMs in startMs until endMs
}