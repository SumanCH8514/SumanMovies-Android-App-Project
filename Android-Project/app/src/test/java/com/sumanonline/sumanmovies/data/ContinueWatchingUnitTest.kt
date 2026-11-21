package com.sumanonline.sumanmovies.data

import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingUnitTest {

    @Test
    fun testContinueWatchingDeduplicationPreservesLatestPerMedia() {
        val list = listOf(
            WatchHistoryEntity(
                id = "series1:s1e3",
                mediaId = "series1",
                title = "Show 1 - S1E3",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 3,
                positionMs = 1000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 3000L
            ),
            WatchHistoryEntity(
                id = "series1:s1e2",
                mediaId = "series1",
                title = "Show 1 - S1E2",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 2,
                positionMs = 2000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 2000L
            ),
            WatchHistoryEntity(
                id = "series1:s1e1",
                mediaId = "series1",
                title = "Show 1 - S1E1",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 1,
                positionMs = 3000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 1000L
            ),
            WatchHistoryEntity(
                id = "movie1:s1e1",
                mediaId = "movie1",
                title = "Movie 1",
                mediaType = "MOVIE",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 1,
                positionMs = 4000L,
                durationMs = 7000L,
                isCompleted = false,
                lastWatchedTimestamp = 2500L
            )
        )

        val distinct = list.distinctBy { it.mediaId }

        assertEquals(2, distinct.size)
        assertEquals("series1:s1e3", distinct[0].id)
        assertEquals("movie1:s1e1", distinct[1].id)
    }

    @Test
    fun testSimulatedNonAtomicClearFailsToClearResidualEpisodes() {
        val databaseItems = mutableListOf(
            WatchHistoryEntity(
                id = "series1:s1e3",
                mediaId = "series1",
                title = "Show 1 - S1E3",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 3,
                positionMs = 1000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 3000L
            ),
            WatchHistoryEntity(
                id = "series1:s1e2",
                mediaId = "series1",
                title = "Show 1 - S1E2",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 2,
                positionMs = 2000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 2000L
            ),
            WatchHistoryEntity(
                id = "series1:s1e1",
                mediaId = "series1",
                title = "Show 1 - S1E1",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 1,
                positionMs = 3000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 1000L
            )
        )

        val visibleInContinueWatching = databaseItems.distinctBy { it.mediaId }
        assertEquals(1, visibleInContinueWatching.size)

        visibleInContinueWatching.forEach { item ->
            databaseItems.removeAll { it.id == item.id }
        }

        assertEquals(2, databaseItems.size)
        assertTrue(databaseItems.any { it.id == "series1:s1e2" })
        assertTrue(databaseItems.any { it.id == "series1:s1e1" })

        val nextVisible = databaseItems.distinctBy { it.mediaId }
        assertEquals(1, nextVisible.size)
        assertEquals("series1:s1e2", nextVisible[0].id)
    }

    @Test
    fun testAtomicClearRemovesAllInProgressRecords() {
        val databaseItems = mutableListOf(
            WatchHistoryEntity(
                id = "series1:s1e3",
                mediaId = "series1",
                title = "Show 1 - S1E3",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 3,
                positionMs = 1000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 3000L
            ),
            WatchHistoryEntity(
                id = "series1:s1e2",
                mediaId = "series1",
                title = "Show 1 - S1E2",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 2,
                positionMs = 2000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 2000L
            ),
            WatchHistoryEntity(
                id = "series1:s1e1",
                mediaId = "series1",
                title = "Show 1 - S1E1",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 1,
                positionMs = 3000L,
                durationMs = 5000L,
                isCompleted = true,
                lastWatchedTimestamp = 1000L
            ),
            WatchHistoryEntity(
                id = "movie1:s1e1",
                mediaId = "movie1",
                title = "Movie 1",
                mediaType = "MOVIE",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 1,
                positionMs = 4000L,
                durationMs = 7000L,
                isCompleted = false,
                lastWatchedTimestamp = 2500L
            )
        )

        databaseItems.removeAll { !it.isCompleted }

        assertEquals(1, databaseItems.size)
        assertEquals("series1:s1e1", databaseItems[0].id)
        assertTrue(databaseItems[0].isCompleted)

        val continueWatching = databaseItems.filter { !it.isCompleted }.distinctBy { it.mediaId }
        assertTrue(continueWatching.isEmpty())
    }

    @Test
    fun testSingleItemRemovalPurgesAllInProgressForThatMedia() {
        val databaseItems = mutableListOf(
            WatchHistoryEntity(
                id = "series1:s1e3",
                mediaId = "series1",
                title = "Show 1 - S1E3",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 3,
                positionMs = 1000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 3000L
            ),
            WatchHistoryEntity(
                id = "series1:s1e2",
                mediaId = "series1",
                title = "Show 1 - S1E2",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 2,
                positionMs = 2000L,
                durationMs = 5000L,
                isCompleted = false,
                lastWatchedTimestamp = 2000L
            ),
            WatchHistoryEntity(
                id = "series2:s1e1",
                mediaId = "series2",
                title = "Show 2 - S1E1",
                mediaType = "SERIES",
                posterUrl = null,
                seasonNumber = 1,
                episodeNumber = 1,
                positionMs = 1500L,
                durationMs = 4000L,
                isCompleted = false,
                lastWatchedTimestamp = 1500L
            )
        )

        val targetId = "series1:s1e3"
        val targetMediaId = "series1"

        databaseItems.removeAll {
            (it.mediaId == targetMediaId || it.id == targetId || it.mediaId == targetId) && !it.isCompleted
        }

        assertEquals(1, databaseItems.size)
        assertEquals("series2:s1e1", databaseItems[0].id)
        assertFalse(databaseItems.any { it.mediaId == "series1" })
    }
}