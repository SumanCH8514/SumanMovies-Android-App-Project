package com.sumanonline.sumanmovies.data.remote.theintrodb

import android.util.LruCache
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sumanonline.sumanmovies.data.models.MediaSegment
import com.sumanonline.sumanmovies.data.models.MediaSegmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TheIntroDbClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val gson = Gson()
    private val baseUrl = "https://api.theintrodb.org/v4/media"
    private val userAgent = "SumanMovies/2.0 (Android; OTT Player)"
    private val segmentCache = LruCache<String, List<MediaSegment>>(500)

    suspend fun getSegments(
        tmdbId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        isSeries: Boolean = false
    ): List<MediaSegment> = withContext(Dispatchers.IO) {
        if (tmdbId == null && imdbId.isNullOrBlank()) {
            return@withContext emptyList()
        }

        val cacheKey = buildCacheKey(tmdbId, imdbId, season, episode, isSeries)
        synchronized(segmentCache) {
            val cached = segmentCache.get(cacheKey)
            if (cached != null) {
                return@withContext cached
            }
        }

        val queryParams = mutableListOf<String>()
        if (tmdbId != null && tmdbId > 0) {
            queryParams.add("tmdb_id=$tmdbId")
        } else if (!imdbId.isNullOrBlank()) {
            val encodedImdb = URLEncoder.encode(imdbId.trim(), "UTF-8")
            queryParams.add("imdb_id=$encodedImdb")
        }

        if (isSeries && season != null && episode != null && season > 0 && episode > 0) {
            queryParams.add("season=$season")
            queryParams.add("episode=$episode")
        }

        val fullUrl = "$baseUrl?${queryParams.joinToString("&")}"

        try {
            val request = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    synchronized(segmentCache) {
                        segmentCache.put(cacheKey, emptyList())
                    }
                    return@withContext emptyList()
                }

                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }

                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext emptyList()
                }

                val json = gson.fromJson(bodyStr, JsonObject::class.java) ?: return@withContext emptyList()
                val resultList = mutableListOf<MediaSegment>()

                resultList.addAll(extractSegments(json, "intro", MediaSegmentType.INTRO))
                resultList.addAll(extractSegments(json, "recap", MediaSegmentType.RECAP))
                resultList.addAll(extractSegments(json, "credits", MediaSegmentType.CREDITS))
                resultList.addAll(extractSegments(json, "preview", MediaSegmentType.PREVIEW))

                val sorted = resultList.sortedBy { it.startMs }
                synchronized(segmentCache) {
                    segmentCache.put(cacheKey, sorted)
                }
                return@withContext sorted
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractSegments(
        jsonObj: JsonObject,
        key: String,
        type: MediaSegmentType
    ): List<MediaSegment> {
        if (!jsonObj.has(key) || jsonObj.get(key).isJsonNull) return emptyList()
        val array = try {
            jsonObj.getAsJsonArray(key)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()

        val list = mutableListOf<MediaSegment>()
        for (element in array) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val startElem = obj.get("start_ms")
            val endElem = obj.get("end_ms")

            val startMs = if (startElem != null && !startElem.isJsonNull) {
                try {
                    startElem.asLong
                } catch (_: Exception) {
                    0L
                }
            } else {
                0L
            }

            val endMs = if (endElem != null && !endElem.isJsonNull) {
                try {
                    endElem.asLong
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            list.add(MediaSegment(type = type, startMs = startMs, endMs = endMs))
        }
        return list
    }

    private fun buildCacheKey(
        tmdbId: Int?,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        isSeries: Boolean
    ): String {
        val idPart = tmdbId?.toString() ?: imdbId?.trim().orEmpty()
        return if (isSeries) {
            "tv_${idPart}_s${season ?: 1}_e${episode ?: 1}"
        } else {
            "movie_${idPart}"
        }
    }
}
