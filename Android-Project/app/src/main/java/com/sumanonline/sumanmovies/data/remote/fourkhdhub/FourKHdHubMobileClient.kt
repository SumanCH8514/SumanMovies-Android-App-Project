package com.sumanonline.sumanmovies.data.remote.fourkhdhub



import android.content.Context



import android.content.Intent



import android.net.Uri
import android.util.Base64
import java.net.URLDecoder
import java.util.Base64 as JavaBase64
import com.google.gson.JsonParser



import com.sumanonline.sumanmovies.data.models.MediaItem



import com.sumanonline.sumanmovies.data.models.MediaType



import com.sumanonline.sumanmovies.data.models.StreamMirror



import com.sumanonline.sumanmovies.data.models.StreamSource



import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.util.StreamHealthValidator



import kotlinx.coroutines.Dispatchers



import kotlinx.coroutines.withContext



import okhttp3.FormBody
import okhttp3.OkHttpClient



import okhttp3.Request



import java.net.URLEncoder



import java.nio.charset.StandardCharsets



import java.util.concurrent.TimeUnit



import java.util.regex.Pattern



class FourKHdHubMobileClient {



    private val client = OkHttpClient.Builder()



        .connectTimeout(15, TimeUnit.SECONDS)



        .readTimeout(20, TimeUnit.SECONDS)



        .followRedirects(true)



        .followSslRedirects(true)



        .build()



    private val mobileUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"



    private val baseUrl = "https://4khdhub.one"



    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {



        try {



            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")



            val targetUrl = "$baseUrl/?s=$encodedQuery"



            val request = Request.Builder()



                .url(targetUrl)



                .header("User-Agent", mobileUserAgent)



                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")



                .header("Accept-Language", "en-US,en;q=0.5")



                .header("Referer", "$baseUrl/")



                .build()



            val response = client.newCall(request).execute()



            if (!response.isSuccessful) return@withContext emptyList()



            val html = response.body?.string() ?: return@withContext emptyList()



            parseSearchHtml(html)



        } catch (_: Exception) {



            emptyList()



        }



    }



    private fun parseSearchHtml(html: String): List<MediaItem> {



        val items = mutableListOf<MediaItem>()



        val cardPattern = Pattern.compile(



            """<a\s+[^>]*href=["']([^"']+)["'][^>]*class=["'][^"']*movie-card[^"']*["'][^>]*>([\s\S]*?)</a>""",



            Pattern.CASE_INSENSITIVE



        )



        val matcher = cardPattern.matcher(html)



        val titlePattern = Pattern.compile("""class=["'][^"']*movie-card-title[^"']*["'][^>]*>([^<]+)""", Pattern.CASE_INSENSITIVE)



        val metaPattern = Pattern.compile("""class=["'][^"']*movie-card-meta[^"']*["'][^>]*>([^<]+)""", Pattern.CASE_INSENSITIVE)



        val imgPattern = Pattern.compile("""<img[^>]+(?:src|data-src)=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)



        val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")



        while (matcher.find()) {



            val rawHref = matcher.group(1) ?: continue



            val cardContent = matcher.group(2) ?: ""



            val titleMatcher = titlePattern.matcher(cardContent)



            val title = if (titleMatcher.find()) titleMatcher.group(1)?.trim() ?: "" else ""



            if (title.isBlank()) continue



            val metaMatcher = metaPattern.matcher(cardContent)



            val metaText = if (metaMatcher.find()) metaMatcher.group(1)?.trim() ?: "" else ""



            val year = yearRegex.find(metaText)?.value ?: yearRegex.find(title)?.value



            val imgMatcher = imgPattern.matcher(cardContent)



            val posterUrl = if (imgMatcher.find()) imgMatcher.group(1)?.trim() else null



            val isSeries = rawHref.contains("-series-") || rawHref.contains("-season-") ||



                    title.contains("Season", ignoreCase = true) || title.contains("Series", ignoreCase = true)



            val fullUrl = if (rawHref.startsWith("http")) rawHref else "$baseUrl/${rawHref.removePrefix("/")}"



            items.add(



                MediaItem(



                    id = "fourkhdhub:$fullUrl",



                    title = title,



                    mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE,



                    year = year,



                    posterUrl = posterUrl,



                    provider = "fourkhdhub"



                )



            )



        }



        return items



    }



    suspend fun resolveStreams(



        pageUrlOrSlug: String,



        season: Int = 1,



        episode: Int = 1



    ): Pair<List<StreamSource>, List<SubtitleTrack>> = withContext(Dispatchers.IO) {



        val cleanTarget = pageUrlOrSlug.removePrefix("fourkhdhub:")



        val targetUrl = when {



            cleanTarget.startsWith("http") -> cleanTarget



            cleanTarget.startsWith("/") -> "$baseUrl$cleanTarget"



            else -> "$baseUrl/$cleanTarget"



        }



        try {



            val request = Request.Builder()



                .url(targetUrl)



                .header("User-Agent", mobileUserAgent)



                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")



                .header("Referer", "$baseUrl/")



                .build()



            val response = client.newCall(request).execute()



            if (!response.isSuccessful) return@withContext Pair(emptyList(), emptyList())



            val html = response.body?.string() ?: return@withContext Pair(emptyList(), emptyList())



            val options = parseDownloadOptions(html, targetUrl, season, episode)



            if (options.isNotEmpty()) {



                val firstOption = options.firstOrNull()



                if (firstOption != null && firstOption.isLockerUrl && firstOption.url.isNotBlank()) {



                    try {



                        val resolvedDirect = resolveLockerUrl(firstOption.url, targetUrl)



                        if (resolvedDirect != null && resolvedDirect.url.isNotBlank()) {



                            val directStream = firstOption.copy(



                                url = resolvedDirect.url,



                                headers = resolvedDirect.headers,



                                format = if (firstOption.format == "MKV" || resolvedDirect.format == "MKV") "MKV" else resolvedDirect.format,



                                codec = resolvedDirect.codec,



                                isLockerUrl = false



                            )



                            return@withContext Pair(listOf(directStream) + options.drop(1), emptyList())



                        }



                    } catch (_: Exception) {}



                }



                return@withContext Pair(options, emptyList())



            }



            val fallbackStreams = extractStreamsFromDetailHtml(html, targetUrl)



            Pair(fallbackStreams, emptyList())



        } catch (_: Exception) {



            Pair(emptyList(), emptyList())



        }



    }



    fun parseDownloadOptions(



        html: String,



        pageReferer: String,



        season: Int = 0,



        episode: Int = 0



    ): List<StreamSource> {



        val options = mutableListOf<StreamSource>()



        if (season > 0 && episode > 0) {



                        val blockPattern = Pattern.compile(
                """<div[^>]*class=["'][^"']*season-item\s+episode-item[^"']*["']([\s\S]*?)(?=<div[^>]*class=["'][^"']*season-item\s+episode-item|$|<footer)""",
                Pattern.CASE_INSENSITIVE
            )
            val blockMatcher = blockPattern.matcher(html)
            while (blockMatcher.find()) {
                val blockHtml = blockMatcher.group(1) ?: continue
                val formatTitleMatcher = Pattern.compile("""<h3[^>]*class=["']episode-title["'][^>]*>([^<]+)</h3>""", Pattern.CASE_INSENSITIVE).matcher(blockHtml)
                val formatTitle = if (formatTitleMatcher.find()) formatTitleMatcher.group(1)?.trim() ?: "" else ""
                val seasonMatcher = Pattern.compile("""\b(?:S0?|Season\s*0?)(\d+)\b""", Pattern.CASE_INSENSITIVE).matcher(formatTitle)
                val blockSeason = if (seasonMatcher.find()) seasonMatcher.group(1)?.toIntOrNull() ?: 1 else 1
                if (season > 0 && blockSeason != season) continue
                val metaMatcher = Pattern.compile("""<div[^>]*class=["']episode-meta["'][^>]*>([\s\S]*?)</div>""", Pattern.CASE_INSENSITIVE).matcher(blockHtml)
                val rawMeta = if (metaMatcher.find()) metaMatcher.group(1) ?: "" else ""
                val badgeMatcher = Pattern.compile("""<span[^>]*class=["']badge["'][^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE).matcher(rawMeta)
                val badges = mutableListOf<String>()
                while (badgeMatcher.find()) {
                    val b = badgeMatcher.group(1)?.trim() ?: continue
                    if (!b.contains("Episodes", ignoreCase = true) && !b.contains("Episode", ignoreCase = true)) {
                        badges.add(b)
                    }
                }
                val audioLangs = if (badges.isNotEmpty()) badges.joinToString(", ") else "Multi Audio"
                val epPattern = Pattern.compile(
                    """<div\s+class=["'][^"']*episode-download-item[^"']*["']([\s\S]*?)(?=<div\s+class=["'][^"']*episode-download-item|$|</div>\s*</div>\s*</div>)""",
                    Pattern.CASE_INSENSITIVE
                )
                val epMatcher = epPattern.matcher(blockHtml)
                val targetEpRegex = Regex("""(?i)\b(?:Episode\s*[-–]?\s*0?${episode}|E0?${episode}|S0?${blockSeason}E0?${episode})\b""")
                while (epMatcher.find()) {
                    val epHtml = epMatcher.group(1) ?: continue
                    val badgePsaMatcher = Pattern.compile("""<span[^>]*class=["']badge-psa["'][^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
                    val badgePsa = if (badgePsaMatcher.find()) badgePsaMatcher.group(1)?.trim() ?: "" else ""
                    val ftMatcher = Pattern.compile("""<div[^>]*class=["']episode-file-title["'][^>]*>([\s\S]*?)</div>""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
                    val fileTitle = if (ftMatcher.find()) ftMatcher.group(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: "" else ""
                    val matchesTargetEp = targetEpRegex.containsMatchIn(badgePsa) || targetEpRegex.containsMatchIn(fileTitle)
                    if (!matchesTargetEp && episode > 0) continue
                    val sizeMatcher = Pattern.compile("""<span[^>]*class=["']badge-size["'][^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
                    val sizeFormatted = if (sizeMatcher.find()) sizeMatcher.group(1)?.trim() else null

                    val linkMatcher = Pattern.compile("""<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
                    var hubCloudUrl: String? = null
                    var hubDriveUrl: String? = null
                    while (linkMatcher.find()) {
                        val href = linkMatcher.group(1) ?: continue
                        val text = linkMatcher.group(2)?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
                        if (text.contains("hubcloud", ignoreCase = true) || href.contains("hubcloud", ignoreCase = true)) {
                            hubCloudUrl = href
                        } else if (text.contains("hubdrive", ignoreCase = true) || href.contains("hubdrive", ignoreCase = true)) {
                            hubDriveUrl = href
                        }
                    }

                    val mirrors = mutableListOf<StreamMirror>()
                    if (hubCloudUrl != null) {
                        mirrors.add(StreamMirror(name = "Server 1 (PixelServer)", url = "$hubCloudUrl#server1", isLocker = true))
                        mirrors.add(StreamMirror(name = "Server 2 (10Gbps Google)", url = "$hubCloudUrl#server2", isLocker = true))
                    }
                    if (hubDriveUrl != null) {
                        mirrors.add(StreamMirror(name = "Server 3 (HubDrive Direct)", url = "$hubDriveUrl#server3", isLocker = true))
                    }
                    if (mirrors.isEmpty()) {
                        mirrors.addAll(extractMirrorsFromSnippet(epHtml))
                    }
                    if (mirrors.isEmpty()) continue

                    val is4k = Regex("""\b(2160p|4k|uhd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(formatTitle)
                    val is1080 = Regex("""\b(1080p|fhd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(formatTitle)
                    val is720 = Regex("""\b(720p|hd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(formatTitle)
                    val is480 = Regex("""\b(480p|sd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(formatTitle)

                    val category = when {
                        is4k -> "2160p / 4K"
                        is1080 -> "1080p"
                        is720 -> "720p"
                        is480 -> "480p"
                        else -> determineCategory(if (formatTitle.isNotBlank()) formatTitle else fileTitle, emptyList())
                    }

                    val quality = when {
                        is4k -> "4K 2160p UHD HDR"
                        formatTitle.contains("remux", ignoreCase = true) -> "1080p Remux AVC"
                        is1080 -> "1080p Full HD"
                        is720 -> "720p HD"
                        is480 -> "480p SD"
                        else -> "HD"
                    }

                    val isMkv = fileTitle.contains(".mkv", ignoreCase = true) || formatTitle.contains("mkv", ignoreCase = true)
                    val isHevc = formatTitle.contains("h265", ignoreCase = true) || formatTitle.contains("hevc", ignoreCase = true) || formatTitle.contains("x265", ignoreCase = true) || fileTitle.contains("h265", ignoreCase = true) || fileTitle.contains("hevc", ignoreCase = true) || fileTitle.contains("x265", ignoreCase = true)
                    val sourceTag = when {
                        formatTitle.contains("bluray", ignoreCase = true) -> "BluRay"
                        formatTitle.contains("web-dl", ignoreCase = true) -> "WEB-DL"
                        formatTitle.contains("webrip", ignoreCase = true) -> "WEBRip"
                        else -> "WEB-DL"
                    }

                    val epDisplayTitle = if (formatTitle.isNotBlank()) "$formatTitle • Episode $episode" else if (fileTitle.isNotBlank()) fileTitle else "Episode $episode"
                    val label = if (!sizeFormatted.isNullOrBlank()) "$epDisplayTitle • $sizeFormatted" else epDisplayTitle

                    options.add(
                        StreamSource(
                            quality = quality,
                            format = if (isMkv) "MKV" else "MP4",
                            url = mirrors.first().url,
                            headers = mapOf("User-Agent" to mobileUserAgent, "Referer" to pageReferer),
                            sizeBytes = parseSizeBytes(sizeFormatted),
                            directFile = true,
                            codec = if (isHevc) "HEVC" else "H264",
                            label = label,
                            provider = "fourkhdhub",
                            audioLangs = audioLangs,
                            sizeFormatted = sizeFormatted,
                            title = epDisplayTitle,
                            sourceQualityTag = sourceTag,
                            mirrorName = mirrors.first().name,
                            isLockerUrl = mirrors.first().isLocker,
                            category = category,
                            mirrors = mirrors
                        )
                    )
                }
            }
            if (options.isNotEmpty()) {
                val categoryRank = listOf("2160p / 4K", "1080p", "720p", "480p", "Other")
                return options.sortedWith(
                    compareBy<StreamSource> { s ->
                        val idx = categoryRank.indexOf(s.category)
                        if (idx >= 0) idx else 99
                    }.thenByDescending { it.codec == "HEVC" }
                    .thenByDescending { it.sizeBytes ?: 0L }
                )
            }



            val epPattern = Pattern.compile(



                """<div\s+class=["'][^"']*episode-download-item[^"']*["']([\s\S]*?)(?=<div\s+class=["'][^"']*episode-download-item|$|<footer)""",



                Pattern.CASE_INSENSITIVE



            )



            val epMatcher = epPattern.matcher(html)



            val epNumberStr = if (episode < 10) "0$episode" else "$episode"



            val epTokens = listOf("Episode-$epNumberStr", "Episode $epNumberStr", "Episode-$episode", "E$epNumberStr", "E$episode")



            while (epMatcher.find()) {



                val epHtml = epMatcher.group(1) ?: continue



                val matchesTargetEp = epTokens.any { epHtml.contains(it, ignoreCase = true) }



                if (!matchesTargetEp && episode > 0) continue



                val titleMatcher = Pattern.compile("""class=["']episode-file-title["'][^>]*>([\s\S]*?)</div>""", Pattern.CASE_INSENSITIVE).matcher(epHtml)



                val rawTitle = if (titleMatcher.find()) titleMatcher.group(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: "" else ""



                val title = rawTitle.ifBlank { "Episode $episode" }



                val sizeMatcher = Pattern.compile("""class=["']badge-size["'][^>]*>([^<]+)""", Pattern.CASE_INSENSITIVE).matcher(epHtml)



                val sizeFormatted = if (sizeMatcher.find()) sizeMatcher.group(1)?.trim() else null



                val mirrors = extractMirrorsFromSnippet(epHtml)



                if (mirrors.isEmpty()) continue



                val category = determineCategory(title, emptyList())



                val quality = when (category) {



                    "2160p / 4K" -> "4K 2160p UHD HDR"



                    "1080p" -> "1080p Full HD"



                    "720p" -> "720p HD"



                    "480p" -> "480p SD"



                    else -> "HD"



                }



                val isMkv = title.contains(".mkv", ignoreCase = true) || title.contains("remux", ignoreCase = true)



                val isHevc = title.contains("h265", ignoreCase = true) || title.contains("hevc", ignoreCase = true) || title.contains("x265", ignoreCase = true)



                options.add(



                    StreamSource(



                        quality = quality,



                        format = if (isMkv) "MKV" else "MP4",



                        url = mirrors.first().url,



                        headers = mapOf("User-Agent" to mobileUserAgent, "Referer" to pageReferer),



                        sizeBytes = parseSizeBytes(sizeFormatted),



                        directFile = true,



                        codec = if (isHevc) "HEVC" else "H264",



                        label = if (!sizeFormatted.isNullOrBlank()) "$title • $sizeFormatted" else title,



                        provider = "fourkhdhub",



                        audioLangs = "English",



                        sizeFormatted = sizeFormatted,



                        title = title,



                        sourceQualityTag = "WEB-DL",



                        mirrorName = mirrors.first().name,



                        isLockerUrl = mirrors.first().isLocker,



                        category = category,



                        mirrors = mirrors



                    )



                )



            }



            if (options.isNotEmpty()) return options



        }



        val itemPattern = Pattern.compile(

            """<div\s+class=["'][^"']*download-item[^"']*["']([\s\S]*?)(?=<div\s+class=["'][^"']*(?:download-item|info-box)|$|<footer)""",

            Pattern.CASE_INSENSITIVE

        )

        val matcher = itemPattern.matcher(html)



        val headerPattern = Pattern.compile("""download-header[\s\S]*?<div\s+class=["'][^"']*font-semibold[^"']*["']>([\s\S]*?)</div>""", Pattern.CASE_INSENSITIVE)

        val fileTitlePattern = Pattern.compile("""class=["']file-title["'][^>]*>([^<]+)""", Pattern.CASE_INSENSITIVE)

        val badgePattern = Pattern.compile("""class=["']badge["'][^>]*>([^<]+)</span>""", Pattern.CASE_INSENSITIVE)

        val sizeRegex = Regex("""\b(\d+(?:\.\d+)?\s*(?:GB|MB))\b""", RegexOption.IGNORE_CASE)



        while (matcher.find()) {

            val itemHtml = matcher.group(1) ?: continue



            var title = ""

            val hMatcher = headerPattern.matcher(itemHtml)

            if (hMatcher.find()) {

                val rawHeader = hMatcher.group(1) ?: ""

                val beforeBr = rawHeader.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).firstOrNull() ?: rawHeader

                title = beforeBr.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

            }



            val ftMatcher = fileTitlePattern.matcher(itemHtml)

            val fileTitle = if (ftMatcher.find()) ftMatcher.group(1)?.trim() ?: "" else ""

            if (title.isBlank() && fileTitle.isNotBlank()) {

                title = fileTitle

            }

            if (title.isBlank()) continue



            val badges = mutableListOf<String>()

            val bMatcher = badgePattern.matcher(itemHtml)

            while (bMatcher.find()) {

                val b = bMatcher.group(1)?.trim() ?: continue

                if (!b.contains("4khdhub", ignoreCase = true)) {

                    badges.add(b)

                }

            }



            val sizeFormatted = badges.firstOrNull { sizeRegex.containsMatchIn(it) }

                ?: (if (hMatcher.reset().find()) sizeRegex.find(hMatcher.group(1) ?: "")?.value else null)

                ?: sizeRegex.find(fileTitle)?.value



            val audioLangs = badges.firstOrNull {

                it.contains("hindi", ignoreCase = true) || it.contains("english", ignoreCase = true) ||

                it.contains("tamil", ignoreCase = true) || it.contains("telugu", ignoreCase = true) ||

                it.contains("dual", ignoreCase = true) || it.contains("multi", ignoreCase = true)

            } ?: "Dual Audio"



            val sourceTag = badges.firstOrNull {

                it.contains("bluray", ignoreCase = true) || it.contains("web-dl", ignoreCase = true) ||

                it.contains("remux", ignoreCase = true) || it.contains("webrip", ignoreCase = true)

            } ?: "BluRay"



            val mirrors = extractMirrorsFromSnippet(itemHtml)

            if (mirrors.isEmpty()) continue



            val category = determineCategory(title, badges)

            val quality = when (category) {

                "2160p / 4K" -> "4K 2160p UHD HDR"

                "1080p" -> "1080p Full HD"

                "720p" -> "720p HD"

                "480p" -> "480p SD"

                else -> "HD"

            }



            val isMkv = fileTitle.contains(".mkv", ignoreCase = true) || fileTitle.contains("mkv", ignoreCase = true) ||

                    title.contains(".mkv", ignoreCase = true) || title.contains("remux", ignoreCase = true) ||

                    title.contains("bluray", ignoreCase = true) || title.contains("hevc", ignoreCase = true) ||

                    !title.contains(".mp4", ignoreCase = true)

            val isHevc = title.contains("h265", ignoreCase = true) || title.contains("hevc", ignoreCase = true) ||

                    title.contains("x265", ignoreCase = true) || fileTitle.contains("h265", ignoreCase = true) ||

                    fileTitle.contains("hevc", ignoreCase = true) || fileTitle.contains("x265", ignoreCase = true)

            val sizeBytes = parseSizeBytes(sizeFormatted)



            options.add(

                StreamSource(

                    quality = quality,

                    format = if (isMkv) "MKV" else "MP4",

                    url = mirrors.first().url,

                    headers = mapOf("User-Agent" to mobileUserAgent, "Referer" to pageReferer),

                    sizeBytes = sizeBytes,

                    directFile = true,

                    codec = if (isHevc) "HEVC" else "H264",

                    label = if (!sizeFormatted.isNullOrBlank()) "$title • $sizeFormatted" else title,

                    provider = "fourkhdhub",

                    audioLangs = audioLangs,

                    sizeFormatted = sizeFormatted,

                    title = title,

                    sourceQualityTag = sourceTag,

                    mirrorName = mirrors.first().name,

                    isLockerUrl = mirrors.first().isLocker,

                    category = category,

                    mirrors = mirrors

                )

            )

        }



        val categoryOrder = listOf("2160p / 4K", "1080p", "720p", "480p", "Other")

        return options.sortedWith(

            compareBy<StreamSource> {

                val idx = categoryOrder.indexOf(it.category)

                if (idx >= 0) idx else 99

            }.thenByDescending {

                it.sizeBytes ?: 0L

            }

        )



    }



    private fun extractMirrorsFromSnippet(snippet: String): List<StreamMirror> {
        val linkPattern = Pattern.compile(
            """<a\s+[^>]*href=["'](https?://[^"']*(?:greenmotors|greenmountmotors|hubcdn|hubcloud|hubdrive|pixeldrain)[^"']*)["'][^>]*>([\s\S]*?)</a>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = linkPattern.matcher(snippet)
        val seenUrls = mutableSetOf<String>()
        var hubCloudUrl: String? = null
        var hubDriveUrl: String? = null
        val fallbackMirrors = mutableListOf<StreamMirror>()
        while (matcher.find()) {
            val url = matcher.group(1) ?: continue
            if (!seenUrls.add(url)) continue
            val rawText = matcher.group(2)?.replace(Regex("<[^>]+>"), " ")?.replace("&nbsp;", " ")?.trim() ?: ""
            if (rawText.contains("HubCloud", ignoreCase = true) || url.contains("hubcloud", ignoreCase = true) || url.contains("greenmotors", ignoreCase = true) || url.contains("greenmountmotors", ignoreCase = true)) {
                if (hubCloudUrl == null) hubCloudUrl = url
            }
            if (rawText.contains("HubDrive", ignoreCase = true) || url.contains("hubdrive", ignoreCase = true)) {
                if (hubDriveUrl == null) hubDriveUrl = url
            }
            val name = when {
                rawText.contains("HubDrive", ignoreCase = true) || url.contains("hubdrive", ignoreCase = true) -> "HubDrive"
                rawText.contains("HubCloud", ignoreCase = true) || url.contains("hubcloud", ignoreCase = true) -> "HubCloud (Fast CDN)"
                rawText.contains("HubCDN", ignoreCase = true) || url.contains("hubcdn", ignoreCase = true) -> "HubCDN Fast CDN"
                rawText.contains("PixelDrain", ignoreCase = true) || url.contains("pixeldrain", ignoreCase = true) -> "PixelDrain Direct"
                else -> "Fast Mirror"
            }
            val isLocker = !url.contains("pixeldrain.com")
            fallbackMirrors.add(StreamMirror(name = name, url = url, isLocker = isLocker))
        }
        val mirrors = mutableListOf<StreamMirror>()
        if (hubCloudUrl != null) {
            mirrors.add(StreamMirror(name = "Server 1 (PixelServer)", url = "$hubCloudUrl#server1", isLocker = true))
            mirrors.add(StreamMirror(name = "Server 2 (10Gbps Google)", url = "$hubCloudUrl#server2", isLocker = true))
        }
        if (hubDriveUrl != null) {
            mirrors.add(StreamMirror(name = "Server 3 (HubDrive Direct)", url = "$hubDriveUrl#server3", isLocker = true))
        }
        if (mirrors.isEmpty()) {
            mirrors.addAll(fallbackMirrors)
        }
        return mirrors
    }

    private fun determineCategory(title: String, badges: List<String>): String {



        val text = "$title ${badges.joinToString(" ")}"



        val is4k = Regex("""\b(2160p|4k|uhd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)



        val is1080 = Regex("""\b(1080p|fhd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)



        val is720 = Regex("""\b(720p|hd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)



        val is480 = Regex("""\b(480p|sd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)



        return when {



            is4k -> "2160p / 4K"



            is1080 -> "1080p"



            is720 -> "720p"



            is480 -> "480p"



            else -> "Other"



        }



    }



    private fun parseSizeBytes(sizeFormatted: String?): Long? {



        if (sizeFormatted.isNullOrBlank()) return null



        return try {



            val clean = sizeFormatted.trim().uppercase()



            val num = clean.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return null



            if (clean.contains("GB")) {



                (num * 1024 * 1024 * 1024).toLong()



            } else if (clean.contains("MB")) {



                (num * 1024 * 1024).toLong()



            } else {



                null



            }



        } catch (_: Exception) {



            null



        }



    }



    suspend fun resolveLockerUrl(url: String, referer: String = baseUrl): StreamSource? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            val serverTarget = when {
                cleanUrl.contains("#server1") -> "server1"
                cleanUrl.contains("#server2") -> "server2"
                cleanUrl.contains("#server3") -> "server3"
                else -> "auto"
            }
            val strippedUrl = cleanUrl.substringBefore("#").trim()

            if (cleanUrl.contains("pixeldrain.com") || cleanUrl.contains("pixeldrain.dev")) {
                val idMatch = Regex("""pixeldrain\.(?:com|dev)/(?:u|api/file)/([a-zA-Z0-9_-]+)""").find(cleanUrl)
                val fileId = idMatch?.groupValues?.get(1)
                if (fileId != null) {
                    val pdUrl = "https://pixeldrain.com/api/file/$fileId?download"
                    val isPdLive = StreamHealthValidator.isStreamableMediaUrl(pdUrl)
                    return@withContext StreamSource(
                        quality = "1080p Full HD",
                        format = "MKV",
                        url = pdUrl,
                        headers = mapOf("User-Agent" to mobileUserAgent),
                        directFile = true,
                        codec = "HEVC",
                        label = "Server 1 (PixelServer)",
                        provider = "fourkhdhub",
                        isLockerUrl = false,
                        isOnline = isPdLive
                    )
                }
            }

            if (strippedUrl.contains("hblinks.")) {
                val resolved = resolveHblinks(strippedUrl, referer)
                if (resolved.isNotEmpty()) return@withContext resolved.first()
            }

            if (strippedUrl.contains("pixel.hubcloud.")) {
                val direct = resolvePixelHubCloud(strippedUrl, referer)
                if (direct != null) {
                    val sanitized = direct.replace("&amp;", "&")
                    return@withContext StreamSource(
                        quality = "1080p Full HD",
                        format = if (sanitized.contains(".mkv")) "MKV" else "MP4",
                        url = sanitized,
                        headers = mapOf("User-Agent" to mobileUserAgent),
                        directFile = true,
                        codec = "HEVC",
                        label = "Google High-Speed Direct",
                        provider = "fourkhdhub",
                        isLockerUrl = false
                    )
                }
            }

            if (strippedUrl.contains("greenmotors") || strippedUrl.contains("greenmountmotors")) {
                val resolved = resolveGreenMotorsLocker(strippedUrl, referer, serverTarget)
                if (resolved.isNotEmpty()) return@withContext resolved.first()
            }

            if (strippedUrl.contains("hubcdn.")) {
                val resolved = resolveHubCdnLocker(strippedUrl, referer)
                if (resolved.isNotEmpty()) return@withContext resolved.first()
            }

            if (strippedUrl.contains("hubcloud.")) {
                val resolved = resolveHubCloudLocker(strippedUrl, referer, serverTarget)
                if (resolved.isNotEmpty()) return@withContext resolved.first()
            }

            if (strippedUrl.contains("hubdrive.")) {
                val resolved = resolveHubDriveLocker(strippedUrl, referer, serverTarget)
                if (resolved.isNotEmpty()) return@withContext resolved.first()
            }

            val isKnownLocker = strippedUrl.contains("greenmotors") ||
                    strippedUrl.contains("greenmountmotors") ||
                    strippedUrl.contains("hubcdn.") ||
                    strippedUrl.contains("hubcloud.") ||
                    strippedUrl.contains("hubdrive.") ||
                    strippedUrl.contains("gamerxyt.com") ||
                    strippedUrl.contains("hblinks.")

            if (!isKnownLocker && (strippedUrl.startsWith("http://") || strippedUrl.startsWith("https://"))) {
                return@withContext StreamSource(
                    quality = "HD",
                    format = if (strippedUrl.contains(".mkv")) "MKV" else "MP4",
                    url = strippedUrl.replace("&amp;", "&"),
                    headers = mapOf("User-Agent" to mobileUserAgent),
                    directFile = true,
                    codec = "H264",
                    label = "Direct Stream",
                    provider = "fourkhdhub",
                    isLockerUrl = false
                )
            }
        } catch (_: Exception) {}
        null
    }

    private suspend fun extractStreamsFromDetailHtml(html: String, pageReferer: String): List<StreamSource> {



        val streams = mutableListOf<StreamSource>()



        val seenUrls = mutableSetOf<String>()



        val pixelDrainRegex = Regex("""https?://pixeldrain\.com/(?:u|api/file)/([a-zA-Z0-9_-]+)""")



        for (match in pixelDrainRegex.findAll(html)) {



            val fileId = match.groupValues[1]



            val directUrl = "https://pixeldrain.com/api/file/$fileId?download"
            if (seenUrls.add(directUrl)) {
                val isLive = StreamHealthValidator.isStreamableMediaUrl(directUrl)
                streams.add(
                    StreamSource(
                        quality = "1080p Full HD",
                        format = "MKV",
                        url = directUrl,
                        headers = mapOf("User-Agent" to mobileUserAgent),
                        directFile = true,
                        codec = "HEVC",
                        label = "4KHDHub PixelDrain Direct (1080p)",
                        provider = "fourkhdhub",
                        isOnline = isLive
                    )
                )
            }



        }



        val greenMotorsRegex = Regex("""href=["'](https?://[^"']*(?:greenmotors|greenmountmotors)[^"']*)["']""")



        val greenMotorsLinks = greenMotorsRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()



        for (link in greenMotorsLinks.take(6)) {



            try {



                val resolved = resolveGreenMotorsLocker(link, pageReferer)



                for (stream in resolved) {



                    if (seenUrls.add(stream.url)) {



                        streams.add(stream)



                    }



                }



            } catch (_: Exception) {}



        }



        val hubCloudRegex = Regex("""href=["'](https?://[^"']*(?:hubcloud|hubdrive)[^"']*)["']""")



        val hubCloudLinks = hubCloudRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()



        for (link in hubCloudLinks.take(4)) {



            try {



                val resolved = resolveHubCloudLocker(link, pageReferer)



                for (stream in resolved) {



                    if (seenUrls.add(stream.url)) {



                        streams.add(stream)



                    }



                }



            } catch (_: Exception) {}



        }



        return streams



    }



    private fun rot13(input: String): String {



        val sb = StringBuilder()



        for (c in input) {



            when (c) {



                in 'a'..'m', in 'A'..'M' -> sb.append((c.code + 13).toChar())



                in 'n'..'z', in 'N'..'Z' -> sb.append((c.code - 13).toChar())



                else -> sb.append(c)



            }



        }



        return sb.toString()



    }



    private fun extractGreenMotorsPayload(html: String): String? {



        val needle = "s("



        var searchIdx = 0



        while (true) {



            val pos = html.indexOf(needle, searchIdx)



            if (pos < 0) break



            val absPos = pos + needle.length



            val rest = html.substring(absPos).trimStart()



            val afterKey = when {



                rest.startsWith("'o'") -> rest.removePrefix("'o'")



                rest.startsWith("\"o\"") -> rest.removePrefix("\"o\"")



                else -> {



                    searchIdx = absPos



                    continue



                }



            }



            val trimmedAfterKey = afterKey.trimStart()



            if (!trimmedAfterKey.startsWith(",")) {



                searchIdx = absPos



                continue



            }



            val trimmedAfterComma = trimmedAfterKey.removePrefix(",").trimStart()



            val quote = trimmedAfterComma.firstOrNull()



            if (quote != '\'' && quote != '"') {



                searchIdx = absPos



                continue



            }



            val payloadSlice = trimmedAfterComma.substring(1)



            val end = payloadSlice.indexOf(quote)



            if (end >= 0) {



                return payloadSlice.substring(0, end)



            }



            searchIdx = absPos



        }



        return null



    }



    private fun safeBase64Decode(str: String): ByteArray {
        val clean = str.trim().replace("\n", "").replace("\r", "")
        val padLen = (4 - (clean.length % 4)) % 4
        val padded = if (padLen in 1..3) clean + "=".repeat(padLen) else clean
        return try {
            JavaBase64.getDecoder().decode(padded)
        } catch (_: Exception) {
            try {
                JavaBase64.getUrlDecoder().decode(padded)
            } catch (_: Exception) {
                Base64.decode(padded, Base64.DEFAULT)
            }
        }
    }

    private fun safeBase64DecodeToString(str: String): String {
        return try {
            String(safeBase64Decode(str), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun decodeGreenMotorsPayload(payload: String): String? {
        return try {
            val step1Str = safeBase64DecodeToString(payload)
            val step2Str = safeBase64DecodeToString(step1Str)
            val step3Rot = rot13(step2Str)
            val step4Str = safeBase64DecodeToString(step3Rot)
            val json = JsonParser.parseString(step4Str).asJsonObject
            val targetB64 = json.get("o")?.asString ?: return null
            safeBase64DecodeToString(targetB64)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveHubCdnLocker(fileUrl: String, referer: String): List<StreamSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StreamSource>()
        try {
            val req = Request.Builder()
                .url(fileUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val html = resp.body?.string() ?: return@withContext emptyList()

            val rMatch = Regex("""[?&]r=([a-zA-Z0-9+/=_-]{20,})""").find(html)
                ?: Regex("""[?&]r=([a-zA-Z0-9+/=_-]{20,})""").find(fileUrl)
            if (rMatch != null) {
                val b64 = rMatch.groupValues[1]
                val decoded = safeBase64DecodeToString(b64)
                val linkMatch = Regex("""[?&]link=([^&"'\s]+)""").find(decoded)
                val directUrl = if (linkMatch != null) {
                    URLDecoder.decode(linkMatch.groupValues[1], "UTF-8").replace("&amp;", "&")
                } else if (decoded.startsWith("http")) {
                    decoded.replace("&amp;", "&")
                } else null

                if (!directUrl.isNullOrBlank()) {
                    val isMkv = directUrl.contains(".mkv", ignoreCase = true)
                    val is4k = directUrl.contains("2160p", ignoreCase = true) || directUrl.contains("4k", ignoreCase = true)
                    results.add(
                        StreamSource(
                            quality = if (is4k) "4K 2160p UHD HDR" else "1080p Full HD",
                            format = if (isMkv) "MKV" else "MP4",
                            url = directUrl,
                            headers = mapOf("User-Agent" to mobileUserAgent),
                            directFile = true,
                            codec = if (is4k) "HEVC" else "x264",
                            label = "HubCDN Fast CDN Direct",
                            provider = "fourkhdhub",
                            isLockerUrl = false
                        )
                    )
                }
            }

            val r2Match = Regex("""href=["'](https?://[^"']*(?:\.r2\.dev|\.r2\.cloudflarestorage\.com)[^"']*)["']""").find(html)
            if (r2Match != null) {
                val direct = r2Match.groupValues[1].replace("&amp;", "&")
                if (results.none { it.url == direct }) {
                    results.add(
                        StreamSource(
                            quality = "1080p Full HD",
                            format = "MKV",
                            url = direct,
                            headers = mapOf("User-Agent" to mobileUserAgent),
                            directFile = true,
                            codec = "HEVC",
                            label = "HubCDN Direct R2",
                            provider = "fourkhdhub",
                            isLockerUrl = false
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        results
    }

    private suspend fun resolveGreenMotorsLocker(link: String, referer: String, serverTarget: String = "auto"): List<StreamSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StreamSource>()
        try {
            val req = Request.Builder()
                .url(link)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()

            val payload = extractGreenMotorsPayload(body)
            val targetUrl = payload?.let { decodeGreenMotorsPayload(it) }
            if (!targetUrl.isNullOrBlank() && targetUrl.startsWith("http")) {
                if (targetUrl.contains("hblinks.")) {
                    return@withContext resolveHblinks(targetUrl, link)
                } else if (targetUrl.contains("hubcloud.")) {
                    return@withContext resolveHubCloudLocker(targetUrl, link, serverTarget)
                } else if (targetUrl.contains("hubdrive.")) {
                    return@withContext resolveHubDriveLocker(targetUrl, link, serverTarget)
                } else if (targetUrl.contains("hubcdn.")) {
                    return@withContext resolveHubCdnLocker(targetUrl, link)
                } else if (targetUrl.contains("pixeldrain.")) {
                    val fileId = targetUrl.substringAfterLast("/")
                    if (fileId.isNotBlank()) {
                        results.add(
                            StreamSource(
                                quality = "1080p Full HD",
                                format = "MKV",
                                url = "https://pixeldrain.com/api/file/$fileId?download",
                                headers = mapOf("User-Agent" to mobileUserAgent),
                                directFile = true,
                                codec = "HEVC",
                                label = "Server 1 (PixelServer)",
                                provider = "fourkhdhub",
                                isLockerUrl = false
                            )
                        )
                        return@withContext results
                    }
                } else {
                    results.add(
                        StreamSource(
                            quality = "1080p Full HD",
                            format = if (targetUrl.contains(".mkv")) "MKV" else "MP4",
                            url = targetUrl.replace("&amp;", "&"),
                            headers = mapOf("User-Agent" to mobileUserAgent, "Referer" to link),
                            directFile = true,
                            codec = "HEVC",
                            label = "Direct Stream",
                            provider = "fourkhdhub",
                            isLockerUrl = false
                        )
                    )
                    return@withContext results
                }
            }
        } catch (_: Exception) {}
        results
    }

    private suspend fun resolveHubDriveLocker(hubDriveUrl: String, referer: String, serverTarget: String = "auto"): List<StreamSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StreamSource>()
        try {
            val req = Request.Builder()
                .url(hubDriveUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()

            val downId = Regex("""id=["']down-id["'][^>]*>([^<]+)<""").find(body)?.groupValues?.get(1)?.trim()
                ?: Regex("""/file/(\d+)""").find(hubDriveUrl)?.groupValues?.get(1)

            if (!downId.isNullOrBlank()) {
                try {
                    val formBody = FormBody.Builder()
                        .add("id", downId)
                        .build()
                    val ajaxReq = Request.Builder()
                        .url("https://hubdrive.blog/ajax.php?ajax=direct-download")
                        .post(formBody)
                        .header("User-Agent", mobileUserAgent)
                        .header("Referer", hubDriveUrl)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Cookie", "xla=s4t")
                        .build()
                    val ajaxResp = client.newCall(ajaxReq).execute()
                    if (ajaxResp.isSuccessful) {
                        val ajaxJson = ajaxResp.body?.string() ?: ""
                        val jsonObj = JsonParser.parseString(ajaxJson).asJsonObject
                        if (jsonObj.has("code") && jsonObj.get("code").asInt == 200) {
                            val dataObj = jsonObj.getAsJsonObject("data")
                            val gdUrl = dataObj?.get("gd")?.asString
                            if (!gdUrl.isNullOrBlank() && (gdUrl.startsWith("http://") || gdUrl.startsWith("https://"))) {
                                results.add(
                                    StreamSource(
                                        quality = "1080p Full HD",
                                        format = "MKV",
                                        url = gdUrl,
                                        headers = mapOf("User-Agent" to mobileUserAgent),
                                        directFile = true,
                                        codec = "HEVC",
                                        label = "Server 3 (HubDrive Instant Direct)",
                                        provider = "fourkhdhub",
                                        isLockerUrl = false
                                    )
                                )
                                return@withContext results
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val hubCloudMatch = Regex("""href=["'](https?://[^"'\s]*(?:hubcloud|hubdrive)\.[^"'\s]*/(?:drive|file|d)/[^"'\s]*)["']""").find(body)
                ?: Regex("""href=["'](https?://[^"'\s]*hubcloud\.[^"'\s]+/[^"'\s]*)["']""").find(body)
            if (hubCloudMatch != null) {
                results.addAll(resolveHubCloudLocker(hubCloudMatch.groupValues[1], hubDriveUrl, serverTarget))
                if (results.isNotEmpty()) return@withContext results
            }

            val pdMatches = Regex("""https?://pixeldrain\.(?:com|dev)/(?:u|api/file)/([a-zA-Z0-9_-]+)""").findAll(body)
            for (m in pdMatches) {
                val fileId = m.groupValues[1]
                val pdUrl = "https://pixeldrain.com/api/file/$fileId?download"
                if (results.none { it.url == pdUrl }) {
                    results.add(
                        StreamSource(
                            quality = "1080p Full HD",
                            format = "MKV",
                            url = pdUrl,
                            headers = mapOf("User-Agent" to mobileUserAgent),
                            directFile = true,
                            codec = "HEVC",
                            label = "Server 3 (HubDrive PixelServer)",
                            provider = "fourkhdhub",
                            isLockerUrl = false
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        results
    }

    private suspend fun resolveHubCloudLocker(hubUrl: String, referer: String, serverTarget: String = "auto"): List<StreamSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StreamSource>()
        try {
            val req = Request.Builder()
                .url(hubUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()

            val is4k = body.contains("2160p") || body.contains("4K") || body.contains("UHD")
            val qualityLabel = if (is4k) "4K 2160p UHD HDR" else "1080p Full HD"

            val resolverBtn = Regex("""<a[^>]+href=["'](https?://[^"'\s]*(?:gamerxyt|hubcloud\.php)[^"'\s]*)["']""").find(body)
                ?: Regex("""<a[^>]+id=["']download["'][^>]+href=["']([^"']+)["']""").find(body)
                ?: Regex("""<a[^>]+class=["'][^"']*btn-primary[^"']*["'][^>]+href=["']([^"']+)["']""").find(body)

            val resolverUrl = resolverBtn?.groupValues?.get(1)
            var resBody = ""
            if (!resolverUrl.isNullOrBlank() && resolverUrl.startsWith("http")) {
                val resReq = Request.Builder()
                    .url(resolverUrl)
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", hubUrl)
                    .build()
                val resResp = client.newCall(resReq).execute()
                if (resResp.isSuccessful) {
                    resBody = resResp.body?.string() ?: ""
                }
            }

            val searchBody = if (resBody.isNotBlank()) resBody else body

            var pixelSource: StreamSource? = null
            val pdMatch = Regex("""https?://pixeldrain\.(?:com|dev)/(?:u|api/file)/([a-zA-Z0-9_-]+)""").find(searchBody)
            if (pdMatch != null) {
                val fileId = pdMatch.groupValues[1]
                pixelSource = StreamSource(
                    quality = qualityLabel,
                    format = "MKV",
                    url = "https://pixeldrain.com/api/file/$fileId?download",
                    headers = mapOf("User-Agent" to mobileUserAgent),
                    directFile = true,
                    codec = if (is4k) "HEVC" else "x264",
                    label = "Server 1 (PixelServer)",
                    provider = "fourkhdhub",
                    isLockerUrl = false
                )
            }

            var gpdlSource: StreamSource? = null
            val gpdlMatch = Regex("""href=["'](https?://[^"'\s]*gpdl\.[^"'\s]+/[^"'\s]*)["']""").find(searchBody)
            if (gpdlMatch != null) {
                val gpdlUrl = gpdlMatch.groupValues[1]
                try {
                    val gpdlReq = Request.Builder()
                        .url(gpdlUrl)
                        .header("User-Agent", mobileUserAgent)
                        .header("Referer", resolverUrl ?: hubUrl)
                        .build()
                    val gpdlResp = client.newCall(gpdlReq).execute()
                    val finalHttpUrl = gpdlResp.request.url
                    val finalUrlStr = finalHttpUrl.toString()
                    val linkParam = finalHttpUrl.queryParameter("link")
                    val directGoogleUrl = if (!linkParam.isNullOrBlank() && (linkParam.startsWith("http://") || linkParam.startsWith("https://"))) {
                        linkParam
                    } else if (finalUrlStr.contains("video-downloads.googleusercontent.com")) {
                        finalUrlStr
                    } else null

                    if (!directGoogleUrl.isNullOrBlank()) {
                        gpdlSource = StreamSource(
                            quality = qualityLabel,
                            format = "MKV",
                            url = directGoogleUrl,
                            headers = mapOf("User-Agent" to mobileUserAgent),
                            directFile = true,
                            codec = if (is4k) "HEVC" else "x264",
                            label = "Server 2 (10Gbps Google Direct)",
                            provider = "fourkhdhub",
                            isLockerUrl = false
                        )
                    }
                } catch (_: Exception) {}
            }

            when (serverTarget) {
                "server1" -> {
                    if (pixelSource != null) results.add(pixelSource)
                    else if (gpdlSource != null) results.add(gpdlSource)
                }
                "server2" -> {
                    if (gpdlSource != null) results.add(gpdlSource)
                    else if (pixelSource != null) results.add(pixelSource)
                }
                else -> {
                    if (pixelSource != null) results.add(pixelSource)
                    if (gpdlSource != null) results.add(gpdlSource)
                }
            }

            if (results.isEmpty()) {
                val r2Matches = Regex("""href=["'](https?://[^"'\s]*(?:\.r2\.cloudflarestorage\.com|\.r2\.dev)/[^"'\s]+)["']""").findAll(searchBody)
                for (r2 in r2Matches) {
                    val r2Url = r2.groupValues[1].replace("&amp;", "&")
                    val isMkv = r2Url.contains(".mkv", ignoreCase = true) || !r2Url.contains(".mp4", ignoreCase = true)
                    results.add(
                        StreamSource(
                            quality = qualityLabel,
                            format = if (isMkv) "MKV" else "MP4",
                            url = r2Url,
                            headers = mapOf("User-Agent" to mobileUserAgent),
                            directFile = true,
                            codec = if (is4k) "HEVC" else "x264",
                            label = "4KHDHub Direct Cloud ($qualityLabel)",
                            provider = "fourkhdhub",
                            isLockerUrl = false
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        results
    }

    private suspend fun resolveHblinks(hblinksUrl: String, referer: String): List<StreamSource> = withContext(Dispatchers.IO) {



        val results = mutableListOf<StreamSource>()



        try {



            val req = Request.Builder()



                .url(hblinksUrl)



                .header("User-Agent", mobileUserAgent)



                .header("Referer", referer)



                .build()



            val resp = client.newCall(req).execute()



            if (!resp.isSuccessful) return@withContext emptyList()



            val html = resp.body?.string() ?: return@withContext emptyList()



            val anchors = Regex("""href=["'](https?://[^"'\s]+)["']""").findAll(html)



            for (m in anchors) {



                val href = m.groupValues[1]



                if (href.contains("hubcloud.")) {



                    results.addAll(resolveHubCloudLocker(href, hblinksUrl))



                } else if (href.contains("hubdrive.")) {



                    results.addAll(resolveHubDriveLocker(href, hblinksUrl))



                }



            }



        } catch (_: Exception) {}



        results



    }



    private fun resolvePixelHubCloud(pixelUrl: String, referer: String): String? {



        try {



            val req = Request.Builder()



                .url(pixelUrl)



                .header("User-Agent", mobileUserAgent)



                .header("Referer", referer)



                .build()



            val resp = client.newCall(req).execute()



            val finalUrl = resp.request.url.toString()



            val linkParam = Regex("""[?&]link=([^&]+)""").find(finalUrl)?.groupValues?.get(1)



            if (!linkParam.isNullOrBlank()) {



                return java.net.URLDecoder.decode(linkParam, "UTF-8")



            }



            if (finalUrl.contains(".mkv", ignoreCase = true) || finalUrl.contains(".mp4", ignoreCase = true) || finalUrl.contains("googleusercontent.com")) {



                return finalUrl



            }



        } catch (_: Exception) {}



        return null



    }



    fun launchExternalPlayer(context: Context, stream: StreamSource, title: String) {



        val intent = Intent(Intent.ACTION_VIEW).apply {



            val mime = if (stream.format.equals("MKV", ignoreCase = true) || stream.url.contains(".mkv")) {



                "video/x-matroska"



            } else {



                "video/mp4"



            }



            setDataAndType(Uri.parse(stream.url), mime)



            putExtra("title", title)



            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)



            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)



        }



        val chooser = Intent.createChooser(intent, "Play with external player (VLC / Nova)").apply {



            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)



        }



        context.startActivity(chooser)



    }



}