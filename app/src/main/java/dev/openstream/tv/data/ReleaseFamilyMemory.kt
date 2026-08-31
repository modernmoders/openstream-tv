package dev.openstream.tv.data

import dev.openstream.tv.addon.Stream
import dev.openstream.tv.autoplay.StreamCascade
import dev.openstream.tv.data.db.ReleaseFamilyStrikeDao
import dev.openstream.tv.data.db.ReleaseFamilyStrikeEntity
import dev.openstream.tv.di.ApplicationScope
import dev.openstream.tv.diagnostics.DiagnosticsSink
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Per-series memory of release families that burned an auto-pick (owner
 * backlog 2026-07-26: "skip known-glitchy encodes across episodes").
 *
 * A release FAMILY is the episode-number-stripped token set of a stream's
 * label ([StreamCascade.familyKey]) — the thing that stays identical across
 * a season's episodes. WRITE ([recordStrikeAsync]) when the app abandons a
 * stream IT picked — a playback failure the viewer walked away from, or the
 * post-open check finding no English audio. READ ([strickenFamilies]) before
 * ranking: [StreamCascade.mergeForDisplay]/[StreamCascade.rank] take the set
 * and sink matching candidates below clean ones — demoted, never hidden.
 *
 * Interface so JVM tests can pass [NONE] instead of wiring Room. Extending:
 * to strike from a new failure kind, call [recordStrikeAsync] where the
 * failure is detected — the ranking side already honors whatever lands here.
 */
interface ReleaseFamilyMemory {

    /** Family keys with at least one strike for this series. */
    suspend fun strickenFamilies(seriesKey: String): Set<String>

    /** Fire-and-forget strike for [stream]'s family; [why] lands in the App log. */
    fun recordStrikeAsync(seriesKey: String, stream: Stream, why: String)

    companion object {
        /** Remembers nothing — for tests and callers outside playback. */
        val NONE = object : ReleaseFamilyMemory {
            override suspend fun strickenFamilies(seriesKey: String) = emptySet<String>()
            override fun recordStrikeAsync(seriesKey: String, stream: Stream, why: String) = Unit
        }
    }
}

/** The real, Room-backed memory. A memory feature must never break playback,
 *  so every DB touch is wrapped — failures just mean "no memory today". */
@Singleton
class RoomReleaseFamilyMemory @Inject constructor(
    private val dao: ReleaseFamilyStrikeDao,
    @ApplicationScope private val appScope: CoroutineScope,
    private val diagnostics: DiagnosticsSink,
) : ReleaseFamilyMemory {

    override suspend fun strickenFamilies(seriesKey: String): Set<String> =
        runCatching { dao.familyKeysFor(seriesKey).toSet() }.getOrDefault(emptySet())

    override fun recordStrikeAsync(seriesKey: String, stream: Stream, why: String) {
        val family = StreamCascade.familyKey(stream)
        // A label of nothing but numbers yields an empty family — striking ""
        // would lump unrelated streams together, so it's dropped.
        if (seriesKey.isBlank() || family.isBlank()) return
        appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                if (dao.bumpStrike(seriesKey, family, now) == 0) {
                    dao.insert(ReleaseFamilyStrikeEntity(seriesKey, family, strikes = 1, lastStrikeAt = now))
                }
                diagnostics.record(
                    "streams",
                    "release family struck for this series ($why): " +
                        "\"${stream.name ?: "unnamed"}\" — ranked last on later episodes",
                )
            }
        }
    }
}
