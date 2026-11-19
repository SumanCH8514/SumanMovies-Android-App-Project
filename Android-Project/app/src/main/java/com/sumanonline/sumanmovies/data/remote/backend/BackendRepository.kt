package com.sumanonline.sumanmovies.data.remote.backend

import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class BackendRepository(baseUrl: String = "https://api.moviebox.sumanonline.com/") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(BackendApi::class.java)

    suspend fun search(query: String, provider: String? = null): List<MediaItem> {
        return try {
            val resp = api.search(query, provider)
            resp.data.map { dto ->
                val type = when (dto.mediaType.lowercase()) {
                    "series", "tv" -> MediaType.SERIES
                    "anime" -> MediaType.ANIME
                    "drama" -> MediaType.DRAMA
                    else -> MediaType.MOVIE
                }
                MediaItem(
                    id = "${dto.provider}:${dto.id}",
                    title = dto.title,
                    mediaType = type,
                    year = dto.year,
                    posterUrl = dto.posterUrl,
                    provider = dto.provider
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getStreams(
        provider: String,
        id: String,
        season: Int = 1,
        episode: Int = 1
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        return try {
            val resp = api.getStreams(provider, id, season, episode)
            val streamData = resp.data ?: return Pair(emptyList(), emptyList())

            val streams = streamData.streams?.map { s ->
                StreamSource(
                    quality = s.quality,
                    format = s.format,
                    url = s.url,
                    headers = s.headers ?: emptyMap(),
                    sizeBytes = s.sizeBytes,
                    directFile = s.directFile,
                    codec = s.codec,
                    label = s.label,
                    provider = provider
                )
            } ?: emptyList()

            val subs = streamData.subtitles?.map { sub ->
                SubtitleTrack(
                    id = sub.id,
                    language = sub.language,
                    label = sub.label,
                    url = sub.url,
                    format = sub.format
                )
            } ?: emptyList()

            Pair(streams, subs)
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    suspend fun getTvChannels(category: String? = null): List<MediaItem> {
        return fetchTvChannels(category).map { it.toMediaItem() }
    }

    suspend fun fetchTvChannels(category: String? = null): List<com.sumanonline.sumanmovies.data.models.TvChannel> {
        return try {
            val resp = api.getTvChannels(category)
            resp.data.map { ch ->
                com.sumanonline.sumanmovies.data.models.TvChannel(
                    id = ch.id,
                    name = ch.name,
                    streamUrl = ch.streamUrl,
                    logoUrl = ch.logoUrl,
                    groupTitle = ch.groupTitle
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun signMoviebox(method: String, url: String, body: String? = null, token: String? = null): Map<String, String> {
        return try {
            val resp = api.signMoviebox(BackendSignRequest(method, url, body, token))
            resp.headers
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
