package com.sumanonline.sumanmovies

import com.sumanonline.sumanmovies.data.remote.moviebox.MovieBoxMobileClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieBoxTest {
    @Test
    fun testMovieBoxCatalogAndStreams() = runBlocking {
        val client = MovieBoxMobileClient()
        val (catalog, total) = client.getCatalog(type = null, genre = null, page = 1)
        assertTrue("Catalog should contain items", catalog.isNotEmpty())
        assertTrue("Total count should be positive", total > 0)

        val (streams, _) = client.resolveStreams("moviebox:2413592258760133680")
        assertTrue("Streams should be resolved", streams.isNotEmpty())
        val firstFull = streams.firstOrNull { it.sourceQualityTag != "Preview" }
        assertTrue("Full stream should be present", firstFull != null)
    }
}


