package com.sumanonline.sumanmovies.data.remote.moviebox

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
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
    companion object {
        private fun logD(tag: String, msg: String) {
            try {
                android.util.Log.d(tag, msg)
            } catch (_: Throwable) {
                println("[$tag] $msg")
            }
        }

        private fun logE(tag: String, msg: String, tr: Throwable? = null) {
            try {
                if (tr != null) android.util.Log.e(tag, msg, tr)
                else android.util.Log.e(tag, msg)
            } catch (_: Throwable) {
                System.err.println("[$tag] $msg" + if (tr != null) " - ${tr.message}" else "")
            }
        }

        fun safeFloat(obj: JsonObject, vararg fields: String): Float? {
            for (field in fields) {
                val elem = obj.get(field) ?: continue
                if (elem.isJsonNull) continue
                try {
                    val str = elem.asString.trim()
                    if (str.isNotEmpty()) {
                        val v = str.toFloatOrNull()
                        if (v != null) return v
                    }
                } catch (_: Exception) {}
            }
            return null
        }

        fun safeInt(obj: JsonObject, vararg fields: String, default: Int = 0): Int {
            for (field in fields) {
                val elem = obj.get(field) ?: continue
                if (elem.isJsonNull) continue
                try {
                    val str = elem.asString.trim()
                    if (str.isNotEmpty()) {
                        val v = str.toIntOrNull()
                        if (v != null) return v
                    }
                } catch (_: Exception) {}
            }
            return default
        }

        fun safeLong(obj: JsonObject, vararg fields: String, default: Long = 0L): Long {
            for (field in fields) {
                val elem = obj.get(field) ?: continue
                if (elem.isJsonNull) continue
                try {
                    val str = elem.asString.trim()
                    if (str.isNotEmpty()) {
                        val v = str.toLongOrNull()
                        if (v != null) return v
                    }
                } catch (_: Exception) {}
            }
            return default
        }
    }

    private val hostPool = listOf(
        "https://api6.aoneroom.com",
        "https://api4sg.aoneroom.com",
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
                        logD("MovieBox", "Success on $url: ${text.take(100)}")
                        return@withContext gson.fromJson(text, JsonObject::class.java)
                    } else {
                        val errBody = resp.body?.string() ?: ""
                        logE("MovieBox", "Host $base failed with code ${resp.code}: $errBody")
                        if (resp.code == 401) {
                            authToken = null
                        }
                        lastErr = "HTTP ${resp.code} $errBody"
                    }
                }
            } catch (e: Exception) {
                logE("MovieBox", "Exception on $url", e)
                lastErr = e.message ?: "Unknown fetch error"
            }
        }
        logE("MovieBox", "All hosts failed. Last: $lastErr")
        throw IllegalStateException("All MovieBox hosts failed. Last error: $lastErr")
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        subjectType: Int = 0
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val token = ensureSession()
            logD("MovieBox", "Got session token: ${token.take(15)}...")

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

            val subjectElements = mutableListOf<JsonObject>()
            if (res != null) {
                val dataObj = res.getAsJsonObject("data") ?: res
                val resultsArr = dataObj.getAsJsonArray("results")
                if (resultsArr != null && resultsArr.size() > 0) {
                    for (elem in resultsArr) {
                        if (!elem.isJsonObject) continue
                        val subs = elem.asJsonObject.getAsJsonArray("subjects")
                        if (subs != null) {
                            for (s in subs) {
                                if (s.isJsonObject) subjectElements.add(s.asJsonObject)
                            }
                        }
                    }
                }
                if (subjectElements.isEmpty()) {
                    val fallback = dataObj.getAsJsonArray("items") ?: dataObj.getAsJsonArray("list")
                    fallback?.forEach { if (it.isJsonObject) subjectElements.add(it.asJsonObject) }
                }
            }

            if (subjectElements.isEmpty()) {
                val payloadV1 = JsonObject().apply {
                    addProperty("keyword", query)
                    addProperty("page", page)
                    addProperty("perPage", 20)
                }
                res = try {
                    requestHosts("POST", "/wefeed-mobile-bff/subject-api/search", gson.toJson(payloadV1), token)
                } catch (_: Exception) {
                    null
                }
                if (res != null) {
                    val dataObj = res.getAsJsonObject("data") ?: res
                    val fallback = dataObj.getAsJsonArray("items")
                        ?: dataObj.getAsJsonArray("list")
                        ?: res.getAsJsonArray("items")
                    fallback?.forEach { if (it.isJsonObject) subjectElements.add(it.asJsonObject) }
                }
            }

            val list = subjectElements
            logD("MovieBox", "search '$query': total subjectElements = ${list.size}")
            val items = mutableListOf<MediaItem>()
            val seenIds = HashSet<String>()
            for (obj in list) {
                val hasResource = if (obj.has("hasResource")) obj.get("hasResource").asBoolean else true
                if (!hasResource) {
                    logD("MovieBox", "Skipped item ${obj.get("title")?.asString} because hasResource=false")
                    continue
                }
                val id = obj.get("subjectId")?.asString ?: obj.get("id")?.asString ?: continue
                val seNum = safeInt(obj, "season", "se", default = 0).takeIf { it > 0 }
                val uniqueId = if (seNum != null && seNum > 0) "$id:s$seNum" else id
                if (!seenIds.add(uniqueId)) continue
                val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: "Untitled"
                val sType = safeInt(obj, "subjectType", "stype", default = 1)
                val isSeries = sType == 2
                val year = obj.get("releaseDate")?.asString?.take(4) ?: obj.get("year")?.asString
                val cover = extractCoverUrl(obj)
                val score = safeFloat(obj, "score", "imdbRatingValue")

                items.add(
                    MediaItem(
                        id = "moviebox:$uniqueId",
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
        } catch (e: Exception) {
            logE("MovieBox", "search failed for '$query'", e)
            emptyList()
        }
    }

    private fun extractCoverUrl(obj: JsonObject): String? {
        val coverElem = obj.get("cover")
        if (coverElem != null && !coverElem.isJsonNull) {
            if (coverElem.isJsonObject) {
                val u = coverElem.asJsonObject.get("url")?.takeIf { !it.isJsonNull }?.asString
                if (!u.isNullOrBlank()) return u
            } else if (coverElem.isJsonPrimitive) {
                val u = coverElem.asString
                if (!u.isNullOrBlank()) return u
            }
        }
        return obj.get("coverUrl")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("poster")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("posterUrl")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("cover_url")?.takeIf { !it.isJsonNull }?.asString
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
            val cleanId = subjectId.removePrefix("moviebox:").substringBefore(":").trim()
            val detailsRes = requestHosts("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$cleanId", null, token)
            val subject = detailsRes.getAsJsonObject("data")?.getAsJsonObject("subject")
                ?: detailsRes.getAsJsonObject("subject")
                ?: detailsRes.getAsJsonObject("data")
                ?: detailsRes

            val idStr = subject.get("subjectId")?.asString ?: subject.get("id")?.asString ?: cleanId
            val title = subject.get("title")?.asString ?: subject.get("name")?.asString ?: "Untitled"
            val sType = safeInt(subject, "subjectType", "stype", default = 1)
            val mediaType = if (sType == 2) MediaType.SERIES else MediaType.MOVIE

            val rawYear = subject.get("releaseDate")?.asString ?: subject.get("year")?.asString ?: ""
            val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawYear)?.value
            val description = subject.get("description")?.asString ?: subject.get("intro")?.asString
            val cover = extractCoverUrl(subject)
            val rating = safeFloat(subject, "score", "imdbRatingValue")

            val genresList = mutableListOf<String>()
            val gElem = subject.get("genre") ?: subject.get("genres")
            if (gElem != null && !gElem.isJsonNull) {
                if (gElem.isJsonArray) {
                    gElem.asJsonArray.forEach { g ->
                        if (!g.isJsonNull && g.isJsonPrimitive) genresList.add(g.asString)
                    }
                } else if (gElem.isJsonPrimitive) {
                    genresList.add(gElem.asString)
                }
            }

            val seasonsList = if (mediaType == MediaType.SERIES) {
                getSeasonInfo(cleanId)
            } else emptyList()

            MediaDetails(
                id = "moviebox:$idStr",
                title = title,
                mediaType = mediaType,
                year = year,
                rating = rating,
                posterUrl = cover,
                backdropUrl = cover,
                overview = description,
                genres = genresList,
                seasons = seasonsList
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getSeasonInfo(subjectId: String): List<Season> = withContext(Dispatchers.IO) {
        try {
            val token = ensureSession()
            val cleanId = subjectId.removePrefix("moviebox:").substringBefore(":").trim()
            val res = requestHosts("GET", "/wefeed-mobile-bff/subject-api/season-info?subjectId=$cleanId", null, token)
            val dataObj = res.getAsJsonObject("data") ?: res
            val seasonsArr = dataObj.getAsJsonArray("seasons") ?: return@withContext emptyList()
            val list = mutableListOf<Season>()
            for (elem in seasonsArr) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val se = obj.get("se")?.asInt ?: continue
                val maxEp = obj.get("maxEp")?.asInt?.coerceAtLeast(1) ?: 1
                val eps = (1..maxEp).map { ep ->
                    Episode(
                        id = "moviebox:$cleanId:s${se}e${ep}",
                        episodeNumber = ep,
                        seasonNumber = se,
                        title = "Episode $ep"
                    )
                }
                list.add(
                    Season(
                        seasonNumber = se,
                        name = "Season $se",
                        episodeCount = maxEp,
                        episodes = eps
                    )
                )
            }
            list.sortedBy { it.seasonNumber }
        } catch (_: Exception) {
            emptyList()
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
            val bytes = java.util.Base64.getDecoder().decode(padded)
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
            val bytes = java.util.Base64.getDecoder().decode(clean)
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
        if (streamUrl.isNotBlank() && !isDeprecationNoticeUrl(streamUrl)) {
            val cookieRequiresPreview = rawCookie?.contains("/preview/") == true ||
                rawCookie?.contains("urlprefix=") == true
            if (streamUrl.contains("/preview/") && !cookieRequiresPreview) {
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

        if (!rawCookie.isNullOrBlank()) {
            val resolved = resolveDashManifestFromPolicy(rawCookie)
            if (!resolved.isNullOrBlank() && !isDeprecationNoticeUrl(resolved)) {
                return resolved
            }
        }

        return streamUrl
    }

    private fun normalizeAudioLanguage(name: String, lanCode: String? = null, fallback: String = "English"): String {
        val lower = name.trim().lowercase()
        val code = lanCode?.trim()?.lowercase()
        return when {
            lower.contains("hindi") || lower.contains("हिन्दी") || code == "hi" || code == "hin" -> "Hindi"
            lower.contains("tamil") || lower.contains("தமிழ்") || code == "ta" || code == "tam" -> "Tamil"
            lower.contains("telugu") || lower.contains("తెలుగు") || code == "te" || code == "tel" -> "Telugu"
            lower.contains("malayalam") || lower.contains("മലയാളം") || code == "ml" || code == "mal" -> "Malayalam"
            lower.contains("kannada") || lower.contains("ಕನ್ನಡ") || code == "kn" || code == "kan" -> "Kannada"
            lower.contains("bengali") || lower.contains("বাংলা") || code == "bn" || code == "ben" -> "Bengali"
            lower.contains("marathi") || lower.contains("मराठी") || code == "mr" || code == "mar" -> "Marathi"
            lower.contains("punjabi") || lower.contains("ਪੰਜਾਬੀ") || code == "pa" || code == "pan" -> "Punjabi"
            lower.contains("urdu") || lower.contains("اردو") || code == "ur" || code == "urd" -> "Urdu"
            lower.contains("gujarati") || code == "gu" || code == "guj" -> "Gujarati"
            lower.contains("spanish") || lower.contains("español") || lower.contains("esla") || code == "es" || code == "spa" -> "Spanish"
            lower.contains("portuguese") || lower.contains("português") || lower.contains("ptbr") || code == "pt" || code == "por" -> "Portuguese"
            lower.contains("korean") || lower.contains("한국어") || code == "ko" || code == "kor" -> "Korean"
            lower.contains("japanese") || lower.contains("日本語") || code == "ja" || code == "jpn" -> "Japanese"
            lower.contains("chinese") || lower.contains("中文") || code == "zh" || code == "chi" -> "Chinese"
            lower.contains("french") || lower.contains("français") || code == "fr" || code == "fre" -> "French"
            lower.contains("german") || lower.contains("deutsch") || code == "de" || code == "ger" -> "German"
            lower.contains("russian") || lower.contains("русский") || code == "ru" || code == "rus" -> "Russian"
            lower.contains("arabic") || lower.contains("عربي") || code == "ar" || code == "ara" -> "Arabic"
            lower.contains("indonesian") || code == "id" || code == "ind" -> "Indonesian"
            lower.contains("thai") || code == "th" || code == "tha" -> "Thai"
            lower.contains("vietnamese") || code == "vi" || code == "vie" -> "Vietnamese"
            lower.contains("english") || code == "en" || code == "eng" -> "English"
            name.isNotBlank() && !lower.contains("original") && !lower.contains("dub") && name != "Unknown" -> name.trim()
            else -> fallback
        }
    }

    private fun isDeprecationNoticeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("1c7de0bd3393702d9191801f15f88f8d") ||
                lower.contains("9a0461bc39da389663bf3dbb17091d3f") ||
                lower.contains("b164fbfb4347792950bdfbfb563d39d9") ||
                lower.contains("/notice.mp4") ||
                (lower.contains("macdn.aoneroom.com") && lower.contains("/other/"))
    }

    private fun normalizeSubtitleLanguage(name: String, lanCode: String?): String {
        val lower = name.trim().lowercase()
        val code = lanCode?.trim()?.lowercase()
        return when {
            lower.contains("english") || code == "en" || code == "eng" -> "English"
            lower.contains("hindi") || lower.contains("हिन्दी") || code == "hi" || code == "hin" -> "Hindi"
            lower.contains("bengali") || lower.contains("বাংলা") || code == "bn" || code == "ben" -> "Bengali"
            lower.contains("spanish") || lower.contains("español") || code == "es" || code == "spa" -> "Spanish"
            lower.contains("french") || lower.contains("français") || code == "fr" || code == "fre" -> "French"
            lower.contains("arabic") || lower.contains("عربي") || code == "ar" || code == "ara" -> "Arabic"
            lower.contains("tamil") || lower.contains("தமிழ்") || code == "ta" || code == "tam" -> "Tamil"
            lower.contains("telugu") || lower.contains("తెలుగు") || code == "te" || code == "tel" -> "Telugu"
            lower.contains("portuguese") || lower.contains("português") || code == "pt" || code == "por" -> "Portuguese"
            lower.contains("indonesian") || code == "id" || code == "ind" -> "Indonesian"
            lower.contains("malay") || code == "ms" || code == "may" -> "Malay"
            lower.contains("filipino") || lower.contains("tagalog") || code == "fil" || code == "tgl" -> "Filipino"
            lower.contains("german") || lower.contains("deutsch") || code == "de" || code == "ger" -> "German"
            lower.contains("russian") || lower.contains("русский") || code == "ru" || code == "rus" -> "Russian"
            lower.contains("japanese") || lower.contains("日本語") || code == "ja" || code == "jpn" -> "Japanese"
            lower.contains("korean") || lower.contains("한국어") || code == "ko" || code == "kor" -> "Korean"
            lower.contains("chinese") || lower.contains("中文") || code == "zh" || code == "chi" -> "Chinese"
            lower.contains("urdu") || lower.contains("اردو") || code == "ur" || code == "urd" -> "Urdu"
            lower.contains("punjabi") || lower.contains("ਪੰਜਾਬੀ") || code == "pa" || code == "pan" -> "Punjabi"
            name.isNotBlank() && name != "Unknown" -> name
            !lanCode.isNullOrBlank() -> lanCode.uppercase()
            else -> "English"
        }
    }

    private suspend fun getCaptions(subjectId: String, resourceId: String?, token: String): List<SubtitleTrack> {
        val result = mutableListOf<SubtitleTrack>()
        val seen = mutableSetOf<String>()
        val endpoints = mutableListOf<String>()
        if (!resourceId.isNullOrBlank() && resourceId != "0") {
            endpoints.add("/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$resourceId")
        }
        endpoints.add("/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=0")

        for (path in endpoints) {
            try {
                val res = requestHosts("GET", path, null, token)
                val dataObj = res.getAsJsonObject("data") ?: res
                val captions = dataObj.getAsJsonArray("extCaptions") ?: res.getAsJsonArray("extCaptions")
                if (captions != null) {
                    for (cap in captions) {
                        val cObj = cap.asJsonObject
                        val url = cObj.get("url")?.asString ?: continue
                        if (url.contains("aa348f2541d13ffe") || !seen.add(url)) continue
                        val rawName = cObj.get("lanName")?.asString ?: cObj.get("lan")?.asString ?: "Unknown"
                        val normalizedLang = normalizeSubtitleLanguage(rawName, cObj.get("lan")?.asString)
                        val trackId = "mb_sub_${result.size}_${cObj.get("id")?.asString ?: ""}"
                        result.add(
                            SubtitleTrack(
                                id = trackId,
                                language = normalizedLang,
                                label = normalizedLang,
                                url = url,
                                format = if (url.endsWith(".vtt", ignoreCase = true) || url.contains(".vtt")) "VTT" else "SRT"
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return result
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
            val cleanId = subjectId.removePrefix("moviebox:").substringBefore(":").trim()

            val streams = mutableListOf<StreamSource>()
            val seenUrls = mutableSetOf<String>()
            var isMovie = (season == 0 && episode == 0)
            val supports4k = com.sumanonline.sumanmovies.util.DeviceCapabilities.supports4KHevc()

            var originalAudioSubjectId: String? = null
            data class DubTarget(val id: String, val langName: String, val isOriginal: Boolean)
            val dubTargets = mutableListOf<DubTarget>()

            var detectedOrigLang = "Original"

            try {
                val detailsRes = requestHosts("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$cleanId", null, token)
                val detData = detailsRes.getAsJsonObject("data") ?: detailsRes
                val subjectObj = detData.getAsJsonObject("subject") ?: detData
                val sType = subjectObj.get("subjectType")?.asInt ?: subjectObj.get("stype")?.asInt
                if (sType != null) {
                    isMovie = (sType != 2)
                }

                val origLangRaw = subjectObj.get("originalLanguage")?.asString
                    ?: subjectObj.get("language")?.asString
                    ?: subjectObj.get("lanName")?.asString
                    ?: detData.get("language")?.asString
                if (!origLangRaw.isNullOrBlank()) {
                    detectedOrigLang = normalizeAudioLanguage(origLangRaw, origLangRaw, fallback = "Original")
                }

                val dubsArr = detData.getAsJsonArray("dubs") ?: subjectObj.getAsJsonArray("dubs")
                if (dubsArr != null && dubsArr.size() > 0) {
                    for (elem in dubsArr) {
                        val dObj = elem.asJsonObject
                        val dubId = dObj.get("subjectId")?.asString ?: dObj.get("id")?.asString ?: continue
                        val rawName = dObj.get("lanName")?.asString ?: dObj.get("language")?.asString ?: dObj.get("lang")?.asString ?: "Unknown"
                        val isOrig = dObj.get("original")?.asBoolean ?: false
                        val cleanLang = when {
                            rawName.contains("Original", ignoreCase = true) || isOrig -> {
                                val specific = normalizeAudioLanguage(rawName, dObj.get("lan")?.asString, fallback = detectedOrigLang)
                                if (specific == "Original" || specific == "English") {
                                    if (detectedOrigLang != "Original") detectedOrigLang else "English"
                                } else specific
                            }
                            else -> normalizeAudioLanguage(rawName, dObj.get("lan")?.asString, fallback = "Dub")
                        }
                        if (isOrig || originalAudioSubjectId == null) {
                            originalAudioSubjectId = dubId
                        }
                        dubTargets.add(DubTarget(dubId, cleanLang, isOrig))
                    }
                }
            } catch (_: Exception) {}

            val defaultCleanLang = if (detectedOrigLang != "Original") detectedOrigLang else "English"
            if (dubTargets.none { it.id == cleanId }) {
                dubTargets.add(0, DubTarget(cleanId, defaultCleanLang, true))
            }

            val primaryTargetLang = dubTargets.firstOrNull { it.id == cleanId }?.langName ?: defaultCleanLang

            val queryTargets = mutableListOf<DubTarget>()
            dubTargets.firstOrNull { it.id == cleanId }?.let { queryTargets.add(it) }
            for (t in dubTargets) {
                if (queryTargets.none { it.id == t.id } && queryTargets.size < 6) {
                    queryTargets.add(t)
                }
            }

            val collectedResourcePairs = mutableListOf<Pair<String, String>>()

            for (target in queryTargets) {
                val targetId = target.id
                val targetLang = target.langName

                val pathsToTry = mutableListOf<String>()
                val seNum = if (season > 0) season else 1
                val epNum = if (episode > 0) episode else 1
                if (!isMovie) {
                    pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$targetId&se=$seNum&ep=$epNum&isVip=true&X-Play-Mode=2")
                    pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$targetId&se=$seNum&ep=$epNum")
                    pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$targetId&se=$seNum&ep=$epNum&download=true")
                } else {
                    pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$targetId&isVip=true&X-Play-Mode=2")
                    pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$targetId")
                    pathsToTry.add("/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$targetId&download=true")
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
                                val streamDuration = safeLong(sObj, "duration", default = 0L)
                                val streamResourceId = sObj.get("id")?.asString ?: sObj.get("resourceId")?.asString
                                if (!streamResourceId.isNullOrBlank()) {
                                    collectedResourcePairs.add(Pair(targetId, streamResourceId))
                                }

                                val rawCookie = sObj.get("signCookie")?.asString
                                    ?: sObj.get("cookie")?.asString
                                    ?: sObj.get("sign_cookie")?.asString

                                val effectiveUrl = resolveMasterManifest(streamUrl, rawCookie)
                                if (isDeprecationNoticeUrl(effectiveUrl) || !effectiveUrl.startsWith("http")) continue

                                val is15MinCapped = (isMovie && (streamDuration in 1..915 || effectiveUrl.contains("/preview/"))) ||
                                        (!isMovie && (streamDuration in 1..300 || effectiveUrl.contains("/preview/")))

                                val isDash = effectiveUrl.contains(".mpd") || effectiveUrl.contains("/dash/")
                                val format = if (isDash) "DASH" else "HLS"
                                val codec = sObj.get("codecName")?.asString ?: sObj.get("codec")?.asString ?: "h264"
                                val totalSizeBytes = safeLong(sObj, "size", default = 0L)

                                val headers = mutableMapOf(
                                    "Referer" to "https://sportslive.wine",
                                    "User-Agent" to "com.community.oneroom/50020126 (Linux; U; Android 12; en_US; 22101316G; Build/QP1A.191005.007.A3; Cronet/135.0.7012.3)"
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

                                    val streamKey = "$effectiveUrl#$res#$codec#$targetLang"
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

                                    val baseLabel = "MovieBox $format $qualityName ($targetLang)"
                                    val finalLabel = if (is15MinCapped) "$baseLabel (Sample)" else baseLabel

                                    streams.add(
                                        StreamSource(
                                            quality = qualityName,
                                            format = format,
                                            url = effectiveUrl,
                                            headers = headers,
                                            directFile = false,
                                            codec = codec,
                                            label = finalLabel,
                                            provider = "moviebox",
                                            audioLangs = targetLang,
                                            title = finalLabel,
                                            sizeBytes = if (scaledSize > 0) scaledSize else null,
                                            sizeFormatted = formattedSize,
                                            sourceQualityTag = if (is15MinCapped) "Preview" else "Full Stream",
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
                                if (isDeprecationNoticeUrl(url) || !seenUrls.add(url.substringBefore("?") + "#" + targetLang)) continue

                                val resNum = safeInt(dObj, "resolution", default = 1080)
                                if (resNum > 1080 && !supports4k) continue
                                val resName = "${resNum}p"
                                val size = safeLong(dObj, "size", default = 0L).takeIf { it > 0 }
                                val cat = when {
                                    resNum >= 2160 -> "2160p / 4K"
                                    resNum >= 1080 -> "1080p"
                                    resNum >= 720 -> "720p"
                                    resNum >= 480 -> "480p"
                                    else -> "Other"
                                }
                                val formattedSize = size?.let { bytes ->
                                    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                                    val mb = bytes / (1024.0 * 1024.0)
                                    if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
                                    else String.format(java.util.Locale.US, "%.0f MB", mb)
                                }

                                val dlTitle = "MovieBox Direct $resName ($targetLang)"
                                streams.add(
                                    StreamSource(
                                        quality = "$resName Download",
                                        format = "MP4",
                                        url = url,
                                        headers = mapOf(
                                            "User-Agent" to "com.community.oneroom/50020126 (Linux; U; Android 12; en_US; 22101316G; Build/QP1A.191005.007.A3; Cronet/135.0.7012.3)"
                                        ),
                                        sizeBytes = size,
                                        directFile = true,
                                        codec = "h264",
                                        label = dlTitle,
                                        provider = "moviebox",
                                        audioLangs = targetLang,
                                        title = dlTitle,
                                        sizeFormatted = formattedSize,
                                        sourceQualityTag = "Full Stream",
                                        category = cat,
                                        isLockerUrl = false
                                    )
                                )
                            }
                        }

                        if (streams.any { it.audioLangs == targetLang && it.sourceQualityTag == "Full Stream" }) break
                    } catch (_: Exception) {}
                }
            }

            for (target in queryTargets) {
                val targetId = target.id
                val targetLang = target.langName

                try {
                    val detailsRes = requestHosts("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$targetId", null, token)
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
                                if (!seenUrls.add(base + "#" + targetLang)) continue

                                val resNum = safeInt(item, "resolution", default = extractResolution(directUrl))
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
                                val sizeBytes = safeLong(item, "size", default = safeLong(item, "firstSize", default = 0L)).takeIf { it > 0 }
                                val formattedSize = sizeBytes?.let { bytes ->
                                    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                                    val mb = bytes / (1024.0 * 1024.0)
                                    if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
                                    else String.format(java.util.Locale.US, "%.0f MB", mb)
                                }
                                val codec = item.get("codecName")?.asString ?: item.get("codec")?.asString ?: "h264"

                                val directHeaders = mapOf(
                                    "User-Agent" to "com.community.oneroom/50020126 (Linux; U; Android 12; en_US; 22101316G; Build/QP1A.191005.007.A3; Cronet/135.0.7012.3)"
                                )

                                val itemDuration = safeLong(item, "duration", "firstDuration", "playDuration", default = 0L)
                                val isTrailerOrSample = item.get("isTrailer")?.asBoolean == true ||
                                        item.get("trailer")?.asBoolean == true ||
                                        (isMovie && itemDuration in 1..90) ||
                                        (!isMovie && itemDuration in 1..45)

                                val label = if (isTrailerOrSample) "MovieBox Direct $resName ($targetLang) (Trailer)"
                                            else "MovieBox Direct $resName ($targetLang)"

                                streams.add(
                                    StreamSource(
                                        quality = resName,
                                        format = "MP4",
                                        url = directUrl,
                                        headers = directHeaders,
                                        directFile = true,
                                        codec = codec,
                                        label = label,
                                        provider = "moviebox",
                                        audioLangs = targetLang,
                                        title = label,
                                        sizeBytes = sizeBytes,
                                        sizeFormatted = formattedSize,
                                        sourceQualityTag = if (isTrailerOrSample) "Preview" else "Full Stream",
                                        category = cat,
                                        isLockerUrl = false
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}

                val resourcePath = if (isMovie) {
                    "/wefeed-mobile-bff/subject-api/resource?subjectId=$targetId&page=1&perPage=20"
                } else {
                    val seVal = if (season > 0) season else 1
                    val epVal = if (episode > 0) episode else 1
                    "/wefeed-mobile-bff/subject-api/resource?subjectId=$targetId&se=$seVal&ep=$epVal&page=1&perPage=20"
                }
                try {
                    val resObj = requestHosts("GET", resourcePath, null, token)
                    val resData = resObj.getAsJsonObject("data") ?: resObj
                    val listArr = resData.getAsJsonArray("list")
                    if (listArr != null && listArr.size() > 0) {
                        for (elem in listArr) {
                            if (!elem.isJsonObject) continue
                            val item = elem.asJsonObject
                            val rSe = safeInt(item, "se", default = 0)
                            val rEp = safeInt(item, "ep", default = 0)
                            if (!isMovie && season > 0 && episode > 0 && (rSe != season || rEp != episode)) {
                                continue
                            }
                            val link = item.get("resourceLink")?.asString ?: item.get("url")?.asString ?: continue
                            if (isDeprecationNoticeUrl(link) || !link.startsWith("http")) continue
                            val base = link.substringBefore("?")
                            if (!seenUrls.add(base + "#" + targetLang)) continue
                            val resNum = safeInt(item, "resolution", default = extractResolution(link))
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
                            val sizeBytes = safeLong(item, "size", default = 0L).takeIf { it > 0 }
                            val formattedSize = sizeBytes?.let { bytes ->
                                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                                val mb = bytes / (1024.0 * 1024.0)
                                if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
                                else String.format(java.util.Locale.US, "%.0f MB", mb)
                            }
                            val codec = item.get("codecName")?.asString ?: item.get("codec")?.asString ?: "h264"

                            val resHeaders = mapOf(
                                "User-Agent" to "com.community.oneroom/50020126 (Linux; U; Android 12; en_US; 22101316G; Build/QP1A.191005.007.A3; Cronet/135.0.7012.3)"
                            )

                            val itemDuration = safeLong(item, "duration", "firstDuration", "playDuration", default = 0L)
                            val isTrailerOrSample = item.get("isTrailer")?.asBoolean == true ||
                                    item.get("trailer")?.asBoolean == true ||
                                    (isMovie && itemDuration in 1..90) ||
                                    (!isMovie && itemDuration in 1..45)

                            val dlTitle = if (isTrailerOrSample) "MovieBox Direct $resName ($targetLang) (Trailer)"
                                          else "MovieBox Direct $resName ($targetLang)"

                            streams.add(
                                StreamSource(
                                    quality = resName,
                                    format = "MP4",
                                    url = link,
                                    headers = resHeaders,
                                    sizeBytes = sizeBytes,
                                    directFile = true,
                                    codec = codec,
                                    label = dlTitle,
                                    provider = "moviebox",
                                    audioLangs = targetLang,
                                    title = dlTitle,
                                    sizeFormatted = formattedSize,
                                    sourceQualityTag = if (isTrailerOrSample) "Preview" else "Full Stream",
                                    category = cat,
                                    isLockerUrl = false
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            val collectedSubs = mutableListOf<SubtitleTrack>()
            val queriedResourceKeys = mutableSetOf<String>()

            for ((subId, rId) in collectedResourcePairs) {
                val key = "$subId#$rId"
                if (queriedResourceKeys.add(key)) {
                    val sList = getCaptions(subId, rId, token)
                    collectedSubs.addAll(sList)
                }
            }

            if (originalAudioSubjectId != null && originalAudioSubjectId != cleanId) {
                val origKey = "$originalAudioSubjectId#0"
                if (queriedResourceKeys.add(origKey)) {
                    val sList = getCaptions(originalAudioSubjectId, "0", token)
                    collectedSubs.addAll(sList)
                }
            }

            if (queriedResourceKeys.add("$cleanId#0")) {
                val defaultSubs = getCaptions(cleanId, "0", token)
                collectedSubs.addAll(defaultSubs)
            }

            val subtitles = collectedSubs
                .filter { it.url.isNotBlank() && !it.url.contains("aa348f2541d13ffe") }
                .distinctBy { it.language to it.url }

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
                                    "User-Agent" to "com.community.oneroom/50020126 (Linux; U; Android 12; en_US; 22101316G; Build/QP1A.191005.007.A3; Cronet/135.0.7012.3)"
                                ),
                                directFile = true,
                                codec = "h264",
                                label = "MovieBox Preview (720p)",
                                provider = "moviebox",
                                audioLangs = primaryTargetLang,
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
                    if (it.audioLangs.equals(primaryTargetLang, ignoreCase = true)) 1 else 0
                }.thenByDescending {
                    when {
                        it.quality.contains("2160") || it.quality.contains("4k", ignoreCase = true) -> 2160
                        it.quality.contains("1080") -> 1080
                        it.quality.contains("720") -> 720
                        it.quality.contains("480") -> 480
                        else -> 0
                    }
                }.thenByDescending {
                    if (it.sourceQualityTag != "Preview" && (it.directFile || it.format.equals("MP4", ignoreCase = true))) 2
                    else if (it.sourceQualityTag != "Preview" && it.format.equals("DASH", ignoreCase = true)) 1
                    else 0
                }
            )

            logD("MovieBox", "resolveStreams: total ${streams.size} streams and ${subtitles.size} subs found")
            Pair(streams, subtitles)
        } catch (e: Exception) {
            logE("MovieBox", "resolveStreams error", e)
            Pair(emptyList(), emptyList())
        }
    }
}
