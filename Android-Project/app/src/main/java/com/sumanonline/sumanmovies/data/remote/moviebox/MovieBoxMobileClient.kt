package com.sumanonline.sumanmovies.data.remote.moviebox

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.data.remote.backend.BackendRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MovieBoxMobileClient(
    private val backendRepository: BackendRepository? = null
) {
    private val hostPool = listOf(
        "https://api6.aoneroom.com",
        "https://api4sg.aoneroom.com",
        "https://api5.aoneroom.com",
        "https://api4.aoneroom.com",
        "https://api3.aoneroom.com",
        "https://api.inmoviebox.com"
    )

    private val client = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 407) {

                response.newBuilder()
                    .code(401)
                    .message("Unauthorized (407 Signature Invalid)")
                    .build()
            } else {
                response
            }
        }
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var activeHostIndex = 0
    private var authToken: String? = null

    private suspend fun ensureSession(): String = withContext(Dispatchers.IO) {
        authToken?.let { return@withContext it }

        val path = "/wefeed-mobile-bff/user-api/visitor-login"
        val bodyStr = "{}"
        val res = requestHosts("POST", path, bodyStr, null)
        val dataObj = res.getAsJsonObject("data") ?: res
        val token = dataObj.get("token")?.asString ?: throw IllegalStateException("Missing MovieBox token")
        authToken = token
        token
    }

    private suspend fun requestHosts(
        method: String,
        pathAndQuery: String,
        body: String?,
        token: String?
    ): JsonObject = withContext(Dispatchers.IO) {
        val startIdx = activeHostIndex
        var lastErr = ""

        for (i in hostPool.indices) {
            val idx = (startIdx + i) % hostPool.size
            val base = hostPool[idx]
            val url = "$base$pathAndQuery"

            suspend fun createRequest(customHeaders: Map<String, String>? = null): Request {
                val headers = customHeaders ?: try {
                    val localSigned = MovieBoxSigner.buildSignedHeaders(method, url, body, token)
                    if (localSigned.isNotEmpty()) localSigned
                    else backendRepository?.signMoviebox(method, url, body, token) ?: emptyMap()
                } catch (_: Exception) {
                    backendRepository?.signMoviebox(method, url, body, token) ?: emptyMap()
                }

                val reqBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }

                if (method.equals("POST", ignoreCase = true)) {
                    val bytes = (body ?: "{}").toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                    val mediaType = "application/json".toMediaType()
                    val reqBody = bytes.toRequestBody(mediaType, 0, bytes.size)
                    reqBuilder.post(reqBody)
                } else {
                    reqBuilder.get()
                }
                return reqBuilder.build()
            }

            try {
                var response = client.newCall(createRequest()).execute()

                response.headers.getDate("Date")?.time?.let { serverMs ->
                    val offset = serverMs - System.currentTimeMillis()
                    MovieBoxSigner.serverTimeOffsetMs = offset
                }

                if (response.code == 440) {
                    response.close()
                    val retrySigned = MovieBoxSigner.buildSignedHeaders(method, url, body, token)
                    response = client.newCall(createRequest(retrySigned)).execute()
                }

                response.use { resp ->
                    resp.header("x-user")?.let { xUser ->
                        try {
                            val parsed = gson.fromJson(xUser, JsonObject::class.java)
                            parsed.get("token")?.asString?.let { t -> if (t.isNotBlank()) authToken = t }
                        } catch (_: Exception) {}
                    }

                    if (resp.isSuccessful) {
                        activeHostIndex = idx
                        val text = resp.body?.string() ?: "{}"
                        android.util.Log.d("MovieBox", "Success on $url: ${text.take(100)}")
                        return@withContext gson.fromJson(text, JsonObject::class.java)
                    } else {
                        val errBody = resp.body?.string() ?: ""
                        android.util.Log.e("MovieBox", "Host $base failed with code ${resp.code}: $errBody")
                        if (resp.code == 401) {
                            authToken = null
                        }
                        lastErr = "HTTP ${resp.code} $errBody"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MovieBox", "Exception on $url", e)
                lastErr = e.message ?: "Unknown fetch error"
            }
        }
        android.util.Log.e("MovieBox", "All hosts failed. Last: $lastErr")
        throw IllegalStateException("All MovieBox hosts failed. Last error: $lastErr")
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        subjectType: Int = 0
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val token = ensureSession()
            android.util.Log.d("MovieBox", "Got session token: ${token.take(15)}...")

            val payloadV2 = JsonObject().apply {
                addProperty("keyword", query)
                addProperty("page", page)
                addProperty("perPage", 15)
                addProperty("subjectType", subjectType)
            }
            var res = try {
                requestHosts("POST", "/wefeed-mobile-bff/subject-api/search/v2", gson.toJson(payloadV2), token)
            } catch (_: Exception) {
                null
            }

            var jsonArray: JsonArray? = null
            if (res != null) {
                val dataObj = res.getAsJsonObject("data") ?: res
                val resultsArr = dataObj.getAsJsonArray("results")
                if (resultsArr != null && resultsArr.size() > 0) {
                    jsonArray = resultsArr[0].asJsonObject.getAsJsonArray("subjects")
                }
                if (jsonArray == null || jsonArray.size() == 0) {
                    jsonArray = dataObj.getAsJsonArray("items") ?: dataObj.getAsJsonArray("list")
                }
            }

            if (jsonArray == null || jsonArray.size() == 0) {
                val payloadV1 = JsonObject().apply {
                    addProperty("keyword", query)
                    addProperty("page", page)
                    addProperty("perPage", 20)
                }
                res = requestHosts("POST", "/wefeed-mobile-bff/subject-api/search", gson.toJson(payloadV1), token)
                val dataObj = res.getAsJsonObject("data") ?: res
                jsonArray = dataObj.getAsJsonArray("items")
                    ?: dataObj.getAsJsonArray("list")
                    ?: res.getAsJsonArray("items")
            }

            val list = jsonArray ?: return@withContext emptyList()
            val items = mutableListOf<MediaItem>()
            for (element in list) {
                val obj = element.asJsonObject
                val id = obj.get("subjectId")?.asString ?: obj.get("id")?.asString ?: continue
                val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: "Untitled"
                val sType = obj.get("subjectType")?.asInt ?: obj.get("stype")?.asInt ?: 1
                val isSeries = sType == 2
                val year = obj.get("releaseDate")?.asString?.take(4) ?: obj.get("year")?.asString
                val cover = obj.getAsJsonObject("cover")?.get("url")?.asString ?: obj.get("coverUrl")?.asString
                val score = obj.get("score")?.asFloat ?: obj.get("imdbRatingValue")?.asFloat

                items.add(
                    MediaItem(
                        id = "moviebox:$id",
                        title = title,
                        mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
                        year = year,
                        rating = score,
                        posterUrl = cover,
                        provider = "moviebox"
                    )
                )
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getCatalog(
        type: String? = null,
        genre: String? = null,
        page: Int = 1
    ): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        val stype = when (type?.lowercase()) {
            "movie", "movies" -> 1
            "series", "tv" -> 2
            else -> 0
        }
        val defaultKeyword = when {
            !genre.isNullOrBlank() -> genre
            stype == 1 -> "2025"
            stype == 2 -> "Season"
            else -> "Action"
        }
        val items = search(defaultKeyword, page, stype)
        Pair(items, if (items.isNotEmpty()) 150 else 0)
    }

    suspend fun getDetails(subjectId: String): MediaDetails? = withContext(Dispatchers.IO) {
        try {
            val token = ensureSession()
            val cleanId = subjectId.removePrefix("moviebox:").trim()
            val detailsRes = requestHosts("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$cleanId", null, token)
            val subject = detailsRes.getAsJsonObject("data")?.getAsJsonObject("subject")
                ?: detailsRes.getAsJsonObject("subject")
                ?: detailsRes.getAsJsonObject("data")
                ?: detailsRes

            val idStr = subject.get("subjectId")?.asString ?: subject.get("id")?.asString ?: cleanId
            val title = subject.get("title")?.asString ?: subject.get("name")?.asString ?: "Untitled"
            val sType = subject.get("subjectType")?.asInt ?: subject.get("stype")?.asInt ?: 1
            val mediaType = if (sType == 2) MediaType.SERIES else MediaType.MOVIE

            val rawYear = subject.get("releaseDate")?.asString ?: subject.get("year")?.asString ?: ""
            val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawYear)?.value
            val description = subject.get("description")?.asString ?: subject.get("intro")?.asString
            val cover = subject.getAsJsonObject("cover")?.get("url")?.asString ?: subject.get("coverUrl")?.asString
            val rating = subject.get("score")?.asFloat ?: subject.get("imdbRatingValue")?.asFloat

            val genresList = mutableListOf<String>()
            val rawGenres = subject.getAsJsonArray("genre") ?: subject.getAsJsonArray("genres")
            rawGenres?.forEach { g ->
                if (!g.isJsonNull) genresList.add(g.asString)
            }

            MediaDetails(
                id = "moviebox:$idStr",
                title = title,
                mediaType = mediaType,
                year = year,
                rating = rating,
                posterUrl = cover,
                backdropUrl = cover,
                overview = description,
                genres = genresList
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun toPlayableManifestUrl(urlStr: String): String? {
        val clean = urlStr.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return null
        if (clean.endsWith(".mpd", ignoreCase = true) || clean.endsWith(".m3u8", ignoreCase = true)) {
            return clean.trimEnd('*', '/')
        }
        val base = clean.replace(Regex("""[*\/]+$"""), "")
        if (base.isEmpty()) return null
        return when {
            base.contains("/hls/") -> "$base/index.m3u8"
            else -> "$base/index.mpd"
        }
    }

    private fun decodeCloudFrontPolicy(rawPolicy: String): String? {
        return try {
            val trimmed = rawPolicy.trim()
            val mapped = buildString {
                for (ch in trimmed) {
                    append(
                        when (ch) {
                            '-' -> '+'
                            '_' -> '='
                            '~' -> '/'
                            else -> ch
                        }
                    )
                }
            }
            val pad = (4 - (mapped.length % 4)) % 4
            val padded = if (pad in 1..3) mapped + "=".repeat(pad) else mapped
            val bytes = android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
            val jsonStr = String(bytes, Charsets.UTF_8)
            val json = gson.fromJson(jsonStr, JsonObject::class.java)
            val statements = json.getAsJsonArray("Statement")
            val resource = statements?.firstOrNull()?.asJsonObject?.get("Resource")?.asString
            if (resource != null) {
                toPlayableManifestUrl(resource)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeUrlPrefix(rawPrefix: String): String? {
        return try {
            val decoded = try {
                java.net.URLDecoder.decode(rawPrefix, "UTF-8")
            } catch (_: Exception) {
                rawPrefix
            }
            val token = decoded.substringBefore(':').trim()
            var clean = token.replace('-', '+').replace('_', '/')
            val pad = (4 - (clean.length % 4)) % 4
            if (pad in 1..3) clean += "=".repeat(pad)
            val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
            val urlStr = String(bytes, Charsets.UTF_8)
            toPlayableManifestUrl(urlStr)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveDashManifestFromPolicy(signCookie: String): String? {
        for (part in signCookie.split(';')) {
            val trimmed = part.trim()
            if (trimmed.contains("urlprefix=")) {
                val idx = trimmed.indexOf("urlprefix=")
                val prefixPart = trimmed.substring(idx + "urlprefix=".length)
                val resolved = decodeUrlPrefix(prefixPart)
                if (resolved != null) return resolved
            }
            if (trimmed.startsWith("CloudFront-Policy=")) {
                val policyRaw = trimmed.substring("CloudFront-Policy=".length)
                val resolved = decodeCloudFrontPolicy(policyRaw)
                if (resolved != null) return resolved
            }
        }
        return null
    }

    private fun resolveMasterManifest(streamUrl: String, rawCookie: String?): String {

        if (!rawCookie.isNullOrBlank()) {
            val resolved = resolveDashManifestFromPolicy(rawCookie)
            if (!resolved.isNullOrBlank() && !isDeprecationNoticeUrl(resolved)) {
                return resolved
            }
        }

        if (streamUrl.contains("/preview/")) {
            val dePreviewed = streamUrl
                .replace("/preview/index.mpd", "/index.mpd")
                .replace("/preview/index.m3u8", "/index.m3u8")
                .replace("/preview/", "/")
            if (dePreviewed.startsWith("http") && !isDeprecationNoticeUrl(dePreviewed)) {
                return dePreviewed
            }
        }

        return streamUrl
    }

    private fun isDeprecationNoticeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("1c7de0bd3393702d9191801f15f88f8d") ||
                lower.contains("9a0461bc39da389663bf3dbb17091d3f") ||
                lower.contains("b164fbfb4347792950bdfbfb563d39d9") ||
                lower.contains("/notice.mp4") ||
                (lower.contains("macdn.aoneroom.com") && lower.contains("/other/"))
    }

    private suspend fun getCaptions(subjectId: String, token: String): List<SubtitleTrack> {
        return try {
            val path = "/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=0"
            val res = requestHosts("GET", path, null, token)
            val dataObj = res.getAsJsonObject("data") ?: res
            val captions = dataObj.getAsJsonArray("extCaptions") ?: res.getAsJsonArray("extCaptions")
            val result = mutableListOf<SubtitleTrack>()
            val seen = mutableSetOf<String>()
            if (captions != null) {
                for (cap in captions) {
                    val cObj = cap.asJsonObject
                    val url = cObj.get("url")?.asString ?: continue
                    if (url.contains("aa348f2541d13ffe") || !seen.add(url)) continue
                    val name = cObj.get("lanName")?.asString ?: cObj.get("lan")?.asString ?: "Unknown"
                    result.add(
                        SubtitleTrack(
                            id = "mb_sub_${result.size}",
                            language = name,
                            label = name,
                            url = url,
                            format = if (url.endsWith(".vtt", ignoreCase = true)) "VTT" else "SRT"
                        )
                    )
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractResolution(s: String?): Int {
        if (s.isNullOrBlank()) return 1080
        val upper = s.uppercase()
        return when {
            upper.contains("2160") || upper.contains("4K") -> 2160
            upper.contains("1080") -> 1080
            upper.contains("720") -> 720
            upper.contains("480") -> 480
            upper.contains("360") -> 360
            else -> 1080
        }
    }

    suspend fun resolveStreams(
        subjectId: String,
        season: Int = 0,
        episode: Int = 0
    ): Pair<List<StreamSource>, List<SubtitleTrack>> = withContext(Dispatchers.IO) {
        try {
            val token = ensureSession()
            val cleanId = subjectId.removePrefix("moviebox:")

            val streams = mutableListOf<StreamSource>()
            val seenUrls = mutableSetOf<String>()
            val isMovie = (season == 0 && episode == 0)
            val supports4k = com.sumanonline.sumanmovies.util.DeviceCapabilities.supports4KHevc()

            val pathsToTry = mutableListOf<String>()
            if (!isMovie) {
                pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$cleanId&se=$season&ep=$episode&isVip=true&X-Play-Mode=2")
                pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$cleanId&se=$season&ep=$episode")
            } else {
                pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$cleanId&isVip=true&X-Play-Mode=2")
                pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$cleanId")
            }

            for (playInfoPath in pathsToTry) {
                try {
                    val res = requestHosts("GET", playInfoPath, null, token)
                    val data = res.getAsJsonObject("data") ?: res

                    val streamsArr = data.getAsJsonArray("streams")
                    if (streamsArr != null && streamsArr.size() > 0) {
                        for (s in streamsArr) {
                            val sObj = s.asJsonObject
                            val streamUrl = sObj.get("url")?.asString ?: continue
                            val streamDuration = sObj.get("duration")?.asLong ?: 0L

                            val is15MinCapped = if (isMovie) (streamDuration in 1..915) else (streamDuration in 1..300)

                            val rawCookie = sObj.get("signCookie")?.asString
                                ?: sObj.get("cookie")?.asString
                                ?: sObj.get("sign_cookie")?.asString

                            val effectiveUrl = resolveMasterManifest(streamUrl, rawCookie)
                            if (isDeprecationNoticeUrl(effectiveUrl) || !effectiveUrl.startsWith("http")) continue

                            val isDash = effectiveUrl.contains(".mpd") || effectiveUrl.contains("/dash/")
                            val format = if (isDash) "DASH" else "HLS"
                            val codec = sObj.get("codecName")?.asString ?: sObj.get("codec")?.asString ?: "h264"
                            val totalSizeBytes = sObj.get("size")?.asLong ?: 0L

                            val headers = mutableMapOf(
                                "Referer" to "https://sportslive.wine",
                                "User-Agent" to "com.community.oneroom/50020120 (Linux; U; Android 12; en_US; 22101316G)"
                            )
                            rawCookie?.let { c ->
                                val cleanCookie = c.split(';')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .joinToString("; ")
                                headers["Cookie"] = cleanCookie

                                if (c.contains("Edge-Cache-Cookie=")) {
                                    val edgeVal = c.substringAfter("Edge-Cache-Cookie=").substringBefore(";")
                                    headers["Edge-Cache-Cookie"] = edgeVal
                                }
                            }

                            val resStr = sObj.get("resolutions")?.asString
                                ?: data.get("displayResolutions")?.asString
                                ?: sObj.get("resolution")?.asString
                                ?: "1080,720,480"

                            val supports4k = com.sumanonline.sumanmovies.util.DeviceCapabilities.supports4KHevc()
                            val resList = resStr.split(",")
                                .mapNotNull { it.trim().filter { ch -> ch.isDigit() }.toIntOrNull() }
                                .filter { it > 0 }
                                .filter { it <= 1080 || supports4k }
                                .distinct()
                                .sortedDescending()
                                .ifEmpty { listOf(1080, 720, 480) }

                            val highestRes = resList.first().toDouble()

                            for (res in resList) {
                                val qualityName = when {
                                    res >= 2160 -> "4K Ultra HD"
                                    res >= 1080 -> "1080p Full HD"
                                    res >= 720 -> "720p HD"
                                    res >= 480 -> "480p SD"
                                    else -> "${res}p"
                                }
                                val cat = when {
                                    res >= 2160 -> "2160p / 4K"
                                    res >= 1080 -> "1080p"
                                    res >= 720 -> "720p"
                                    res >= 480 -> "480p"
                                    else -> "Other"
                                }

                                val streamKey = "$effectiveUrl#$res#$codec"
                                if (!seenUrls.add(streamKey)) continue

                                val scaledSize = if (totalSizeBytes > 0) {
                                    val scale = (res / highestRes).let { r -> Math.pow(r, 1.5) }.coerceIn(0.15, 1.0)
                                    (totalSizeBytes * scale).toLong()
                                } else 0L

                                val formattedSize = if (scaledSize > 0) {
                                    val gb = scaledSize / (1024.0 * 1024.0 * 1024.0)
                                    val mb = scaledSize / (1024.0 * 1024.0)
                                    if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
                                    else String.format(java.util.Locale.US, "%.0f MB", mb)
                                } else null

                                streams.add(
                                    StreamSource(
                                        quality = qualityName,
                                        format = format,
                                        url = effectiveUrl,
                                        headers = headers,
                                        directFile = false,
                                        codec = codec,
                                        label = if (is15MinCapped) "MovieBox $format $qualityName (Sample)" else "MovieBox $format $qualityName",
                                        provider = "moviebox",
                                        title = "MovieBox $format $qualityName",
                                        sizeBytes = if (scaledSize > 0) scaledSize else null,
                                        sizeFormatted = formattedSize,
                                        sourceQualityTag = if (is15MinCapped) "Preview" else "Full Movie Stream",
                                        category = cat,
                                        isLockerUrl = false
                                    )
                                )
                            }
                        }
                    }

                    val downloads = data.getAsJsonArray("downloads")
                    if (downloads != null) {
                        for (d in downloads) {
                            val dObj = d.asJsonObject
                            val url = dObj.get("url")?.asString ?: continue
                            if (isDeprecationNoticeUrl(url) || !seenUrls.add(url.substringBefore("?"))) continue

                            val resNum = dObj.get("resolution")?.asString?.filter { it.isDigit() }?.toIntOrNull() ?: 1080
                            if (resNum > 1080 && !supports4k) continue
                            val resName = "${resNum}p"
                            val size = dObj.get("size")?.asLong
                            val cat = when {
                                resNum >= 2160 -> "2160p / 4K"
                                resNum >= 1080 -> "1080p"
                                resNum >= 720 -> "720p"
                                resNum >= 480 -> "480p"
                                else -> "Other"
                            }
                            val formattedSize = size?.let { bytes ->
                                if (bytes <= 0) null
                                else {
                                    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                                    val mb = bytes / (1024.0 * 1024.0)
                                    if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
                                    else String.format(java.util.Locale.US, "%.0f MB", mb)
                                }
                            }

                            streams.add(
                                StreamSource(
                                    quality = "$resName Download",
                                    format = "MP4",
                                    url = url,
                                    headers = mapOf(
                                        "User-Agent" to "com.community.oneroom/50020120 (Linux; U; Android 12; en_US; 22101316G)"
                                    ),
                                    sizeBytes = size,
                                    directFile = true,
                                    codec = "h264",
                                    label = "MovieBox Direct Download $resName",
                                    provider = "moviebox",
                                    title = "MovieBox Direct Download $resName",
                                    sizeFormatted = formattedSize,
                                    sourceQualityTag = "Download",
                                    category = cat,
                                    isLockerUrl = false
                                )
                            )
                        }
                    }

                    if (streams.isNotEmpty()) break
                } catch (_: Exception) {}
            }

            try {
                val detailsRes = requestHosts("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$cleanId", null, token)
                val detData = detailsRes.getAsJsonObject("data") ?: detailsRes
                val detectors = detData.getAsJsonArray("resourceDetectors")
                if (detectors != null && detectors.size() > 0) {
                    for (dElem in detectors) {
                        val detector = dElem.asJsonObject
                        val resList = detector.getAsJsonArray("resolutionList")
                        val items = mutableListOf<JsonObject>()
                        if (resList != null && resList.size() > 0) {
                            for (r in resList) items.add(r.asJsonObject)
                        } else {
                            val dlUrl = detector.get("downloadUrl")?.asString
                            if (!dlUrl.isNullOrBlank() && !isDeprecationNoticeUrl(dlUrl)) {
                                items.add(detector)
                            }
                        }

                        for (item in items) {
                            val directUrl = item.get("downloadUrl")?.asString
                                ?: item.get("resourceLink")?.asString
                                ?: item.get("url")?.asString
                                ?: continue

                            if (isDeprecationNoticeUrl(directUrl) || !directUrl.startsWith("http")) continue
                            val base = directUrl.substringBefore("?")
                            if (!seenUrls.add(base)) continue

                            val resNum = item.get("resolution")?.asInt
                                ?: extractResolution(directUrl)
                            if (resNum > 1080 && !supports4k) continue
                            val resName = when {
                                resNum >= 2160 -> "4K Ultra HD"
                                resNum >= 1080 -> "1080p Full HD"
                                resNum >= 720 -> "720p HD"
                                resNum >= 480 -> "480p SD"
                                else -> "${resNum}p"
                            }
                            val cat = when {
                                resNum >= 2160 -> "2160p / 4K"
                                resNum >= 1080 -> "1080p"
                                resNum >= 720 -> "720p"
                                resNum >= 480 -> "480p"
                                else -> "Other"
                            }
                            val sizeBytes = item.get("size")?.asLong ?: item.get("firstSize")?.asLong
                            val formattedSize = sizeBytes?.let { bytes ->
                                if (bytes <= 0) null
                                else {
                                    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                                    val mb = bytes / (1024.0 * 1024.0)
                                    if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
                                    else String.format(java.util.Locale.US, "%.0f MB", mb)
                                }
                            }
                            val codec = item.get("codecName")?.asString ?: item.get("codec")?.asString ?: "h264"

                            val directHeaders = mapOf(
                                "User-Agent" to "com.community.oneroom/50020120 (Linux; U; Android 12; en_US; 22101316G)"
                            )

                            streams.add(
                                StreamSource(
                                    quality = resName,
                                    format = "MP4",
                                    url = directUrl,
                                    headers = directHeaders,
                                    directFile = true,
                                    codec = codec,
                                    label = "MovieBox Full Movie $resName",
                                    provider = "moviebox",
                                    title = "MovieBox Full Movie $resName",
                                    sizeBytes = sizeBytes,
                                    sizeFormatted = formattedSize,
                                    sourceQualityTag = "Full Movie",
                                    category = cat,
                                    isLockerUrl = false
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            val subtitles = getCaptions(cleanId, token)

            if (streams.isEmpty()) {
                try {
                    val detailsRes = requestHosts("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$cleanId", null, token)
                    val detData = detailsRes.getAsJsonObject("data") ?: detailsRes
                    val preVideo = detData.get("preVideoAddress")?.asString
                    if (!preVideo.isNullOrBlank() && !isDeprecationNoticeUrl(preVideo) && seenUrls.add(preVideo.substringBefore("?"))) {
                        streams.add(
                            StreamSource(
                                quality = "720p HD",
                                format = "MP4",
                                url = preVideo,
                                headers = mapOf(
                                    "User-Agent" to "com.community.oneroom/50020120 (Linux; U; Android 12; en_US; 22101316G)"
                                ),
                                directFile = true,
                                codec = "h264",
                                label = "MovieBox Preview (720p)",
                                provider = "moviebox",
                                title = "MovieBox Preview Stream",
                                sourceQualityTag = "Preview",
                                category = "720p",
                                isLockerUrl = false
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            streams.sortWith(
                compareByDescending<StreamSource> {
                    if (it.sourceQualityTag != "Preview") 1 else 0
                }.thenByDescending {
                    when {
                        it.quality.contains("2160") || it.quality.contains("4k", ignoreCase = true) -> 2160
                        it.quality.contains("1080") -> 1080
                        it.quality.contains("720") -> 720
                        it.quality.contains("480") -> 480
                        else -> 0
                    }
                }.thenByDescending {
                    if (it.format == "DASH" && it.sourceQualityTag != "Preview") 2
                    else if (it.directFile && it.sourceQualityTag == "Full Movie") 1
                    else 0
                }
            )

            android.util.Log.d("MovieBox", "resolveStreams: total ${streams.size} streams and ${subtitles.size} subs found")
            Pair(streams, subtitles)
        } catch (e: Exception) {
            android.util.Log.e("MovieBox", "resolveStreams error", e)
            Pair(emptyList(), emptyList())
        }
    }
}
