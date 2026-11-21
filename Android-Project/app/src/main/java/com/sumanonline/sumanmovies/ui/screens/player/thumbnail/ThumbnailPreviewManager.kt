package com.sumanonline.sumanmovies.ui.screens.player.thumbnail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private var clearJob: Job? = null
    private var prewarmJob: Job? = null
    private var lastRequestedTimestampMs: Long = -1L
    private var prewarmedStreamKey: String? = null

    fun requestPreview(
        timestampMs: Long,
        mediaId: String,
        streamUrl: String?,
        headers: Map<String, String>,
        posterUrl: String? = null
    ) {
        clearJob?.cancel()
        val targetMs = timestampMs.coerceAtLeast(0L)
        val isForward = targetMs >= lastRequestedTimestampMs
        lastRequestedTimestampMs = targetMs

        val bucketIndex = targetMs / 10000L
        val streamHash = streamUrl?.hashCode() ?: 0
        val exactKey = "${mediaId}_${streamHash}_${bucketIndex}"

        val nearestBitmap = repository.cacheManager?.getNearestBitmap(mediaId, streamHash, bucketIndex, maxBucketDelta = 6)
        val exactCached = repository.cacheManager?.getBitmap(exactKey)

        if (exactCached != null) {
            _previewFrame.value = ThumbnailFrame(
                timestampMs = targetMs,
                bitmap = exactCached,
                isPlaceholder = false
            )
            triggerDirectionalPrefetch(mediaId, streamUrl, headers, bucketIndex, isForward, posterUrl)
            return
        }

        if (nearestBitmap != null) {
            _previewFrame.value = ThumbnailFrame(
                timestampMs = targetMs,
                bitmap = nearestBitmap,
                isPlaceholder = false
            )
        } else {
            val currentBitmap = _previewFrame.value?.bitmap
            _previewFrame.value = ThumbnailFrame(
                timestampMs = targetMs,
                bitmap = currentBitmap,
                isPlaceholder = currentBitmap == null,
                placeholderUrl = posterUrl
            )
        }

        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.IO) {
            delay(30L)
            val frame = repository.getThumbnail(
                mediaId = mediaId,
                streamUrl = streamUrl,
                headers = headers,
                timestampMs = targetMs,
                posterUrl = posterUrl
            )
            if (frame.bitmap != null) {
                _previewFrame.value = frame
            }
            triggerDirectionalPrefetch(mediaId, streamUrl, headers, bucketIndex, isForward, posterUrl)
        }
    }

    private fun triggerDirectionalPrefetch(
        mediaId: String,
        streamUrl: String?,
        headers: Map<String, String>,
        bucketIndex: Long,
        isForward: Boolean,
        posterUrl: String?
    ) {
        if (streamUrl.isNullOrBlank()) return
        scope.launch(Dispatchers.IO) {
            val deltas = if (isForward) listOf(1L, 2L, 3L, -1L) else listOf(-1L, -2L, 1L)
            for (d in deltas) {
                val nextBucketMs = (bucketIndex + d).coerceAtLeast(0L) * 10000L
                repository.getThumbnail(mediaId, streamUrl, headers, nextBucketMs, posterUrl)
            }
        }
    }

    fun startPrewarming(
        mediaId: String,
        streamUrl: String?,
        headers: Map<String, String>,
        currentPosMs: Long,
        durationMs: Long,
        posterUrl: String? = null
    ) {
        if (streamUrl.isNullOrBlank() || durationMs <= 0L) return
        val streamKey = "${mediaId}_${streamUrl.hashCode()}"
        if (streamKey == prewarmedStreamKey && prewarmJob?.isActive == true) return
        prewarmedStreamKey = streamKey

        prewarmJob?.cancel()
        prewarmJob = scope.launch(Dispatchers.IO) {
            val offsetsAroundPlayhead = listOf(0L, 15000L, -15000L, 30000L, -30000L, 60000L, 120000L, 300000L)
            for (offset in offsetsAroundPlayhead) {
                val target = (currentPosMs + offset).coerceIn(0L, durationMs)
                repository.getThumbnail(mediaId, streamUrl, headers, target, posterUrl)
                delay(30L)
            }

            val stepMs = 45000L
            var timeMs = 0L
            while (timeMs <= durationMs) {
                repository.getThumbnail(mediaId, streamUrl, headers, timeMs, posterUrl)
                timeMs += stepMs
                delay(40L)
            }
        }
    }

    fun clearPreview(immediate: Boolean = true) {
        activeJob?.cancel()
        activeJob = null
        if (immediate) {
            clearJob?.cancel()
            lastRequestedTimestampMs = -1L
            _previewFrame.value = null
        } else {
            clearJob?.cancel()
            clearJob = scope.launch {
                delay(400L)
                lastRequestedTimestampMs = -1L
                _previewFrame.value = null
            }
        }
    }
}