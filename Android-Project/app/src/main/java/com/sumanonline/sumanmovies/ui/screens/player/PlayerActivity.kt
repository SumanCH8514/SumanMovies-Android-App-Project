package com.sumanonline.sumanmovies.ui.screens.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sumanonline.sumanmovies.SumanMoviesApp
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.ui.screens.customization.PlayerUiStyle
import com.sumanonline.sumanmovies.ui.theme.AppAccent
import com.sumanonline.sumanmovies.ui.theme.SumanMoviesTheme

class PlayerActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        volumeControlStream = AudioManager.STREAM_MUSIC
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val mediaId = intent.getStringExtra("EXTRA_MEDIA_ID") ?: ""
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Video Player"
        val mediaTypeStr = intent.getStringExtra("EXTRA_MEDIA_TYPE") ?: "MOVIE"
        val posterUrl = intent.getStringExtra("EXTRA_POSTER_URL")
        val backdropUrl = intent.getStringExtra("EXTRA_BACKDROP_URL")
        val releaseYear = intent.getStringExtra("EXTRA_RELEASE_YEAR")
        val streamUrl = intent.getStringExtra("EXTRA_STREAM_URL")
        val streamFormat = intent.getStringExtra("EXTRA_STREAM_FORMAT")
        val streamQuality = intent.getStringExtra("EXTRA_STREAM_QUALITY")
        val streamProvider = intent.getStringExtra("EXTRA_STREAM_PROVIDER")
        val streamDirect = intent.getBooleanExtra("EXTRA_STREAM_DIRECT", false)
        val streamIsLocker = intent.getBooleanExtra("EXTRA_STREAM_IS_LOCKER", false)
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
            when (mediaTypeStr.uppercase()) {
                "TV", "SERIES", "SHOW" -> MediaType.SERIES
                "ANIME" -> MediaType.ANIME
                "DRAMA" -> MediaType.DRAMA
                else -> MediaType.valueOf(mediaTypeStr.uppercase())
            }
        } catch (_: Exception) {
            MediaType.MOVIE
        }

        val season = intent.getIntExtra("EXTRA_SEASON", 1)
        val episode = intent.getIntExtra("EXTRA_EPISODE", 1)
        val episodeTitle = intent.getStringExtra("EXTRA_EPISODE_TITLE")

        viewModel.initPlayback(
            mediaId = mediaId,
            title = title,
            mediaType = mediaType,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseYear = releaseYear,
            initialStreamUrl = streamUrl,
            initialFormat = streamFormat,
            initialQuality = streamQuality,
            initialProvider = streamProvider,
            initialDirect = streamDirect,
            initialIsLocker = streamIsLocker,
            initialHeaders = streamHeaders,
            serverName = serverName,
            serverDisplayName = serverDisplayName,
            initialStreams = allStreams,
            initialSubtitles = allSubtitles,
            seasonNumber = season,
            episodeNumber = episode,
            initialEpisodeTitle = episodeTitle
        )

        setContent {
            val prefs = (application as? SumanMoviesApp)?.preferencesManager
            val applyPalette = prefs?.applyPaletteToPlayer?.collectAsState()?.value ?: false
            val savedAccent = prefs?.selectedAccent?.collectAsState()?.value ?: AppAccent.SUNSET_GOLD
            val accent = if (applyPalette) savedAccent else AppAccent.SUNSET_GOLD
            val playerUiStyle = prefs?.playerUiStyle?.collectAsState()?.value ?: PlayerUiStyle.MODERN

            SumanMoviesTheme(accent = accent) {
                PlayerScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    playerUiStyle = playerUiStyle,
                    onEnterPip = { enterPipMode() },
                    onToggleFullscreen = { toggleFullscreen() },
                    onShare = { mediaTitle, id -> shareContent(mediaTitle, id) }
                )
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    private fun toggleFullscreen() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun shareContent(title: String, mediaId: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Watch \"$title\" on SumanMovies: https://sumanmovies.app/watch/$mediaId")
            }
            startActivity(Intent.createChooser(shareIntent, "Share $title"))
        } catch (_: Exception) {}
    }

    private var wasPlayingBeforeStop: Boolean = false

    override fun onStart() {
        super.onStart()
        if (wasPlayingBeforeStop) {
            wasPlayingBeforeStop = false
            viewModel.playerManager.play()
        }
    }

    override fun onStop() {
        super.onStop()
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (!inPip && !isChangingConfigurations) {
            wasPlayingBeforeStop = viewModel.playerManager.playbackState.value.isPlaying
            viewModel.playerManager.pause()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setControlsVisibility(!isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.playerManager.playbackState.value.isPlaying) {
            enterPipMode()
        }
    }
}
