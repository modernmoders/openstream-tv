package dev.openstream.tv.player.skip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Intro/credits skip for anime (owner 2026-07-08). Timestamps come from the
 * community **AniSkip** database (https://api.aniskip.com), keyed by MyAnimeList
 * id + episode number — so this only ever fires for anime the community has
 * timed. Everything else simply gets no segments and no button, which is why it
 * needs no "is this anime?" guess: the data source IS the guess.
 *
 * The player polls position against [SkipSegment]s and offers a one-press
 * "Skip Intro"/"Skip Credits" that seeks to the segment's end.
 */
enum class SkipType(val label: String) {
    INTRO("Skip Intro"),
    CREDITS("Skip Credits"),
}

/** A resolved skip window in real player time. [endMs] is where OK seeks to. */
data class SkipSegment(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long,
) {
    init {
        require(startMs in 0..endMs) { "bad segment $startMs..$endMs" }
    }
}

/**
 * The segment whose window contains [positionMs], or null. Pure so the player's
 * "show the skip button now?" decision is unit-testable without a real clock.
 * A tiny lead-in (segments can start a beat early) is handled by the data, not
 * here. If windows overlap the earliest-starting active one wins (stable).
 */
fun activeSegmentAt(positionMs: Long, segments: List<SkipSegment>): SkipSegment? =
    segments
        .filter { positionMs in it.startMs until it.endMs }
        .minByOrNull { it.startMs }

/**
 * AniSkip client: skip windows for one (malId, episode). Returns an empty list
 * for "nothing timed" AND for any failure — a skip feature must never break
 * playback, so a network error just means "no button" (elder rule).
 */
fun interface AniSkipClient {
    suspend fun skipTimes(malId: Long, episode: Int, episodeLengthSec: Long?): List<SkipSegment>
}

/**
 * Resolve the app's series id to a MyAnimeList id, or null when there's no
 * confident mapping (AniSkip needs a MAL id). Anime addons that use kitsu:/mal:
 * ids resolve cleanly; an IMDb-only id (tt…) has no reliable 1:1 MAL mapping,
 * so it returns null and the feature stays silent rather than skipping the
 * wrong window.
 */
fun interface AnimeMalIdResolver {
    suspend fun malId(seriesMetaId: String): Long?
}

// --- AniSkip v2 wire format (only the fields we use) ---

@Serializable
internal data class AniSkipResponse(
    val found: Boolean = false,
    val results: List<AniSkipResult> = emptyList(),
)

@Serializable
internal data class AniSkipResult(
    @SerialName("skipType") val skipType: String = "",
    val interval: AniSkipInterval = AniSkipInterval(),
)

/** Times are seconds (may be fractional) from the start of the episode. */
@Serializable
internal data class AniSkipInterval(
    @SerialName("startTime") val startTime: Double = 0.0,
    @SerialName("endTime") val endTime: Double = 0.0,
)

/**
 * Map an AniSkip response to our segments. `op`/`mixed-op` → INTRO,
 * `ed`/`mixed-ed` → CREDITS; anything else (recap, etc.) is ignored. Zero-length
 * or inverted intervals are dropped so [SkipSegment]'s invariant always holds.
 */
internal fun AniSkipResponse.toSegments(): List<SkipSegment> {
    if (!found) return emptyList()
    return results.mapNotNull { r ->
        val type = when (r.skipType) {
            "op", "mixed-op" -> SkipType.INTRO
            "ed", "mixed-ed" -> SkipType.CREDITS
            else -> return@mapNotNull null
        }
        val start = (r.interval.startTime * 1000).toLong()
        val end = (r.interval.endTime * 1000).toLong()
        if (end <= start || start < 0) return@mapNotNull null
        SkipSegment(type, start, end)
    }
}
