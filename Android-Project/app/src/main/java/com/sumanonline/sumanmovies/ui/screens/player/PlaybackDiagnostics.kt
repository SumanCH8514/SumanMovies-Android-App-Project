package com.sumanonline.sumanmovies.ui.screens.player

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackDiagnosticsSummary(
    val timeToReadyMs: Long = 0L,
    val timeToFirstFrameMs: Long = 0L,
    val rebufferCount: Int = 0,
    val totalRebufferDurationMs: Long = 0L,
    val retryAttempts: Int = 0,
    val decoderName: String? = null,
    val audioDecoderName: String? = null,
    val lastError: String? = null
)

@OptIn(UnstableApi::class)
class PlaybackDiagnostics : AnalyticsListener {
    private var sessionStartTimeMs: Long = 0L
    private var timeToReadyMs: Long = 0L
    private var timeToFirstFrameMs: Long = 0L

    private var isPlayingStarted: Boolean = false
    private var rebufferCount: Int = 0
    private var rebufferStartMs: Long = 0L
    private var totalRebufferDurationMs: Long = 0L
    private var retryCount: Int = 0

    private var videoDecoder: String? = null
    private var audioDecoder: String? = null
    private var lastErrorSummary: String? = null

    private val _summary = MutableStateFlow(PlaybackDiagnosticsSummary())
    val summary: StateFlow<PlaybackDiagnosticsSummary> = _summary.asStateFlow()

    fun resetSession() {
        sessionStartTimeMs = SystemClock.elapsedRealtime()
        timeToReadyMs = 0L
        timeToFirstFrameMs = 0L
        isPlayingStarted = false
        rebufferCount = 0
        rebufferStartMs = 0L
        totalRebufferDurationMs = 0L
        retryCount = 0
        videoDecoder = null
        audioDecoder = null
        lastErrorSummary = null
        updateState()
    }

    fun recordRetryAttempt() {
        retryCount++
        updateState()
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        val now = SystemClock.elapsedRealtime()
        when (state) {
            Player.STATE_READY -> {
                if (timeToReadyMs == 0L && sessionStartTimeMs > 0L) {
                    timeToReadyMs = now - sessionStartTimeMs
                }
                if (rebufferStartMs > 0L) {
                    totalRebufferDurationMs += (now - rebufferStartMs)
                    rebufferStartMs = 0L
                }
                isPlayingStarted = true
                updateState()
            }
            Player.STATE_BUFFERING -> {
                if (isPlayingStarted && rebufferStartMs == 0L) {
                    rebufferCount++
                    rebufferStartMs = now
                    updateState()
                }
            }
            Player.STATE_ENDED, Player.STATE_IDLE -> {
                if (rebufferStartMs > 0L) {
                    totalRebufferDurationMs += (now - rebufferStartMs)
                    rebufferStartMs = 0L
                    updateState()
                }
            }
        }
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        if (timeToFirstFrameMs == 0L && sessionStartTimeMs > 0L) {
            timeToFirstFrameMs = SystemClock.elapsedRealtime() - sessionStartTimeMs
            updateState()
        }
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        videoDecoder = decoderName
        updateState()
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        audioDecoder = decoderName
        updateState()
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        lastErrorSummary = error.errorCodeName
        updateState()
    }

    private fun updateState() {
        _summary.value = PlaybackDiagnosticsSummary(
            timeToReadyMs = timeToReadyMs,
            timeToFirstFrameMs = timeToFirstFrameMs,
            rebufferCount = rebufferCount,
            totalRebufferDurationMs = totalRebufferDurationMs,
            retryAttempts = retryCount,
            decoderName = videoDecoder,
            audioDecoderName = audioDecoder,
            lastError = lastErrorSummary
        )
    }
}
