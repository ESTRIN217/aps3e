package aenu.aps3e.data

import aenu.aps3e.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder


data class GameCoverRequest(
    val title: String,
    val serial: String
)

data class CoverRefreshResult(
    val scrapedCount: Int,
    val failedCount: Int
)

class CoverRepository(private val filesDir: File) {
    private val client = OkHttpClient()
    private val coversDir = File(filesDir, "covers").apply { mkdirs() }
    private val descriptionCache = mutableMapOf<String, String>()

    suspend fun getCoverPath(serial: String): String? = withContext(Dispatchers.IO) {
        val file = File(coversDir, "${serial}_cover.jpg")
        if (file.exists()) file.absolutePath else null
    }

    suspend fun refreshCovers(games: List<GameCoverRequest>): CoverRefreshResult = withContext(Dispatchers.IO) {
        var scraped = 0
        var failed = 0

        games.forEach { game ->
            val current = File(coversDir, "${game.serial}_cover.jpg")
            if (current.exists()) return@forEach

            val url = findCoverUrl(game.title)
            if (url == null) {
                failed += 1
                return@forEach
            }

            val bytes = downloadImage(url)
            if (bytes == null) {
                failed += 1
                return@forEach
            }

            if (saveCover(game.serial, bytes) != null) {
                scraped += 1
            } else {
                failed += 1
            }
        }

        CoverRefreshResult(scraped, failed)
    }

    suspend fun clearCovers() = withContext(Dispatchers.IO) {
        val files = coversDir.listFiles() ?: return@withContext
        files.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
    }

    private suspend fun findCoverUrl(gameTitle: String): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TGDB_API_KEY
        if (apiKey.isBlank()) return@withContext null
        val candidates = buildSearchCandidates(gameTitle)
        for (candidate in candidates) {
            val encodedTitle = URLEncoder.encode(candidate, "UTF-8")
            val searchUrl = "https://api.thegamesdb.net/v1/Games/ByGameName?apikey=$apiKey&name=$encodedTitle&filter[platform]=12"
            val searchJson = getJson(searchUrl) ?: continue

            val searchObj = JSONObject(searchJson)
            val games = searchObj.optJSONObject("data")?.optJSONArray("games") ?: continue
            if (games.length() == 0) continue

            val gameId = pickBestGameId(games, candidate) ?: continue

            val imagesUrl = "https://api.thegamesdb.net/v1/Games/Images?apikey=$apiKey&games_id=$gameId"
            val imagesJson = getJson(imagesUrl) ?: continue
            val imagesObj = JSONObject(imagesJson)
            val baseUrl = imagesObj.optJSONObject("data")
                ?.optJSONObject("base_url")
                ?.optString("original")
                ?.replace("\\/", "/")
                ?: ""

            if (baseUrl.isBlank()) continue

            val images = imagesObj.optJSONObject("data")
                ?.optJSONObject("images")
                ?.optJSONArray(gameId.toString())
                ?: continue

            val best = pickBestCoverImage(images)
            if (best != null) {
                return@withContext baseUrl + best
            }
        }
        null
    }

    suspend fun getGameDescription(gameTitle: String): String? = withContext(Dispatchers.IO) {
        if (gameTitle.isBlank()) return@withContext null
        val cacheKey = gameTitle.lowercase()
        if (descriptionCache.containsKey(cacheKey)) {
            val cached = descriptionCache[cacheKey]
            return@withContext if (cached.isNullOrBlank()) null else cached
        }

        val apiKey = BuildConfig.TGDB_API_KEY
        if (apiKey.isBlank()) return@withContext null

        val candidates = buildSearchCandidates(gameTitle)
        for (candidate in candidates) {
            val encodedTitle = URLEncoder.encode(candidate, "UTF-8")
            val searchUrl = "https://api.thegamesdb.net/v1/Games/ByGameName?apikey=$apiKey&name=$encodedTitle&filter[platform]=12"
            val searchJson = getJson(searchUrl) ?: continue
            val searchObj = JSONObject(searchJson)
            val games = searchObj.optJSONObject("data")?.optJSONArray("games") ?: continue
            if (games.length() == 0) continue

            val bestGame = pickBestGame(games, candidate) ?: continue
            val overview = bestGame.optString("overview").trim()
            if (overview.isNotBlank()) {
                descriptionCache[cacheKey] = overview
                return@withContext overview
            }
        }

        descriptionCache[cacheKey] = ""
        null
    }

    private fun pickBestCoverImage(images: org.json.JSONArray): String? {
        val bestByRatio = pickCoverByRatio(images)
        if (bestByRatio != null) return bestByRatio

        for (i in 0 until images.length()) {
            val image = images.optJSONObject(i) ?: continue
            val type = image.optString("type")
            val side = image.optString("side")
            if (type == "boxart" && side == "front") {
                val filename = image.optString("filename").replace("\\/", "/")
                if (filename.isNotBlank()) {
                    return filename
                }
            }
        }
        return null
    }

    private fun pickCoverByRatio(images: org.json.JSONArray): String? {
        var bestFile: String? = null
        var bestDiff = Double.MAX_VALUE

        for (i in 0 until images.length()) {
            val image = images.optJSONObject(i) ?: continue
            val type = image.optString("type")
            val side = image.optString("side")
            if (type != "boxart" || side != "front") continue

            val width = image.optInt("width", -1)
            val height = image.optInt("height", -1)
            if (width <= 0 || height <= 0) continue

            val ratio = width.toDouble() / height.toDouble()
            val diff = kotlin.math.abs(ratio - COVER_RATIO_TARGET)
            if (diff <= COVER_RATIO_TOLERANCE && diff < bestDiff) {
                val filename = image.optString("filename").replace("\\/", "/")
                if (filename.isNotBlank()) {
                    bestFile = filename
                    bestDiff = diff
                }
            }
        }

        return bestFile
    }

    private fun pickBestGameId(games: org.json.JSONArray, targetTitle: String): Int? {
        val bestGame = pickBestGame(games, targetTitle) ?: return null
        val gameId = bestGame.optInt("id", -1)
        return if (gameId > 0) gameId else null
    }

    private fun pickBestGame(games: org.json.JSONArray, targetTitle: String): JSONObject? {
        var bestGame: JSONObject? = null
        var bestScore = Int.MIN_VALUE

        for (i in 0 until games.length()) {
            val game = games.optJSONObject(i) ?: continue
            val gameId = game.optInt("id", -1)
            if (gameId <= 0) continue
            val gameTitle = game.optString("game_title").ifBlank { game.optString("title") }
            if (gameTitle.isBlank()) continue
            val score = scoreTitleMatch(targetTitle, gameTitle)
            if (score > bestScore) {
                bestScore = score
                bestGame = game
            }
        }

        return bestGame
    }

    private fun scoreTitleMatch(targetTitle: String, candidateTitle: String): Int {
        val targetKey = normalizeForMatch(targetTitle)
        val candidateKey = normalizeForMatch(candidateTitle)
        if (targetKey.isBlank() || candidateKey.isBlank()) return Int.MIN_VALUE

        if (targetKey == candidateKey) return 1000

        val targetTokens = targetKey.split(" ").filter { it.isNotBlank() }
        val candidateTokens = candidateKey.split(" ").filter { it.isNotBlank() }
        if (targetTokens.isEmpty() || candidateTokens.isEmpty()) return Int.MIN_VALUE

        val common = targetTokens.toSet().intersect(candidateTokens.toSet())
        var score = common.size * 50
        score -= kotlin.math.abs(candidateTokens.size - targetTokens.size) * 5

        if (candidateKey.startsWith(targetKey) || targetKey.startsWith(candidateKey)) {
            score += 100
        } else if (candidateKey.contains(targetKey)) {
            score += 80
        }

        val targetHasEdition = targetTokens.any { EDITION_TOKENS.contains(it) }
        val candidateHasEdition = candidateTokens.any { EDITION_TOKENS.contains(it) }
        if (candidateHasEdition && !targetHasEdition) {
            score -= 60
        }

        return score
    }

    private fun buildSearchCandidates(title: String): List<String> {
        val trimmed = title.trim()
        val normalized = normalizeTitle(trimmed)
        val noParens = removeParenthetical(normalized)
        val noLang = removeTokens(noParens, LANGUAGE_TOKENS)
        val noTags = removeTokens(noLang, TAG_TOKENS)
        val noTagsNoHdd = removeTokens(noTags, setOf("HDD"))

        val rawCandidates = listOf(trimmed, normalized, noParens, noLang, noTags, noTagsNoHdd)
        val seen = LinkedHashSet<String>()
        rawCandidates.forEach { candidate ->
            val cleaned = candidate.trim()
            if (cleaned.length >= 3) {
                seen.add(cleaned)
            }
        }
        return seen.toList()
    }

    private fun normalizeTitle(value: String): String {
        return value
            .replace(Regex("[^A-Za-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeForMatch(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun removeParenthetical(value: String): String {
        return value.replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun removeTokens(value: String, tokens: Set<String>): String {
        if (value.isBlank()) return value
        val filtered = value.split(" ")
            .filter { token ->
                token.isNotBlank() && !tokens.contains(token.uppercase())
            }
        return filtered.joinToString(" ").trim()
    }

    private companion object {
        const val COVER_RATIO_TARGET = 0.7
        const val COVER_RATIO_TOLERANCE = 0.1
        val LANGUAGE_TOKENS = setOf(
            "EN", "DE", "IT", "FR", "ES", "PT", "RU", "JA", "KO", "ZH",
            "CN", "TW", "US", "EU", "JP", "KR", "BR", "PL", "TR", "AR",
            "NL", "SE", "NO", "DK", "FI", "GR", "HU", "CZ", "SK", "RO",
            "BG", "HR", "SL", "LT", "LV", "EE", "UA", "IL"
        )
        val TAG_TOKENS = setOf(
            "DIGITAL", "HD", "HDD", "FULL", "BUNDLE", "TRIAL", "DEMO",
            "EDITION", "ULTIMATE", "COMPLETE", "REMASTERED"
        )
        val EDITION_TOKENS = setOf(
            "edition", "ultimate", "complete", "remastered", "collection",
            "bundle", "deluxe", "goty", "definitive"
        )
    }

    private suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.bytes()
        }
    }

    private suspend fun saveCover(serial: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val file = File(coversDir, "${serial}_cover.jpg")
        try {
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getJson(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        }
    }
}
