package com.sumanonline.sumanmovies.util

import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import kotlin.math.abs

object MediaMatcher {
    private val stopWords = setOf(
        "the", "a", "an", "and", "of", "in", "to", "for", "with", "on", "at", "by", "from", "s"
    )

    private val releaseTags = setOf(
        "4k", "2160p", "1080p", "720p", "480p", "360p", "ds4k", "uhd", "fhd", "hd", "sd",
        "web", "dl", "webdl", "webrip", "bluray", "brrip", "hdrip", "dvdrip", "remux",
        "x264", "x265", "h264", "h265", "hevc", "avc", "av1", "10bit", "8bit", "bit",
        "hdr", "hdr10", "hdr10plus", "dv", "dovi", "dolbyvision",
        "dd5", "dd51", "dd2", "dd20", "ddp", "ddp51", "ddp20", "ddp71", "atmos", "dolby",
        "stereo", "surround", "digital", "plus", "aac", "ac3", "dts", "truehd",
        "hindi", "english", "korean", "tamil", "telugu", "malayalam", "kannada", "bengali",
        "marathi", "punjabi", "gujarati", "urdu", "japanese", "chinese", "mandarin", "cantonese",
        "spanish", "french", "german", "italian", "russian", "thai", "vietnamese", "indonesian",
        "turkish", "arabic", "dual", "audio", "multi", "multiaudio", "dualaudio", "org", "clean",
        "dub", "dubbed",
        "primevideo", "prime", "netflix", "nf", "hotstar", "disney", "hulu", "apple", "appletv",
        "appletvplus", "zee5", "sonyliv", "jiocinema", "jio", "mxplayer", "voot", "altbalaji",
        "lionsgate", "paramount", "peacock", "hbo", "max", "crunchyroll", "aha", "hoichoi",
        "ep", "eps", "episode", "episodes", "all", "complete", "pack", "added", "vol", "volume",
        "esub", "esubs", "msub", "msubs", "sub", "subs", "subtitles", "movie", "series",
        "tv", "download", "watch", "online", "rip", "extended", "unrated", "part", "proper"
    )

    fun normalizeForMatching(text: String): String {
        return text.lowercase()
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
            .replace(Regex("""(?i)\(season\s*\d+\)"""), " ")
            .replace(Regex("""(?i)\[season\s*\d+\]"""), " ")
            .replace(Regex("""(?i)\bseason\s*\d+\b"""), " ")
            .replace(Regex("""(?i)\[?\s*ep\s*[-–]?\s*\d+\s*(?:added)?\s*\]?"""), " ")
            .replace(Regex("""(?i)\[?\s*all\s+episodes\s*\]?"""), " ")
            .replace(Regex("""(?i)\[\s*(?:nf|primevideo|netflix|hotstar|disney|zee5|sonyliv)\s+series\s*\]"""), " ")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun buildSearchQueries(title: String): List<String> {
        val cleanFull = title
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
            .trim()

        val noPunctuation = cleanFull
            .replace(Regex("""[:\-/]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val baseOnly = cleanFull
            .replace(Regex("""[:\-/].*"""), "")
            .trim()

        val list = mutableListOf<String>()
        if (cleanFull.isNotBlank()) list.add(cleanFull)
        if (noPunctuation.isNotBlank() && noPunctuation != cleanFull) list.add(noPunctuation)
        if (baseOnly.isNotBlank() && baseOnly != cleanFull && baseOnly != noPunctuation) list.add(baseOnly)

        val firstWord = cleanFull.split(" ").firstOrNull()?.trim()
        if (!firstWord.isNullOrBlank() && firstWord.length >= 4 && firstWord != cleanFull && firstWord != baseOnly) {
            list.add(firstWord)
        }

        return list
    }

    fun scoreCandidate(
        candidate: MediaItem,
        targetTitle: String,
        targetYear: String? = null,
        season: Int = 0,
        isSeries: Boolean = false
    ): Int {
        val normTarget = normalizeForMatching(targetTitle)
        val normCandidate = normalizeForMatching(candidate.title)
        if (normTarget.isBlank() || normCandidate.isBlank()) return 0

        val targetTokens = normTarget.split(" ")
            .filter { it.isNotBlank() && it !in stopWords && !it.all { ch -> ch.isDigit() } }
            .toSet()
        val candidateTokens = normCandidate.split(" ")
            .filter { it.isNotBlank() && it !in stopWords && it !in releaseTags && !it.all { ch -> ch.isDigit() } }
            .toSet()

        val isHdHub = candidate.id.startsWith("hdhub4u:", ignoreCase = true) ||
                candidate.id.startsWith("fourkhdhub:", ignoreCase = true) ||
                candidate.id.startsWith("4khdhub:", ignoreCase = true) ||
                candidate.provider.contains("hdhub", ignoreCase = true)

        if (targetTokens.isNotEmpty()) {
            val commonTokens = targetTokens.intersect(candidateTokens)
            if (commonTokens.isEmpty() && !normCandidate.contains(normTarget) && !normTarget.contains(normCandidate)) {
                return 0
            }
            val overlapRatio = commonTokens.size.toDouble() / targetTokens.size.toDouble()
            if (overlapRatio < 0.5 && !normCandidate.contains(normTarget)) {
                return 0
            }
        } else {
            if (normTarget != normCandidate && !normCandidate.contains(normTarget) && !normTarget.contains(normCandidate)) {
                return 0
            }
        }

        var score = 0

        if (normTarget == normCandidate) {
            score += 1000
        } else {
            val commonTokens = targetTokens.intersect(candidateTokens)
            val missingFromCandidate = targetTokens - candidateTokens
            val extraInCandidate = candidateTokens - targetTokens

            if (missingFromCandidate.isNotEmpty()) {
                score -= missingFromCandidate.size * 250
            } else {
                score += 400
                if (isHdHub) {
                    score += 400
                }
            }

            if (missingFromCandidate.isEmpty()) {
                if (!isHdHub) {
                    score -= minOf(extraInCandidate.size * 30, 150)
                }
            } else {
                if (isHdHub) {
                    score -= minOf(extraInCandidate.size * 25, 100)
                } else {
                    score -= extraInCandidate.size * 150
                }
            }

            score += commonTokens.size * 100

            if (normCandidate.contains(normTarget)) {
                score += 250
            } else if (normTarget.contains(normCandidate)) {
                score += 50
            }
        }

        val parsedTargetYear = targetYear?.toIntOrNull()
        val parsedCandidateYear = candidate.year?.toIntOrNull()
            ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(candidate.title)?.value?.toIntOrNull()

        if (parsedTargetYear != null && parsedCandidateYear != null) {
            val diff = abs(parsedTargetYear - parsedCandidateYear)
            when (diff) {
                0 -> score += 500
                1 -> score += 150
                else -> {
                    if (isHdHub && normCandidate.contains(normTarget)) {
                        score -= 200
                    } else {
                        score -= 600
                    }
                }
            }
        }

        val candidateIsSeries = candidate.mediaType == MediaType.SERIES ||
            candidate.title.contains("Season", ignoreCase = true) ||
            candidate.title.contains("S0", ignoreCase = true)

        if (isSeries || season > 0) {
            if (candidateIsSeries) {
                score += 200
            } else {
                score -= 400
            }
            if (season > 0) {
                val seasonRegex = Regex("""\b(?:Season\s*0?|S0?)$season\b""", RegexOption.IGNORE_CASE)
                if (seasonRegex.containsMatchIn(candidate.title)) {
                    score += 400
                }
            }
        } else {
            if (!candidateIsSeries) {
                score += 200
            } else {
                score -= 400
            }
        }

        return score
    }

    const val MIN_MATCH_SCORE = 300

    fun findBestMatch(
        candidates: List<MediaItem>,
        targetTitle: String,
        targetYear: String? = null,
        season: Int = 0,
        isSeries: Boolean = false
    ): MediaItem? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) {
            val single = candidates.first()
            val sc = scoreCandidate(single, targetTitle, targetYear, season, isSeries)
            return if (sc >= MIN_MATCH_SCORE) single else null
        }

        val scored = candidates.map { candidate ->
            candidate to scoreCandidate(candidate, targetTitle, targetYear, season, isSeries)
        }

        val sorted = scored.sortedByDescending { it.second }
        val best = sorted.firstOrNull()

        return if (best != null && best.second >= MIN_MATCH_SCORE) {
            best.first
        } else {
            null
        }
    }
}
