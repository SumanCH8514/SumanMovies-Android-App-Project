package com.sumanonline.sumanmovies.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import com.sumanonline.sumanmovies.ui.components.BottomNavBar
import com.sumanonline.sumanmovies.ui.screens.activity.ActivityScreen
import com.sumanonline.sumanmovies.ui.screens.activity.ActivityViewModel
import com.sumanonline.sumanmovies.ui.screens.catalog.CatalogScreen
import com.sumanonline.sumanmovies.ui.screens.catalog.CatalogViewModel
import com.sumanonline.sumanmovies.ui.screens.category.CategoryMediaScreen
import com.sumanonline.sumanmovies.ui.screens.category.CategoryMediaViewModel
import com.sumanonline.sumanmovies.ui.screens.customization.CustomizationScreen
import com.sumanonline.sumanmovies.ui.screens.customization.CustomizationViewModel
import com.sumanonline.sumanmovies.ui.screens.details.DetailsScreen
import com.sumanonline.sumanmovies.ui.screens.details.DetailsViewModel
import com.sumanonline.sumanmovies.ui.screens.home.HomeScreen
import com.sumanonline.sumanmovies.ui.screens.home.HomeViewModel
import com.sumanonline.sumanmovies.ui.screens.library.LibraryScreen
import com.sumanonline.sumanmovies.ui.screens.library.LibraryTab
import com.sumanonline.sumanmovies.ui.screens.library.LibraryViewModel
import com.sumanonline.sumanmovies.ui.screens.moviebox.MovieBoxScreen
import com.sumanonline.sumanmovies.ui.screens.moviebox.MovieBoxViewModel
import com.sumanonline.sumanmovies.ui.screens.player.PlayerActivity
import com.sumanonline.sumanmovies.ui.screens.play4u.Play4UScreen
import com.sumanonline.sumanmovies.ui.screens.play4u.Play4UViewModel
import com.sumanonline.sumanmovies.ui.screens.profile.ProfileScreen
import com.sumanonline.sumanmovies.ui.screens.profile.ProfileViewModel
import com.sumanonline.sumanmovies.ui.screens.seasons.SeasonsEpisodesScreen
import com.sumanonline.sumanmovies.ui.screens.seasons.SeasonsEpisodesViewModel
import com.sumanonline.sumanmovies.ui.screens.settings.SettingsScreen
import com.sumanonline.sumanmovies.ui.screens.settings.SettingsViewModel
import androidx.compose.material3.MaterialTheme

@Composable
fun AppNavigation(
    navController: NavHostController,
    repository: MediaRepository,
    homeViewModel: HomeViewModel,
    catalogViewModel: CatalogViewModel,
    activityViewModel: ActivityViewModel,
    libraryViewModel: LibraryViewModel,
    profileViewModel: ProfileViewModel,
    settingsViewModel: SettingsViewModel,
    customizationViewModel: CustomizationViewModel
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current

    val libraryState by libraryViewModel.uiState.collectAsState()
    val customState by customizationViewModel.uiState.collectAsState()
    val downloadsBadgeCount = libraryState.downloads.count { it.status == "DOWNLOADING" || it.status == "COMPLETED" || it.status == "PAUSED" }

    val showBottomBar = when (currentRoute) {
        Screen.Home.route,
        Screen.Catalog.route,
        Screen.Activity.route,
        Screen.Library.route,
        Screen.Profile.route -> true
        else -> false
    }

    fun launchPlayer(
        context: Context,
        item: MediaItem,
        stream: StreamSource? = null,
        server: StreamingServer? = null,
        allStreams: List<StreamSource> = emptyList(),
        subtitles: List<com.sumanonline.sumanmovies.data.models.SubtitleTrack> = emptyList(),
        season: Int? = null,
        episode: Int? = null
    ) {
        val sMatch = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE).find(item.title)
        val resolvedSeason = season ?: sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val resolvedEpisode = episode ?: sMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1

        val isSeries = item.mediaType != MediaType.MOVIE
        val cleanBase = item.title
            .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
            .trim()
            .ifBlank { item.title }
        val resolvedTitle = if (isSeries) "$cleanBase - S${resolvedSeason}E${resolvedEpisode}" else item.title

        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("EXTRA_MEDIA_ID", item.id)
            putExtra("EXTRA_TITLE", resolvedTitle)
            putExtra("EXTRA_MEDIA_TYPE", item.mediaType.name)
            putExtra("EXTRA_POSTER_URL", item.posterUrl)
            putExtra("EXTRA_SEASON", resolvedSeason)
            putExtra("EXTRA_EPISODE", resolvedEpisode)
            if (server != null) {
                putExtra("EXTRA_SERVER_NAME", server.name)
                putExtra("EXTRA_SERVER_DISPLAY_NAME", server.displayName)
            }
            if (stream != null) {
                putExtra("EXTRA_STREAM_URL", stream.url)
                putExtra("EXTRA_STREAM_FORMAT", stream.format)
                putExtra("EXTRA_STREAM_QUALITY", stream.quality)
                putExtra("EXTRA_STREAM_PROVIDER", stream.provider)
                putExtra("EXTRA_STREAM_DIRECT", stream.directFile)
                if (stream.headers.isNotEmpty()) {
                    putExtra("EXTRA_STREAM_HEADERS", HashMap(stream.headers))
                }
            }
            if (allStreams.isNotEmpty()) {
                val json = com.google.gson.Gson().toJson(allStreams)
                putExtra("EXTRA_ALL_STREAMS_JSON", json)
            }
            if (subtitles.isNotEmpty()) {
                val json = com.google.gson.Gson().toJson(subtitles)
                putExtra("EXTRA_ALL_SUBTITLES_JSON", json)
            }
        }
        context.startActivity(intent)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    downloadsCount = downloadsBadgeCount
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        homeTabsStyle = customState.homeTabsStyle,
                        onMediaClick = { item ->
                            navController.navigate(Screen.Details.createRoute(item.id, item.mediaType.name))
                        },
                        onPlayClick = { item -> launchPlayer(context, item) },
                        onPlayContinueWatching = { entity ->
                            val cleanBase = entity.title
                                .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
                                .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
                                .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
                                .trim()
                                .ifBlank { entity.title }
                            val isSeries = entity.mediaType.equals("SERIES", ignoreCase = true) ||
                                    entity.mediaType.equals("ANIME", ignoreCase = true) ||
                                    entity.mediaType.equals("DRAMA", ignoreCase = true)
                            val formattedTitle = if (isSeries) "$cleanBase - S${entity.seasonNumber}E${entity.episodeNumber}" else entity.title
                            val media = MediaItem(
                                id = entity.mediaId,
                                title = formattedTitle,
                                posterUrl = entity.posterUrl,
                                mediaType = try {
                                    MediaType.valueOf(entity.mediaType)
                                } catch (_: Exception) {
                                    MediaType.MOVIE
                                }
                            )
                            launchPlayer(
                                context = context,
                                item = media,
                                season = entity.seasonNumber,
                                episode = entity.episodeNumber
                            )
                        },
                        onSearchClick = { navController.navigate(Screen.Catalog.route) },
                        onSeeAllCategory = { catKey, title ->
                            navController.navigate(Screen.CategoryMedia.createRoute(catKey, title))
                        },
                        onNavigateHistory = {
                            navController.navigate(Screen.Activity.route)
                        }
                    )
                }

                composable(Screen.Catalog.route) {
                    CatalogScreen(
                        viewModel = catalogViewModel,
                        onMediaClick = { item ->
                            navController.navigate(Screen.Details.createRoute(item.id, item.mediaType.name))
                        },
                        onNavigateToPlay4U = {
                            navController.navigate(Screen.Play4U.route)
                        },
                        onNavigateToMovieBox = {
                            navController.navigate(Screen.MovieBox.route)
                        },
                        onGenreClick = { genre ->
                            val key = "genre_${genre.lowercase().replace(" ", "_")}"
                            navController.navigate(Screen.CategoryMedia.createRoute(key, genre))
                        }
                    )
                }

                composable(Screen.Activity.route) {
                    ActivityScreen(
                        viewModel = activityViewModel,
                        onResumeMedia = { item, season, episode ->
                            launchPlayer(context, item, season = season, episode = episode)
                        },
                        onNavigateDetails = { item ->
                            navController.navigate(Screen.Details.createRoute(item.id, item.mediaType.name))
                        }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onMediaClick = { item ->
                            navController.navigate(Screen.Details.createRoute(item.id, item.mediaType.name))
                        },
                        onPlayDownload = { item, stream ->
                            launchPlayer(context, item, stream)
                        }
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onNavigateCustomization = { navController.navigate(Screen.Customization.route) },
                        onNavigateSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateBookmarks = {
                            libraryViewModel.selectTab(LibraryTab.FAVORITES)
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateHistory = {
                            navController.navigate(Screen.Activity.route)
                        },
                        onNavigateDownloads = {
                            libraryViewModel.selectTab(LibraryTab.DOWNLOADS)
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(Screen.Customization.route) {
                    CustomizationScreen(
                        viewModel = customizationViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Play4U.route) {
                    val play4UViewModel = Play4UViewModel(repository)
                    Play4UScreen(
                        viewModel = play4UViewModel,
                        onBack = { navController.popBackStack() },
                        onMediaClick = { item ->
                            repository.cacheItem(item)
                            navController.navigate(Screen.Details.createRoute(item.id, item.mediaType.name))
                        }
                    )
                }

                composable(Screen.MovieBox.route) {
                    val movieBoxViewModel = MovieBoxViewModel(repository)
                    MovieBoxScreen(
                        viewModel = movieBoxViewModel,
                        onBack = { navController.popBackStack() },
                        onMediaClick = { item ->
                            repository.cacheItem(item)
                            navController.navigate(Screen.Details.createRoute(item.id, item.mediaType.name))
                        }
                    )
                }

                composable(
                    route = Screen.Details.route,
                    arguments = listOf(
                        navArgument("mediaId") { type = NavType.StringType },
                        navArgument("mediaType") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val rawMediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                    val mediaId = try {
                        java.net.URLDecoder.decode(rawMediaId, "UTF-8")
                    } catch (_: Exception) {
                        rawMediaId
                    }
                    val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "MOVIE"
                    val detailsViewModel = DetailsViewModel(repository, mediaId, mediaType)

                    DetailsScreen(
                        viewModel = detailsViewModel,
                        onBack = { navController.popBackStack() },
                        onPlay = { item, stream, server, allStreams, subs ->
                            launchPlayer(context, item, stream, server, allStreams, subs)
                        },
                        onMediaClick = { item ->
                            navController.navigate(Screen.Details.createRoute(item.id, item.mediaType.name))
                        },
                        onShowAllEpisodesClick = { id, title ->
                            navController.navigate(Screen.SeasonsEpisodes.createRoute(id, title))
                        },
                        onNavigateToDownloads = {
                            libraryViewModel.selectTab(LibraryTab.DOWNLOADS)
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(
                    route = Screen.SeasonsEpisodes.route,
                    arguments = listOf(
                        navArgument("mediaId") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                    val titleRaw = backStackEntry.arguments?.getString("title") ?: "Episodes"
                    val title = java.net.URLDecoder.decode(titleRaw, "UTF-8")
                    val seasonsViewModel = SeasonsEpisodesViewModel(repository, mediaId, title)

                    SeasonsEpisodesScreen(
                        viewModel = seasonsViewModel,
                        onBack = { navController.popBackStack() },
                        onEpisodeClick = { item, ep ->
                            launchPlayer(
                                context = context,
                                item = item,
                                season = ep.seasonNumber,
                                episode = ep.episodeNumber
                            )
                        },
                        onNavigateToDownloads = {
                            libraryViewModel.selectTab(LibraryTab.DOWNLOADS)
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(
                    route = Screen.CategoryMedia.route,
                    arguments = listOf(
                        navArgument("categoryKey") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val categoryKey = backStackEntry.arguments?.getString("categoryKey") ?: "popular_movies"
                    val titleRaw = backStackEntry.arguments?.getString("title") ?: "Popular Movies"
                    val title = java.net.URLDecoder.decode(titleRaw, "UTF-8")
                    val categoryViewModel: CategoryMediaViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        viewModelStoreOwner = backStackEntry,
                        key = "category_${categoryKey}_$title",
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return CategoryMediaViewModel(repository, categoryKey, title) as T
                            }
                        }
                    )

                    CategoryMediaScreen(
                        viewModel = categoryViewModel,
                        onBack = { navController.popBackStack() },
                        onMediaClick = { item ->
                            navController.navigate(Screen.Details.createRoute(item.id, item.mediaType.name))
                        }
                    )
                }
            }
        }
    }
}
