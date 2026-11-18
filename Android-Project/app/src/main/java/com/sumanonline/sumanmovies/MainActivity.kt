package com.sumanonline.sumanmovies

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.sumanonline.sumanmovies.ui.navigation.AppNavigation
import com.sumanonline.sumanmovies.ui.screens.activity.ActivityViewModel
import com.sumanonline.sumanmovies.ui.screens.catalog.CatalogViewModel
import com.sumanonline.sumanmovies.ui.screens.customization.AppearanceMode
import com.sumanonline.sumanmovies.ui.screens.customization.CustomizationViewModel
import com.sumanonline.sumanmovies.ui.screens.home.HomeViewModel
import com.sumanonline.sumanmovies.ui.screens.library.LibraryViewModel
import com.sumanonline.sumanmovies.ui.screens.profile.ProfileViewModel
import com.sumanonline.sumanmovies.ui.screens.settings.SettingsViewModel
import com.sumanonline.sumanmovies.ui.screens.splash.CinematicSplashScreen
import com.sumanonline.sumanmovies.ui.theme.SumanMoviesTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val app = application as SumanMoviesApp
        val repo = app.mediaRepository

        setContent {
            val customizationViewModel: CustomizationViewModel = viewModel()
            val customState by customizationViewModel.uiState.collectAsState()
            var showCinematicSplash by remember { mutableStateOf(true) }

            val isDark = when (customState.appearanceMode) {
                AppearanceMode.DARK -> true
                AppearanceMode.LIGHT -> false
                AppearanceMode.DEVICE -> isSystemInDarkTheme()
            }

            SumanMoviesTheme(accent = customState.selectedAccent) {
                val navController = rememberNavController()

                val homeViewModel: HomeViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return HomeViewModel(repo) as T
                        }
                    }
                )

                val catalogViewModel: CatalogViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return CatalogViewModel(repo) as T
                        }
                    }
                )

                val activityViewModel: ActivityViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ActivityViewModel(repo) as T
                        }
                    }
                )

                val libraryViewModel: LibraryViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return LibraryViewModel(repo) as T
                        }
                    }
                )

                val profileViewModel: ProfileViewModel = viewModel()

                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SettingsViewModel(repo) as T
                        }
                    }
                )

                if (showCinematicSplash) {
                    CinematicSplashScreen(onFinished = { showCinematicSplash = false })
                } else {
                    AppNavigation(
                        navController = navController,
                        repository = repo,
                        homeViewModel = homeViewModel,
                        catalogViewModel = catalogViewModel,
                        activityViewModel = activityViewModel,
                        libraryViewModel = libraryViewModel,
                        profileViewModel = profileViewModel,
                        settingsViewModel = settingsViewModel,
                        customizationViewModel = customizationViewModel
                    )
                }
            }
        }
    }
}
