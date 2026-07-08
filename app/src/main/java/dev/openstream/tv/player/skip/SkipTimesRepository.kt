package dev.openstream.tv.player.skip

import dev.openstream.tv.diagnostics.DiagnosticsSink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One entry point for the player: "what are the skip windows for THIS episode?"
 * Resolves the series to a MAL id, asks AniSkip, and hands back segments in real
 * player milliseconds. Empty for non-anime, unmapped ids, or any failure — the
 * caller just shows no button.
 */
@Singleton
class SkipTimesRepository @Inject constructor(
    private val resolver: AnimeMalIdResolver,
    private val client: AniSkipClient,
    private val diagnostics: DiagnosticsSink,
) {
    suspend fun segmentsFor(
        seriesMetaId: String,
        episode: Int,
        durationMs: Long,
    ): List<SkipSegment> {
        if (episode <= 0) return emptyList()
        val mal = resolver.malId(seriesMetaId) ?: return emptyList()
        val lengthSec = (durationMs / 1000).takeIf { it > 0 }
        val segments = client.skipTimes(mal, episode, lengthSec)
        if (segments.isNotEmpty()) {
            diagnostics.record("skip", "mal=$mal ep=$episode → ${segments.size} skip window(s)")
        }
        return segments
    }
}
