package dev.openstream.tv.player.skip

import dev.openstream.tv.addon.AddonJson
import dev.openstream.tv.diagnostics.DiagnosticsSink
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * AniSkip v2 over HTTP. Any failure (network, bad JSON, "not found") collapses
 * to an empty list — the skip button must never be able to break playback.
 */
@Singleton
class OkHttpAniSkipClient @Inject constructor(
    private val httpClient: OkHttpClient,
) : AniSkipClient {

    override suspend fun skipTimes(
        malId: Long,
        episode: Int,
        episodeLengthSec: Long?,
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        // types=op&types=ed asks only for opening/ending. episodeLength helps
        // AniSkip pick the right submission; 0 is the documented "unknown".
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
            "?types=op&types=ed&episodeLength=${episodeLengthSec ?: 0}"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val text = response.body.string()
                AddonJson.decodeFromString(AniSkipResponse.serializer(), text).toSegments()
            }
        } catch (e: IOException) {
            emptyList()
        } catch (e: Exception) {
            // Bad JSON / unexpected shape — stay silent, never crash the player.
            emptyList()
        }
    }
}

/**
 * Resolve a series id to a MAL id. Direct for mal:/myanimelist: ids; a Kitsu id
 * is mapped through Kitsu's public `mappings` endpoint; an AniList id likewise.
 * IMDb (tt…) returns null — there's no confident 1:1 to a single MAL entry.
 * Logs the outcome so a box's App log reveals what an anime episode actually
 * carried (the only way to verify this without the streams on hand).
 */
@Singleton
class KitsuAnimeMalIdResolver @Inject constructor(
    private val httpClient: OkHttpClient,
    private val diagnostics: DiagnosticsSink,
) : AnimeMalIdResolver {

    override suspend fun malId(seriesMetaId: String): Long? {
        val id = seriesMetaId.trim()
        // "kitsu:123", "mal:123:4", "tt123:1:2" — take the scheme + first number.
        val scheme = id.substringBefore(':', "").lowercase()
        val rawNumber = id.substringAfter(':', "").substringBefore(':')
        val number = rawNumber.toLongOrNull()
        val result = when {
            scheme in setOf("mal", "myanimelist") && number != null -> number
            scheme == "kitsu" && number != null -> kitsuToMal(number)
            scheme == "anilist" && number != null -> anilistToMal(number)
            else -> null
        }
        diagnostics.record(
            "skip",
            "malId($seriesMetaId) → ${result ?: "unresolved (scheme=$scheme)"}",
        )
        return result
    }

    private suspend fun kitsuToMal(kitsuId: Long): Long? = withContext(Dispatchers.IO) {
        // Kitsu → external mappings; find the myanimelist/anime externalId.
        val url = "https://kitsu.io/api/edge/anime/$kitsuId/mappings"
        fetchMalFromMappings(url) { it.externalSite.contains("myanimelist") }
    }

    private suspend fun anilistToMal(anilistId: Long): Long? = withContext(Dispatchers.IO) {
        // Kitsu also indexes AniList ids, so map AniList→Kitsu→MAL in one query
        // shape by filtering mappings the other way is overkill; skip for v1.
        null.also { _ ->
            diagnostics.record("skip", "anilist:$anilistId mapping not implemented")
        }
    }

    private fun fetchMalFromMappings(
        url: String,
        pick: (KitsuMapping) -> Boolean,
    ): Long? {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.api+json").build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                val parsed = AddonJson.decodeFromString(KitsuMappingsResponse.serializer(), body)
                parsed.data.firstOrNull(pick)?.externalId?.toLongOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }
}

// --- Kitsu JSON:API mappings (only the fields we read) ---

@Serializable
private data class KitsuMappingsResponse(val data: List<KitsuMapping> = emptyList())

@Serializable
private data class KitsuMapping(val attributes: KitsuMappingAttrs = KitsuMappingAttrs()) {
    val externalSite: String get() = attributes.externalSite
    val externalId: String get() = attributes.externalId
}

@Serializable
private data class KitsuMappingAttrs(
    @SerialName("externalSite") val externalSite: String = "",
    @SerialName("externalId") val externalId: String = "",
)
