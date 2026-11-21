package com.sumanonline.sumanmovies.data.remote.kmdl

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class KMDLMobileClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()
    private val baseUrl = "https://kmdl.eu.org"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val seasonVideosCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<StreamSource>>>()
    private val seasonSubtitlesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<SubtitleTrack>>>()
    private val cacheTtlMs = 30 * 60 * 1000L

    val drives = listOf(
        Pair(1, "Movies"),
        Pair(0, "Drama"),
        Pair(2, "Ongoing"),
        Pair(4, "Server 2"),
        Pair(5, "Server 3")
    )

    suspend fun getCatalog(driveIndex: Int = 1, page: Int = 1): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$baseUrl/$driveIndex:/"
            val payload = JsonObject().apply {
                addProperty("password", "")
                addProperty("page_token", "")
                addProperty("page_index", page - 1)
            }
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", mobileUserAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Pair(emptyList(), 0)
                val body = response.body?.string() ?: return@withContext Pair(emptyList(), 0)
                val json = gson.fromJson(body, JsonObject::class.java)
                val files = json.getAsJsonObject("data")?.getAsJsonArray("files") ?: return@withContext Pair(emptyList(), 0)

                val items = mutableListOf<MediaItem>()
                for (elem in files) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val item = parseFileObj(obj, driveIndex)
                    if (item != null) {
                        items.add(item)
                    }
                }
                return@withContext Pair(items, items.size)
            }
        } catch (_: Exception) {
            Pair(emptyList(), 0)
        }
    }

    suspend fun search(query: String, driveIndex: Int? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val primaryDrive = driveIndex ?: 0
        var rawItems = searchDrive(primaryDrive, trimmed)
        if (rawItems.isEmpty() && primaryDrive != 0) {
            rawItems = searchDrive(0, trimmed)
        }
        if (rawItems.isEmpty() && primaryDrive != 2) {
            rawItems = searchDrive(2, trimmed)
        }
        if (rawItems.isEmpty() && primaryDrive != 1) {
            rawItems = searchDrive(1, trimmed)
        }
        if (rawItems.isEmpty() && primaryDrive != 4) {
            rawItems = searchDrive(4, trimmed)
        }
        if (rawItems.isEmpty() && primaryDrive != 5) {
            rawItems = searchDrive(5, trimmed)
        }

        val hasFolders = rawItems.any { it.genres.contains("Folder") }
        val filtered = if (hasFolders) {
            rawItems.filter { it.genres.contains("Folder") }
        } else {
            rawItems
        }

        val uniqueByFileId = filtered.distinctBy { item ->
            val parts = item.id.removePrefix("kmdl:").split(":")
            if (parts.size >= 2) parts[1] else item.id
        }

        val grouped = uniqueByFileId.groupBy { "${it.title.lowercase().trim()}_${it.year?.take(4) ?: ""}" }
        grouped.values.map { group ->
            val first = group.first()
            if (group.size > 1) {
                val mirrorFileIds = group.mapNotNull {
                    val p = it.id.removePrefix("kmdl:").split(":")
                    if (p.size >= 2) p[1] else null
                }.distinct()
                val parts = first.id.removePrefix("kmdl:").split(":")
                val combinedId = if (parts.size >= 4) {
                    "kmdl:${parts[0]}:${mirrorFileIds.joinToString(",")}:${parts[2]}:${parts[3]}"
                } else {
                    first.id
                }
                val baseYear = first.year?.take(4)?.trim() ?: ""
                first.copy(
                    id = combinedId,
                    year = baseYear.ifBlank { first.year }
                )
            } else {
                val baseYear = first.year?.take(4)?.trim() ?: ""
                first.copy(year = baseYear.ifBlank { first.year })
            }
        }
    }

    private fun searchDrive(driveIndex: Int, query: String): List<MediaItem> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val endpoint = "$baseUrl/$driveIndex:search?q=$encodedQuery"
            val payload = JsonObject().apply {
                addProperty("q", query)
            }
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", mobileUserAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = gson.fromJson(body, JsonObject::class.java)
                val files = json.getAsJsonObject("data")?.getAsJsonArray("files") ?: return emptyList()

                val items = mutableListOf<MediaItem>()
                for (elem in files) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val item = parseFileObj(obj, driveIndex)
                    if (item != null) {
                        items.add(item)
                    }
                }
                items
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseFileObj(obj: JsonObject, driveIndex: Int): MediaItem? {
        val rawName = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return null
        val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val mimeType = obj.get("mimeType")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val link = obj.get("link")?.takeIf { !it.isJsonNull }?.asString ?: ""

        val isFolder = mimeType == "application/vnd.google-apps.folder"
        val isVideo = mimeType.startsWith("video/") || rawName.endsWith(".mkv", true) || rawName.endsWith(".mp4", true)
        if (!isFolder && !isVideo) return null

        val parts = rawName.split("|").map { it.trim() }
        val titleCandidate = parts[0]
            .replace(Regex("""\b(1080p|720p|480p|2160p|4k|hdr|web-dl|bluray|hevc|x265|x264|dual audio|hindi|english)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\.[a-zA-Z0-9]{3,4}$"""), "")
            .replace(Regex("""[🌐⭕💬]"""), "")
            .trim()

        val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")
        val year = if (parts.size > 1) {
            yearRegex.find(parts[1])?.value ?: yearRegex.find(rawName)?.value
        } else {
            yearRegex.find(rawName)?.value
        }

        val hasSeriesIndicators = rawName.contains("Season", true) ||
            rawName.contains("S0", true) ||
            rawName.contains("S1", true) ||
            rawName.contains("S2", true) ||
            rawName.contains("S3", true) ||
            rawName.contains("Episode", true) ||
            rawName.contains("Ep.", true) ||
            rawName.contains("Complete", true) ||
            rawName.contains("K-Drama", true) ||
            rawName.contains("Kdrama", true) ||
            Regex("""\bS\d{1,2}\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawName) ||
            Regex("""\bEP?\d{1,3}\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawName)

        val mediaType = when {
            hasSeriesIndicators -> MediaType.SERIES
            driveIndex == 1 -> MediaType.MOVIE
            driveIndex == 0 || driveIndex == 2 -> MediaType.SERIES
            isFolder && !rawName.contains("Movie", true) && !rawName.contains("Film", true) -> MediaType.SERIES
            else -> MediaType.MOVIE
        }

        val targetRef = if (isFolder) rawName else (link.ifBlank { rawName })
        val typeTag = if (isFolder) "dir" else "file"
        val effectiveId = "kmdl:$driveIndex:$id:$typeTag:${URLEncoder.encode(targetRef, "UTF-8")}"

        val genreList = mutableListOf("Cloud Drive")
        if (isFolder) genreList.add("Folder")
        if (driveIndex == 0) genreList.add("Drama")
        else if (driveIndex == 2) genreList.add("Ongoing")
        else genreList.add("Cinema")

        val rating = if (parts.size > 2) {
            parts[2].replace(Regex("[^0-9.]"), "").toFloatOrNull()
        } else null

        return MediaItem(
            id = effectiveId,
            title = titleCandidate.ifBlank { rawName },
            mediaType = mediaType,
            year = year,
            rating = rating,
            posterUrl = null,
            backdropUrl = null,
            overview = "Drive: ${drives.firstOrNull { it.first == driveIndex }?.second ?: "Cloud"} • Size: ${formatSize(obj.get("size")?.asLong ?: 0L)}",
            genres = genreList,
            provider = "kmdl"
        )
    }

    suspend fun resolveStreamAndSubtitles(
        mediaId: String,
        season: Int = 1,
        episode: Int = 1
    ): Pair<List<StreamSource>, List<SubtitleTrack>> = withContext(Dispatchers.IO) {
        try {
            val parts = mediaId.removePrefix("kmdl:").split(":")
            if (parts.size < 3) return@withContext Pair(emptyList(), emptyList())
            val driveIndex = parts[0].toIntOrNull() ?: 0
            val fileIds = parts[1].split(",").map { it.trim() }.filter { it.isNotBlank() }

            val cacheKey = "${parts[0]}:${parts[1]}:$season"
            val cachedEntry = seasonVideosCache[cacheKey]
            if (cachedEntry != null && System.currentTimeMillis() - cachedEntry.first < cacheTtlMs && cachedEntry.second.isNotEmpty()) {
                val matched = filterEpisodeVideos(cachedEntry.second, season, episode)
                if (matched.isNotEmpty()) {
                    val cachedSubs = seasonSubtitlesCache[cacheKey]?.second ?: emptyList()
                    return@withContext Pair(matched, cachedSubs)
                }
            }

            val isExplicitFile: Boolean
            val decodedTarget: String
            if (parts.size >= 4) {
                isExplicitFile = (parts[2] == "file")
                decodedTarget = java.net.URLDecoder.decode(parts[3], "UTF-8")
            } else {
                val decoded = java.net.URLDecoder.decode(parts[2], "UTF-8")
                isExplicitFile = decoded.startsWith("/download.aspx")
                decodedTarget = decoded
            }

            if (isExplicitFile && decodedTarget.startsWith("/download.aspx")) {
                val direct = if (decodedTarget.startsWith("http")) decodedTarget else "$baseUrl$decodedTarget"
                return@withContext Pair(
                    listOf(
                        StreamSource(
                            title = "KMDL Cloud Stream (Lossless MKV)",
                            url = direct,
                            quality = "1080p",
                            format = "mkv",
                            provider = "kmdl",
                            directFile = true
                        )
                    ),
                    emptyList()
                )
            }

            val collectedVideos = mutableListOf<StreamSource>()
            val collectedSubtitles = mutableListOf<SubtitleTrack>()

            fun exploreFolder(path: String, mirrorLabel: String = "", depth: Int = 0) {
                if (depth > 3) return
                try {
                    val listPayload = JsonObject().apply {
                        addProperty("password", "")
                        addProperty("page_token", "")
                        addProperty("page_index", 0)
                    }
                    val listReq = Request.Builder()
                        .url("$baseUrl$path")
                        .post(listPayload.toString().toRequestBody(jsonMediaType))
                        .header("User-Agent", mobileUserAgent)
                        .build()

                    val subfolders = mutableListOf<Pair<String, String>>()

                    client.newCall(listReq).execute().use { resp ->
                        if (!resp.isSuccessful) return
                        val body = resp.body?.string() ?: ""
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val files = json.getAsJsonObject("data")?.getAsJsonArray("files") ?: return

                        for (elem in files) {
                            if (!elem.isJsonObject) continue
                            val f = elem.asJsonObject
                            val fName = f.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val fMime = f.get("mimeType")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val fLink = f.get("link")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val fid = f.get("id")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val fSize = f.get("size")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                            val isFolder = fMime == "application/vnd.google-apps.folder"

                            if (isFolder) {
                                val lower = fName.lowercase()
                                if (!lower.contains("ost") && !lower.contains("music") && !lower.contains("soundtrack") && !lower.contains("sample")) {
                                    subfolders.add(Pair(fName, fid))
                                }
                            } else if (fLink.isNotBlank()) {
                                if (fName.endsWith(".mkv", true) || fName.endsWith(".mp4", true) || fMime.startsWith("video/")) {
                                    val q = if (fName.contains("720p", true)) "720p" else if (fName.contains("4k", true) || fName.contains("2160p", true)) "4K" else "1080p"
                                    val fmt = if (fName.endsWith(".mp4", true)) "mp4" else "mkv"
                                    val streamTitle = if (mirrorLabel.isNotBlank()) {
                                        "${fName.ifBlank { "KMDL Cloud Stream" }} ($mirrorLabel)"
                                    } else {
                                        fName.ifBlank { "KMDL Cloud Stream" }
                                    }
                                    collectedVideos.add(
                                        StreamSource(
                                            title = streamTitle,
                                            url = if (fLink.startsWith("http")) fLink else "$baseUrl$fLink",
                                            quality = q,
                                            format = fmt,
                                            provider = "kmdl",
                                            sizeFormatted = formatSize(fSize),
                                            directFile = true
                                        )
                                    )
                                } else if (fName.endsWith(".srt", true) || fName.endsWith(".vtt", true)) {
                                    val lang = if (fName.contains("eng", true)) "English" else if (fName.contains("bangla", true) || fName.contains("bengali", true)) "Bangla" else "Subtitle"
                                    collectedSubtitles.add(
                                        SubtitleTrack(
                                            id = fLink,
                                            language = lang,
                                            label = fName,
                                            url = if (fLink.startsWith("http")) fLink else "$baseUrl$fLink",
                                            format = if (fName.endsWith(".srt", true)) "srt" else "vtt"
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val targetSubfolders = if (depth == 0 && season > 0) {
                        val seasonMatched = subfolders.filter { sub ->
                            val name = sub.first.lowercase()
                            name.contains("season $season", true) ||
                            name.contains("season 0$season", true) ||
                            name.contains("s0$season", true) ||
                            name.contains("s$season", true)
                        }
                        if (seasonMatched.isNotEmpty()) seasonMatched else subfolders
                    } else {
                        subfolders
                    }

                    for (sub in targetSubfolders) {
                        val enc = URLEncoder.encode(sub.first, "UTF-8").replace("+", "%20")
                        val subPath = "${path.trimEnd('/')}/$enc/"
                        exploreFolder(subPath, mirrorLabel, depth + 1)
                    }
                } catch (_: Exception) {}
            }

            for ((mIdx, fId) in fileIds.withIndex()) {
                val mirrorLabel = if (fileIds.size > 1) "Server ${mIdx + 1}" else ""
                var folderPath = ""
                try {
                    val pathUrl = "$baseUrl/$driveIndex:id2path"
                    val pathPayload = JsonObject().apply { addProperty("id", fId) }
                    val pathReq = Request.Builder()
                        .url(pathUrl)
                        .post(pathPayload.toString().toRequestBody(jsonMediaType))
                        .header("User-Agent", mobileUserAgent)
                        .build()

                    client.newCall(pathReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            folderPath = gson.fromJson(body, JsonObject::class.java)?.get("path")?.asString ?: ""
                        }
                    }
                } catch (_: Exception) {}

                if (folderPath.isBlank() && driveIndex != 0) {
                    try {
                        val pathPayload = JsonObject().apply { addProperty("id", fId) }
                        val pathReq = Request.Builder()
                            .url("$baseUrl/0:id2path")
                            .post(pathPayload.toString().toRequestBody(jsonMediaType))
                            .header("User-Agent", mobileUserAgent)
                            .build()
                        client.newCall(pathReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                folderPath = gson.fromJson(body, JsonObject::class.java)?.get("path")?.asString ?: ""
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (folderPath.isNotBlank()) {
                    exploreFolder(folderPath, mirrorLabel)
                }

                if (collectedVideos.isNotEmpty()) {
                    break
                }
            }

            if (collectedVideos.isEmpty()) {
                val cleanTitle = decodedTarget
                    .split("|")[0]
                    .replace(Regex("""\b(1080p|720p|480p|2160p|4k|hdr|web-dl|bluray|hevc|x265|x264|dual audio|hindi|english)\b""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\.[a-zA-Z0-9]{3,4}$"""), "")
                    .replace(Regex("""[🌐⭕💬🔞]"""), "")
                    .trim()

                if (cleanTitle.isNotBlank()) {
                    val rawFiles = searchRawFiles(cleanTitle, driveIndex).ifEmpty {
                        searchRawFiles(cleanTitle, 0)
                    }

                    for (f in rawFiles) {
                        val fMime = f.get("mimeType")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val fName = f.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val fLink = f.get("link")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val fSize = f.get("size")?.takeIf { !it.isJsonNull }?.asLong ?: 0L

                        if (fLink.isNotBlank() && (fMime.startsWith("video/") || fName.endsWith(".mkv", true) || fName.endsWith(".mp4", true))) {
                            val q = if (fName.contains("720p", true)) "720p" else if (fName.contains("4k", true) || fName.contains("2160p", true)) "4K" else "1080p"
                            val fmt = if (fName.endsWith(".mp4", true)) "mp4" else "mkv"
                            collectedVideos.add(
                                StreamSource(
                                    title = fName.ifBlank { cleanTitle },
                                    url = if (fLink.startsWith("http")) fLink else "$baseUrl$fLink",
                                    quality = q,
                                    format = fmt,
                                    provider = "kmdl",
                                    sizeFormatted = formatSize(fSize),
                                    directFile = true
                                )
                            )
                        } else if (fLink.isNotBlank() && (fName.endsWith(".srt", true) || fName.endsWith(".vtt", true))) {
                            val lang = if (fName.contains("eng", true)) "English" else if (fName.contains("bangla", true) || fName.contains("bengali", true)) "Bangla" else "Subtitle"
                            collectedSubtitles.add(
                                SubtitleTrack(
                                    id = fLink,
                                    language = lang,
                                    label = fName,
                                    url = if (fLink.startsWith("http")) fLink else "$baseUrl$fLink",
                                    format = if (fName.endsWith(".srt", true)) "srt" else "vtt"
                                )
                            )
                        }
                    }
                }
            }

            if (collectedVideos.isNotEmpty()) {
                seasonVideosCache[cacheKey] = Pair(System.currentTimeMillis(), collectedVideos)
                if (collectedSubtitles.isNotEmpty()) {
                    seasonSubtitlesCache[cacheKey] = Pair(System.currentTimeMillis(), collectedSubtitles)
                }
            }

            val matchedVideos = filterEpisodeVideos(collectedVideos, season, episode)
            Pair(matchedVideos, collectedSubtitles)
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    private fun filterEpisodeVideos(videos: List<StreamSource>, season: Int, episode: Int): List<StreamSource> {
        if (episode <= 0 || videos.size <= 1) return videos
        val epPatterns = listOf(
            Regex("""(?i)\b(?:s0?${season})?e0?${episode}\b"""),
            Regex("""(?i)\b(?:episode|ep|e)\s*0?${episode}\b"""),
            Regex("""(?i)0?${episode}\.(?:mkv|mp4)"""),
            Regex("""(?i)\b${episode}\b""")
        )
        for (pat in epPatterns) {
            val m = videos.filter { pat.containsMatchIn(it.title ?: "") }
            if (m.isNotEmpty()) return m
        }
        return videos
    }

    private fun searchRawFiles(query: String, driveIndex: Int = 0): List<JsonObject> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val endpoint = "$baseUrl/$driveIndex:search?q=$encodedQuery"
            val payload = JsonObject().apply {
                addProperty("q", query)
            }
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", mobileUserAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = gson.fromJson(body, JsonObject::class.java)
                val files = json.getAsJsonObject("data")?.getAsJsonArray("files") ?: return emptyList()
                files.filter { it.isJsonObject }.map { it.asJsonObject }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun resolveStream(mediaId: String): List<StreamSource> {
        return resolveStreamAndSubtitles(mediaId).first
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Direct Stream"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0) else String.format(java.util.Locale.US, "%.0f MB", mb)
    }
}
