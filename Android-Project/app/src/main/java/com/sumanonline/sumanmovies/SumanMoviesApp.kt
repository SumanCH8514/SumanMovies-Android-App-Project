package com.sumanonline.sumanmovies

import android.app.Application
import android.content.Context
import com.sumanonline.sumanmovies.data.download.AppDownloadManager
import com.sumanonline.sumanmovies.data.local.AppDatabase
import com.sumanonline.sumanmovies.data.local.AppPreferencesManager
import com.sumanonline.sumanmovies.data.remote.backend.BackendRepository
import com.sumanonline.sumanmovies.data.remote.fourkhdhub.FourKHdHubMobileClient
import com.sumanonline.sumanmovies.data.remote.hdhub4u.HDHub4uMobileClient
import com.sumanonline.sumanmovies.data.remote.moviebox.MovieBoxMobileClient
import com.sumanonline.sumanmovies.data.remote.play4u.Play4UMobileClient
import com.sumanonline.sumanmovies.data.remote.tmdb.TmdbRepository
import com.sumanonline.sumanmovies.data.remote.update.AppUpdateManager
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import com.sumanonline.sumanmovies.data.repository.PlaybackResolver
import com.sumanonline.sumanmovies.data.repository.ServerSelectionManager

class SumanMoviesApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: AppPreferencesManager
        private set

    lateinit var updateManager: AppUpdateManager
        private set

    lateinit var tmdbRepository: TmdbRepository
        private set

    lateinit var backendRepository: BackendRepository
        private set

    lateinit var movieBoxClient: MovieBoxMobileClient
        private set

    lateinit var fourKHdHubClient: FourKHdHubMobileClient
        private set

    lateinit var hdHub4uClient: HDHub4uMobileClient
        private set

    lateinit var play4UClient: Play4UMobileClient
        private set

    lateinit var downloadManager: AppDownloadManager
        private set

    lateinit var serverSelectionManager: ServerSelectionManager
        private set

    lateinit var playbackResolver: PlaybackResolver
        private set

    lateinit var mediaRepository: MediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        preferencesManager = AppPreferencesManager(this)
        updateManager = AppUpdateManager()
        tmdbRepository = TmdbRepository(apiKey = BuildConfig.TMDB_API_KEY)
        backendRepository = BackendRepository(baseUrl = BuildConfig.BACKEND_BASE_URL)
        movieBoxClient = MovieBoxMobileClient(backendRepository)
        fourKHdHubClient = FourKHdHubMobileClient()
        hdHub4uClient = HDHub4uMobileClient()
        play4UClient = Play4UMobileClient()
        downloadManager = AppDownloadManager(this, database.downloadDao(), preferencesManager)
        serverSelectionManager = ServerSelectionManager(this)
        val searchHistoryManager = com.sumanonline.sumanmovies.data.repository.SearchHistoryManager(this)
        playbackResolver = PlaybackResolver(backendRepository, movieBoxClient, fourKHdHubClient, hdHub4uClient, play4UClient)
        mediaRepository = MediaRepository(
            database = database,
            tmdbRepo = tmdbRepository,
            backendRepo = backendRepository,
            movieBoxClient = movieBoxClient,
            fourKHdHubClient = fourKHdHubClient,
            hdHub4uClient = hdHub4uClient,
            play4UClient = play4UClient,
            downloadManager = downloadManager,
            serverSelectionManager = serverSelectionManager,
            playbackResolver = playbackResolver,
            searchHistoryManager = searchHistoryManager
        )
    }

    companion object {
        private lateinit var instance: SumanMoviesApp

        fun get(): SumanMoviesApp = instance

        fun get(context: Context): SumanMoviesApp =
            context.applicationContext as SumanMoviesApp
    }
}
