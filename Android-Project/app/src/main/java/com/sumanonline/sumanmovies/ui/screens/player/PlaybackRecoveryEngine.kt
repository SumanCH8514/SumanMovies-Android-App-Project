package com.sumanonline.sumanmovies.ui.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.random.Random

sealed class RecoveryStatus {
    object Idle : RecoveryStatus()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int, val message: String) : RecoveryStatus()
    data class Failed(val message: String, val isPermanent: Boolean) : RecoveryStatus()
}

@OptIn(UnstableApi::class)
class PlaybackRecoveryEngine(
    private val scope: CoroutineScope,
    private val maxRetryAttempts: Int = 3
) {
    private val _status = MutableStateFlow<RecoveryStatus>(RecoveryStatus.Idle)
    val status: StateFlow<RecoveryStatus> = _status.asStateFlow()

    private var currentAttempt = 0
    private var retryJob: Job? = null
    private var lastSavedPositionMs: Long = 0L
    private var lastPlayWhenReady: Boolean = true

    fun isRetryable(error: PlaybackException): Boolean {
        val errorCode = error.errorCode
        val cause = error.cause

        val rawMsg = (error.message ?: "").lowercase()
        val causeMsg = (cause?.message ?: "").lowercase()
        val combined = "$rawMsg $causeMsg"

        if (combined.contains("mediacodecaudiorenderer") ||
            combined.contains("mediacodecvideorenderer") ||
            combined.contains("decoderinitializationexception") ||
            combined.contains("unrecognizedinputformatexception") ||
            combined.contains("no_unsupported_type") ||
            combined.contains("no_exceeds_capabilities")
        ) {
            return false
        }

        if (cause is HttpDataSource.InvalidResponseCodeException) {
            val responseCode = cause.responseCode
            if (responseCode == 404 || responseCode == 401 || responseCode == 403 || responseCode == 410) {
                return false
            }
            if (responseCode == 429 || (responseCode in 500..599)) {
                return true
            }
        }

        if (cause is SocketTimeoutException ||
            cause is ConnectException ||
            cause is UnknownHostException ||
            cause is EOFException ||
            cause is ProtocolException ||
            cause is SSLException ||
            cause is HttpDataSource.HttpDataSourceException
        ) {
            return true
        }

        return when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
            PlaybackException.ERROR_CODE_TIMEOUT -> true
            else -> cause is IOException
        }
    }

    fun calculateDelayMs(attempt: Int): Long {
        val baseDelay = when (attempt) {
            1 -> 1000L
            2 -> 2000L
            else -> 4000L
        }
        val jitter = Random.nextLong(0, 500)
        return baseDelay + jitter
    }

    fun scheduleRetry(
        error: PlaybackException,
        currentPositionMs: Long,
        playWhenReady: Boolean,
        onExecuteRetry: (savedPositionMs: Long, playWhenReady: Boolean) -> Unit,
        onExhausted: (reason: String) -> Unit
    ) {
        if (!isRetryable(error)) {
            val msg = error.localizedMessage ?: "Non-retryable playback error"
            _status.value = RecoveryStatus.Failed(msg, isPermanent = true)
            onExhausted(msg)
            return
        }

        if (currentAttempt >= maxRetryAttempts) {
            val finalMsg = "Unable to reconnect after $maxRetryAttempts attempts. Please try again or switch stream."
            _status.value = RecoveryStatus.Failed(finalMsg, isPermanent = false)
            onExhausted(finalMsg)
            return
        }

        currentAttempt++
        lastSavedPositionMs = currentPositionMs.coerceAtLeast(0L)
        lastPlayWhenReady = playWhenReady

        val delayMs = calculateDelayMs(currentAttempt)
        val attemptMsg = "Reconnecting (attempt $currentAttempt of $maxRetryAttempts)..."
        _status.value = RecoveryStatus.Reconnecting(currentAttempt, maxRetryAttempts, attemptMsg)

        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            onExecuteRetry(lastSavedPositionMs, lastPlayWhenReady)
        }
    }

    fun markSuccess() {
        currentAttempt = 0
        retryJob?.cancel()
        retryJob = null
        _status.value = RecoveryStatus.Idle
    }

    fun cancel() {
        currentAttempt = 0
        retryJob?.cancel()
        retryJob = null
        _status.value = RecoveryStatus.Idle
    }
}
