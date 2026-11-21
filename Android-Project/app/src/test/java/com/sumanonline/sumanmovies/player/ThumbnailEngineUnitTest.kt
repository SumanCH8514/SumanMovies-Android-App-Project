package com.sumanonline.sumanmovies.player

import com.sumanonline.sumanmovies.ui.screens.player.thumbnail.ThumbnailFrame
import com.sumanonline.sumanmovies.ui.screens.player.thumbnail.ThumbnailPreviewManager
import com.sumanonline.sumanmovies.ui.screens.player.thumbnail.ThumbnailRepository
import com.sumanonline.sumanmovies.ui.screens.player.thumbnail.ThumbnailSpriteCue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailEngineUnitTest {

    @Test
    fun testWebVttParsingWithSpriteCoordinates() {
        val sampleVtt = """
            WEBVTT

            00:00:00.000 --> 00:00:10.000
            sprites_1.jpg#xywh=0,0,160,90

            00:00:10.000 --> 00:00:20.000
            sprites_1.jpg#xywh=160,0,160,90

            00:01:30.000 --> 00:01:40.000
            sprites_2.jpg#xywh=320,180,160,90
        """.trimIndent()

        val baseUrl = "https://cdn.example.com/hls/master.m3u8"
        val repository = ThumbnailRepository(null)
        val cues = repository.parseWebVtt(sampleVtt, baseUrl)

        assertEquals(3, cues.size)

        val cue1 = cues[0]
        assertEquals(0L, cue1.startMs)
        assertEquals(10000L, cue1.endMs)
        assertEquals("https://cdn.example.com/hls/sprites_1.jpg", cue1.imageUrl)
        assertEquals(0, cue1.x)
        assertEquals(0, cue1.y)
        assertEquals(160, cue1.width)
        assertEquals(90, cue1.height)
        assertTrue(cue1.contains(5000L))
        assertFalse(cue1.contains(10000L))

        val cue2 = cues[1]
        assertEquals(10000L, cue2.startMs)
        assertEquals(20000L, cue2.endMs)
        assertEquals(160, cue2.x)
        assertEquals(0, cue2.y)
        assertTrue(cue2.contains(15000L))

        val cue3 = cues[2]
        assertEquals(90000L, cue3.startMs)
        assertEquals(100000L, cue3.endMs)
        assertEquals("https://cdn.example.com/hls/sprites_2.jpg", cue3.imageUrl)
        assertEquals(320, cue3.x)
        assertEquals(180, cue3.y)
    }

    @Test
    fun testThumbnailSpriteCueIntervalMatching() {
        val cue = ThumbnailSpriteCue(
            startMs = 30000L,
            endMs = 40000L,
            imageUrl = "https://cdn.example.com/thumb.jpg",
            x = 0,
            y = 0,
            width = 160,
            height = 90
        )

        assertFalse(cue.contains(29999L))
        assertTrue(cue.contains(30000L))
        assertTrue(cue.contains(35000L))
        assertTrue(cue.contains(39999L))
        assertFalse(cue.contains(40000L))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testThumbnailPreviewManagerRequestAndClear() = runTest {
        val repository = ThumbnailRepository(null)
        val manager = ThumbnailPreviewManager(this, repository)

        assertNull(manager.previewFrame.value)

        manager.requestPreview(
            timestampMs = 25000L,
            mediaId = "test_media",
            streamUrl = null,
            headers = emptyMap(),
            posterUrl = "https://image.tmdb.org/t/p/w500/sample.jpg"
        )

        advanceTimeBy(60L)
        val frame = manager.previewFrame.value
        assertNotNull(frame)
        assertEquals(25000L, frame?.timestampMs)
        assertTrue(frame?.isPlaceholder == true)
        assertEquals("https://image.tmdb.org/t/p/w500/sample.jpg", frame?.placeholderUrl)

        manager.clearPreview()
        assertNull(manager.previewFrame.value)
    }

    @Test
    fun testHlsSegmentEntryIntervalMatching() {
        val segment = com.sumanonline.sumanmovies.ui.screens.player.thumbnail.HlsSegmentEntry(
            startMs = 12000L,
            endMs = 18000L,
            segmentUrl = "https://cdn.example.com/hls/segment_2.ts"
        )

        assertFalse(segment.contains(11999L))
        assertTrue(segment.contains(12000L))
        assertTrue(segment.contains(15000L))
        assertTrue(segment.contains(17999L))
        assertFalse(segment.contains(18000L))
    }

    @Test
    fun testNearestBucketCalculation() {
        val targetMs = 32440L
        val bucketIndex = targetMs / 10000L
        assertEquals(3L, bucketIndex)

        val deltas = listOf(1L, 2L, 3L)
        val forwardBuckets = deltas.map { bucketIndex + it }
        assertEquals(listOf(4L, 5L, 6L), forwardBuckets)
    }
}
