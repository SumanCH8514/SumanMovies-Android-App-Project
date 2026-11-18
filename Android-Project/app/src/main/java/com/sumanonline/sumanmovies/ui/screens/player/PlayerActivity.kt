package com.sumanonline.sumanmovies.ui.screens.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.ui.theme.SumanMoviesTheme

class PlayerActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val mediaId = intent.getStringExtra("EXTRA_MEDIA_ID") ?: ""
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Video Player"
        val mediaTypeStr = intent.getStringExtra("EXTRA_MEDIA_TYPE") ?: "MOVIE"
        val posterUrl = intent.getStringExtra("EXTRA_POSTER_URL")
        val streamUrl = intent.getStringExtra("EXTRA_STREAM_URL")
        val streamFormat = intent.getStringExtra("EXTRA_STREAM_FORMAT")
        val streamQuality = intent.getStringExtra("EXTRA_STREAM_QUALITY")
        val streamProvider = intent.getStringExtra("EXTRA_STREAM_PROVIDER")
        val streamDirect = intent.getBooleanExtra("EXTRA_STREAM_DIRECT", false)
        val serverName = intent.getStringExtra("EXTRA_SERVER_NAME")
        val serverDisplayName = intent.getStringExtra("EXTRA_SERVER_DISPLAY_NAME")

        val rawHeaders = intent.getSerializableExtra("EXTRA_STREAM_HEADERS") as? HashMap<*, *>
        val streamHeaders = rawHeaders?.mapNotNull { (k, v) ->
            if (k is String && v is String) k to v else null
        }?.toMap() ?: emptyMap()

        val allStreamsJson = intent.getStringExtra("EXTRA_ALL_STREAMS_JSON")
        val allStreams: List<com.sumanonline.sumanmovies.data.models.StreamSource> = if (!allStreamsJson.isNullOrBlank()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<com.sumanonline.sumanmovies.data.models.StreamSource>>() {}.type
                com.google.gson.Gson().fromJson(allStreamsJson, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val allSubtitlesJson = intent.getStringExtra("EXTRA_ALL_SUBTITLES_JSON")
        val allSubtitles: List<com.sumanonline.sumanmovies.data.models.SubtitleTrack> = if (!allSubtitlesJson.isNullOrBlank()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<com.sumanonline.sumanmovies.data.models.SubtitleTrack>>() {}.type
                com.google.gson.Gson().fromJson(allSubtitlesJson, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val mediaType = try {
            MediaType.valueOf(mediaTypeStr.uppercase())
        } catch (_: Exception) {
            MediaType.MOVIE
        }

        val season = intent.getIntExtra("EXTRA_SEASON", 1)
        val episode = intent.getIntExtra("EXTRA_EPISODE", 1)

        viewModel.initPlayback(
            mediaId = mediaId,
            title = title,
            mediaType = mediaType,
            posterUrl = posterUrl,
            initialStreamUrl = streamUrl,
            initialFormat = streamFormat,
            initialQuality = streamQuality,
            initialProvider = streamProvider,
            initialDirect = streamDirect,
            initialHeaders = streamHeaders,
            serverName = serverName,
            serverDisplayName = serverDisplayName,
            initialStreams = allStreams,
            initialSubtitles = allSubtitles,
            seasonNumber = season,
            episodeNumber = episode
        )

        setContent {
            SumanMoviesTheme {
                PlayerScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
