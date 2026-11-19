package com.sumanonline.sumanmovies.data.models

data class TvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val country: String? = null,
    val language: String? = null,
    val isLive: Boolean = true
) {
    fun toMediaItem(): MediaItem = MediaItem(
        id = "tv:$id",
        title = name,
        mediaType = MediaType.TV_CHANNEL,
        posterUrl = logoUrl,
        overview = groupTitle ?: "Live TV Channel",
        provider = "tv"
    )

    fun toStreamSource(): StreamSource = StreamSource(
        quality = "Live HD",
        format = if (streamUrl.contains(".m3u8", ignoreCase = true) || streamUrl.contains("hls", ignoreCase = true)) "hls" else "mp4",
        url = streamUrl,
        label = "$name (Live TV)",
        provider = "tv",
        directFile = true
    )
}
