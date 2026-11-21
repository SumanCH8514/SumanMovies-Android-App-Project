package com.sumanonline.sumanmovies.ui.screens.player.thumbnail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThumbnailPreviewManager(
    private val scope: CoroutineScope,
    val repository: ThumbnailRepository
) {
    private val _previewFrame = MutableStateFlow<ThumbnailFrame?>(null)
    val previewFrame: StateFlow<ThumbnailFrame?> = _previewFrame.asStateFlow()

    private var activeJob: Job? = null
    private var lastRequestedTimestampMs: Long = -1L

    fun requestPreview(
        timestampMs: Long,
        mediaId: String,
        streamUrl: String?,
        headers: Map<String, String>,
        posterUrl: String? = null
    ) {
        val targetMs = timestampMs.coerceAtLeast(0L)
        if (targetMs == lastRequestedTimestampMs && _previewFrame.value != null) {
            return
        }
        lastRequestedTimestampMs = targetMs

        activeJob?.cancel()
        activeJob = scope.launch {
            delay(50L)
            val frame = repository.getThumbnail(
                mediaId = mediaId,
                streamUrl = streamUrl,
                headers = headers,
                timestampMs = targetMs,
                posterUrl = posterUrl
            )
            _previewFrame.value = frame
        }
    }

    fun clearPreview() {
        lastRequestedTimestampMs = -1L
        activeJob?.cancel()
        activeJob = null
        _previewFrame.value = null
    }
}
