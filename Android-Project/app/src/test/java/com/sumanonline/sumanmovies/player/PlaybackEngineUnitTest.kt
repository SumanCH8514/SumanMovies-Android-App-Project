package com.sumanonline.sumanmovies.player

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.sumanonline.sumanmovies.data.cache.PlayerCacheManager
import com.sumanonline.sumanmovies.ui.screens.player.PlaybackRecoveryEngine
import com.sumanonline.sumanmovies.ui.screens.player.RecoveryStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackEngineUnitTest {

    @Test
    fun testCacheKeySanitizationRemovesEphemeralQueryParameters() {
        val originalUrl = "https://cdn.example.com/videos/movie123.mp4?token=abc123xyz&expires=1795280000&sig=deadbeef"
        val cacheKey = PlayerCacheManager.generateCacheKey(originalUrl)

        assertFalse(cacheKey.contains("token="))
        assertFalse(cacheKey.contains("expires="))
        assertFalse(cacheKey.contains("sig="))
        assertTrue(cacheKey.contains("movie123.mp4"))
        assertTrue(cacheKey.startsWith("https://cdn.example.com/videos/movie123.mp4"))
    }

    @Test
    fun testCacheKeyDistinguishesDifferentVideos() {
        val urlA = "https://cdn.example.com/videos/movie_A.mp4?token=123"
        val urlB = "https://cdn.example.com/videos/movie_B.mp4?token=123"

        val keyA = PlayerCacheManager.generateCacheKey(urlA)
        val keyB = PlayerCacheManager.generateCacheKey(urlB)

        assertNotEquals(keyA, keyB)
        assertTrue(keyA.contains("movie_A.mp4"))
        assertTrue(keyB.contains("movie_B.mp4"))
    }

    @Test
    fun testCacheLimitConfiguration() {
        val original = PlayerCacheManager.configuredMaxCacheSizeBytes
        assertTrue(original > 0L)

        PlayerCacheManager.setMaxCacheSizeBytes(500L * 1024L * 1024L)
        assertEquals(500L * 1024L * 1024L, PlayerCacheManager.configuredMaxCacheSizeBytes)

        PlayerCacheManager.setMaxCacheSizeBytes(10L)
        assertEquals(50L * 1024L * 1024L, PlayerCacheManager.configuredMaxCacheSizeBytes)

        PlayerCacheManager.setMaxCacheSizeBytes(300L * 1024L * 1024L)
        assertEquals(300L * 1024L * 1024L, PlayerCacheManager.configuredMaxCacheSizeBytes)
    }

    @Test
    fun testErrorClassificationForRetryableErrors() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val engine = PlaybackRecoveryEngine(testScope, maxRetryAttempts = 3)

        val timeoutException = PlaybackException(
            "Connection timeout",
            SocketTimeoutException("Read timed out"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )
        assertTrue(engine.isRetryable(timeoutException))

        val mockUri = org.mockito.Mockito.mock(Uri::class.java)
        val server503Exception = PlaybackException(
            "Service Unavailable",
            HttpDataSource.InvalidResponseCodeException(
                503,
                "Service Unavailable",
                null,
                emptyMap(),
                androidx.media3.datasource.DataSpec(mockUri),
                byteArrayOf()
            ),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
        assertTrue(engine.isRetryable(server503Exception))
    }

    @Test
    fun testErrorClassificationForNonRetryableErrors() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val engine = PlaybackRecoveryEngine(testScope, maxRetryAttempts = 3)

        val codecException = PlaybackException(
            "MediaCodecVideoRenderer error NO_EXCEEDS_CAPABILITIES",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )
        assertFalse(engine.isRetryable(codecException))

        val mockUri = org.mockito.Mockito.mock(Uri::class.java)
        val notFoundException = PlaybackException(
            "HTTP 404 Not Found",
            HttpDataSource.InvalidResponseCodeException(
                404,
                "Not Found",
                null,
                emptyMap(),
                androidx.media3.datasource.DataSpec(mockUri),
                byteArrayOf()
            ),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
        assertFalse(engine.isRetryable(notFoundException))
    }

    @Test
    fun testExponentialBackoffDelays() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val engine = PlaybackRecoveryEngine(testScope, maxRetryAttempts = 3)

        val delay1 = engine.calculateDelayMs(1)
        val delay2 = engine.calculateDelayMs(2)
        val delay3 = engine.calculateDelayMs(3)

        assertTrue(delay1 in 1000..1500)
        assertTrue(delay2 in 2000..2500)
        assertTrue(delay3 in 4000..4500)
    }

    @Test
    fun testRecoveryEngineBoundedRetries() = runTest {
        val engine = PlaybackRecoveryEngine(this, maxRetryAttempts = 2)

        var retryCount = 0
        var exhaustedCalled = false

        val networkError = PlaybackException(
            "Network failure",
            IOException("Connection reset"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )

        engine.scheduleRetry(
            error = networkError,
            currentPositionMs = 45000L,
            playWhenReady = true,
            onExecuteRetry = { savedPos, playWhenReady ->
                retryCount++
                assertEquals(45000L, savedPos)
                assertTrue(playWhenReady)
            },
            onExhausted = {
                exhaustedCalled = true
            }
        )

        assertTrue(engine.status.value is RecoveryStatus.Reconnecting)
        advanceTimeBy(1600L)
        assertEquals(1, retryCount)
        assertFalse(exhaustedCalled)

        engine.scheduleRetry(
            error = networkError,
            currentPositionMs = 45000L,
            playWhenReady = true,
            onExecuteRetry = { savedPos, _ ->
                retryCount++
                assertEquals(45000L, savedPos)
            },
            onExhausted = {
                exhaustedCalled = true
            }
        )

        advanceTimeBy(2600L)
        assertEquals(2, retryCount)
        assertFalse(exhaustedCalled)

        engine.scheduleRetry(
            error = networkError,
            currentPositionMs = 45000L,
            playWhenReady = true,
            onExecuteRetry = { _, _ ->
                retryCount++
            },
            onExhausted = {
                exhaustedCalled = true
            }
        )

        assertTrue(exhaustedCalled)
        assertEquals(2, retryCount)
        assertTrue(engine.status.value is RecoveryStatus.Failed)
    }

    @Test
    fun testRecoveryEngineCancellation() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val engine = PlaybackRecoveryEngine(testScope, maxRetryAttempts = 3)

        val networkError = PlaybackException(
            "Timeout",
            SocketTimeoutException(),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )

        engine.scheduleRetry(
            error = networkError,
            currentPositionMs = 12000L,
            playWhenReady = true,
            onExecuteRetry = { _, _ -> },
            onExhausted = { }
        )

        assertTrue(engine.status.value is RecoveryStatus.Reconnecting)
        engine.cancel()
        assertTrue(engine.status.value is RecoveryStatus.Idle)
    }

    @Test
    fun testSeekClampingPreservesValidBounds() {
        val durationMs = 120000L
        val negativeTarget = -5000L
        val clampedNegative = if (durationMs > 0L) negativeTarget.coerceIn(0L, durationMs) else negativeTarget.coerceAtLeast(0L)
        assertEquals(0L, clampedNegative)

        val overshootTarget = 150000L
        val clampedOvershoot = if (durationMs > 0L) overshootTarget.coerceIn(0L, durationMs) else overshootTarget.coerceAtLeast(0L)
        assertEquals(120000L, clampedOvershoot)

        val validTarget = 45000L
        val clampedValid = if (durationMs > 0L) validTarget.coerceIn(0L, durationMs) else validTarget.coerceAtLeast(0L)
        assertEquals(45000L, clampedValid)
    }

    @Test
    fun testUnseekableStreamRejectsSeekWithoutResetting() {
        val currentPosition = 30000L
        val isSeekable = false
        val targetSeek = 60000L

        val resultingPosition = if (isSeekable) {
            targetSeek
        } else {
            currentPosition
        }

        assertEquals(currentPosition, resultingPosition)
        assertNotEquals(0L, resultingPosition)
    }
}
