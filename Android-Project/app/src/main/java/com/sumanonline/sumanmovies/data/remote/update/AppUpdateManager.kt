package com.sumanonline.sumanmovies.data.remote.update

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val isError: Boolean = false,
    val errorMessage: String? = null
)

class AppUpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val repoOwner: String = "SumanCH8514",
    private val repoName: String = "SumanMovies-Android-App-Project"
) {
    suspend fun checkForUpdates(currentVersion: String = "1.0.0"): AppUpdateInfo = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val fallbackWebUrl = "https://github.com/$repoOwner/$repoName/releases"

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "SumanMovies-Android-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()

            if (response.code == 404) {
                return@withContext AppUpdateInfo(
                    hasUpdate = false,
                    currentVersion = currentVersion,
                    latestVersion = currentVersion,
                    releaseTitle = "SumanMovies v$currentVersion",
                    releaseNotes = "You have the latest official version installed."
                )
            }

            if (!response.isSuccessful) {
                return@withContext AppUpdateInfo(
                    hasUpdate = false,
                    currentVersion = currentVersion,
                    latestVersion = currentVersion,
                    isError = true,
                    errorMessage = "Server returned code ${response.code}"
                )
            }

            val body = response.body?.string() ?: return@withContext AppUpdateInfo(
                hasUpdate = false,
                currentVersion = currentVersion,
                latestVersion = currentVersion
            )

            val json = JsonParser.parseString(body).asJsonObject
            val rawTag = json.get("tag_name")?.asString ?: currentVersion
            val cleanLatest = rawTag.trim().removePrefix("v").removePrefix("V")
            val releaseTitle = json.get("name")?.asString ?: "SumanMovies v$cleanLatest"
            val releaseNotes = json.get("body")?.asString ?: "Performance improvements and new streaming features."
            val htmlUrl = json.get("html_url")?.asString ?: fallbackWebUrl

            var downloadUrl = htmlUrl
            val assets = json.getAsJsonArray("assets")
            if (assets != null) {
                for (elem in assets) {
                    val asset = elem.asJsonObject
                    val name = asset.get("name")?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.get("browser_download_url")?.asString ?: htmlUrl
                        break
                    }
                }
            }

            val hasUpdate = isNewerVersion(cleanLatest, currentVersion)

            AppUpdateInfo(
                hasUpdate = hasUpdate,
                currentVersion = currentVersion,
                latestVersion = cleanLatest,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl
            )
        } catch (e: Exception) {
            AppUpdateInfo(
                hasUpdate = false,
                currentVersion = currentVersion,
                latestVersion = currentVersion,
                isError = true,
                errorMessage = e.localizedMessage ?: "Network connection failed"
            )
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(Regex("[^0-9]")).filter { it.isNotBlank() }.map { it.toInt() }
            val currentParts = current.split(Regex("[^0-9]")).filter { it.isNotBlank() }.map { it.toInt() }
            val maxLen = maxOf(latestParts.size, currentParts.size)

            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
