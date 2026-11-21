package com.sumanonline.sumanmovies.data.models

enum class MediaSegmentType {
    INTRO,
    RECAP,
    CREDITS,
    PREVIEW
}

data class MediaSegment(
    val type: MediaSegmentType,
    val startMs: Long,
    val endMs: Long?
) {
    fun contains(positionMs: Long): Boolean {
        val end = endMs ?: Long.MAX_VALUE
        return positionMs in startMs until end
    }

    fun targetPosition(durationMs: Long): Long {
        return when {
            endMs != null && endMs > 0L -> endMs + 200L
            durationMs > 0L -> (durationMs - 1000L).coerceAtLeast(0L)
            else -> startMs + 30_000L
        }
    }

    val actionLabel: String
        get() = when (type) {
            MediaSegmentType.INTRO -> "Skip Intro"
            MediaSegmentType.RECAP -> "Skip Recap"
            MediaSegmentType.CREDITS -> "Skip Credits"
            MediaSegmentType.PREVIEW -> "Skip Preview"
        }
}
