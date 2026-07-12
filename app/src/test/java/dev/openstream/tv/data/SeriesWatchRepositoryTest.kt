package dev.openstream.tv.data

import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.SeriesWatch
import dev.openstream.tv.domain.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 17: the "12 of 220 episodes" reduction behind the amber tile bar. */
class SeriesWatchRepositoryTest {

    private fun row(
        videoId: String,
        metaId: String,
        fraction: Double,
        metaType: String = "series",
    ) = WatchProgress(
        ref = MediaRef("stremio", videoId),
        metaId = metaId,
        metaType = metaType,
        title = videoId,
        poster = null,
        positionMs = (1_000_000 * fraction).toLong(),
        durationMs = 1_000_000,
        updatedAt = 1L,
    )

    @Test
    fun `counts only FINISHED episodes, grouped by series`() {
        val counts = SeriesWatchRepository.watchedCountByMetaKey(
            listOf(
                row("naruto:1", "naruto", 1.0),
                row("naruto:2", "naruto", 0.97), // past 95% = watched
                row("naruto:3", "naruto", 0.50), // in progress, not counted
                row("aot:1", "aot", 1.0),
            ),
        )
        assertEquals(2, counts["series/naruto"])
        assertEquals(1, counts["series/aot"])
    }

    @Test
    fun `a series with nothing finished has no entry at all`() {
        val counts = SeriesWatchRepository.watchedCountByMetaKey(
            listOf(row("naruto:1", "naruto", 0.4)),
        )
        assertTrue(counts.isEmpty())
    }

    @Test
    fun `fraction and complete come from watched over total`() {
        val half = SeriesWatch(watched = 110, total = 220)
        assertEquals(0.5f, half.fraction)
        assertTrue(!half.complete)
        assertTrue(SeriesWatch(watched = 220, total = 220).complete)
        // A zero total (bad addon data) must never divide by zero.
        assertEquals(0f, SeriesWatch(watched = 3, total = 0).fraction)
    }
}
