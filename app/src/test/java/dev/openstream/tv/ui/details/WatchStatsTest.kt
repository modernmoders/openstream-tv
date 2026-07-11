package dev.openstream.tv.ui.details

import dev.openstream.tv.addon.Video
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Season/show roll-ups behind the watched system's Details chrome: the
 * "31 of 98 episodes watched" header line, the mini check on a fully
 * watched season's pill, the "3 / 14" count on the selected one.
 */
class WatchStatsTest {

    private fun episode(id: String, season: Int?) = Video(id = id, season = season)

    /** A stored row at [fraction] of a 20-minute episode. */
    private fun progressAt(id: String, fraction: Double) = WatchProgress(
        ref = MediaRef.addon(id),
        metaId = "tt1",
        metaType = "series",
        title = id,
        poster = null,
        positionMs = (1_200_000 * fraction).toLong(),
        durationMs = 1_200_000,
        updatedAt = 1,
    )

    private val videos = listOf(
        episode("s1e1", season = 1),
        episode("s1e2", season = 1),
        episode("s2e1", season = 2),
        episode("s2e2", season = 2),
        episode("s2e3", season = 2),
    )

    @Test
    fun `show roll-up counts only episodes past the watched line`() {
        val progress = mapOf(
            "s1e1" to progressAt("s1e1", 1.0), // watched
            "s1e2" to progressAt("s1e2", 0.5), // in progress — not watched
            "s2e1" to progressAt("s2e1", 0.96), // past 95% — watched
        )
        assertEquals(WatchStats(watched = 2, total = 5), showWatchStats(videos, progress))
    }

    @Test
    fun `season stats split by season and flag a complete one`() {
        val progress = mapOf(
            "s1e1" to progressAt("s1e1", 1.0),
            "s1e2" to progressAt("s1e2", 1.0),
            "s2e1" to progressAt("s2e1", 1.0),
        )
        val bySeason = seasonWatchStats(videos, progress)
        assertTrue(bySeason.getValue(1).complete)
        assertEquals(WatchStats(watched = 1, total = 3), bySeason.getValue(2))
        assertFalse(bySeason.getValue(2).complete)
    }

    @Test
    fun `season-less episodes are left out of season stats but count in the show`() {
        val mixed = videos + episode("special", season = null)
        val progress = mapOf("special" to progressAt("special", 1.0))
        assertEquals(setOf(1, 2), seasonWatchStats(mixed, progress).keys)
        assertEquals(WatchStats(watched = 1, total = 6), showWatchStats(mixed, progress))
    }

    @Test
    fun `no history means zero watched and no complete seasons`() {
        val bySeason = seasonWatchStats(videos, emptyMap())
        assertTrue(bySeason.values.none { it.complete })
        assertEquals(WatchStats(watched = 0, total = 5), showWatchStats(videos, emptyMap()))
    }

    @Test
    fun `an empty season is never complete`() {
        // total == 0 must not read as "all watched" — guard against the
        // vacuous-truth bug on shows whose season list is still loading.
        assertFalse(WatchStats(watched = 0, total = 0).complete)
    }
}
