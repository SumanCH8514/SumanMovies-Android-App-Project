package com.sumanonline.sumanmovies.data.remote.backend

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BackendApi {
    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("provider") provider: String? = null
    ): BackendSearchResponse

    @GET("api/v1/media/{provider}/{id}")
    suspend fun getMediaDetails(
        @Path("provider") provider: String,
        @Path(value = "id", encoded = true) id: String
    ): BackendMediaDetailsResponse

    @GET("api/v1/streams/{provider}/{id}")
    suspend fun getStreams(
        @Path("provider") provider: String,
        @Path(value = "id", encoded = true) id: String,
        @Query("season") season: Int = 1,
        @Query("episode") episode: Int = 1
    ): BackendStreamsResponse

    @GET("api/v1/tv/channels")
    suspend fun getTvChannels(
        @Query("category") category: String? = null
    ): BackendTvResponse

    @POST("api/v1/moviebox/sign")
    suspend fun signMoviebox(
        @Body request: BackendSignRequest
    ): BackendSignResponse
}
