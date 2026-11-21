package com.sumanonline.sumanmovies.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Catalog : Screen("catalog")
    object Activity : Screen("activity")
    object Library : Screen("library")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Customization : Screen("customization")
    object Play4U : Screen("play4u")
    object MovieBox : Screen("moviebox")
    object LiveTv : Screen("livetv")
    object StreamingProviders : Screen("streaming_providers")
    object KMDL : Screen("kmdl")
    object MoviesLeech : Screen("moviesleech")
    object MoviesMod : Screen("moviesmod")
    object Details : Screen("details/{mediaId}/{mediaType}") {
        fun createRoute(mediaId: String, mediaType: String) =
            "details/${java.net.URLEncoder.encode(mediaId, "UTF-8")}/$mediaType"
    }
    object SeasonsEpisodes : Screen("seasons_episodes/{mediaId}/{title}") {
        fun createRoute(mediaId: String, title: String) = "seasons_episodes/$mediaId/${java.net.URLEncoder.encode(title, "UTF-8")}"
    }
    object CategoryMedia : Screen("category/{categoryKey}/{title}") {
        fun createRoute(categoryKey: String, title: String) = "category/$categoryKey/${java.net.URLEncoder.encode(title, "UTF-8")}"
    }
}
