package dev.openstream.tv.ui.components

import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The browse-tile watch indicator (watched system): watched → check disc,
 * genuinely in progress → progress ring, everything short of the Continue
 * Watching floor → nothing (an accidental 20-second click must not stamp
 * posters all over Home).
 */
class PosterIndicatorTest {

    private fun progress(positionMs: Long, durationMs: Long = 1_200_000) = WatchProgress(
        ref = MediaRef.addon("tt1:1:1"),
        metaId = "tt1",
        metaType = "series",
        title = "E1",
        poster = null,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = 1,
    )

    @Test
    fun `no history means no indicator`() {
        assertNull(posterIndicatorFor(null))
    }

    @Test
    fun `finished shows the watched badge`() {
        assertEquals(PosterIndicator.Watched, posterIndicatorFor(progress(positionMs = 1_200_000)))
    }

    @Test
    fun `mid-playback shows the ring at its fraction with minutes left`() {
        // 300s of 1200s → 25%, 900s = 15 min remaining.
        assertEquals(
            PosterIndicator.InProgress(0.25f, minutesLeft = 15),
            posterIndicatorFor(progress(positionMs = 300_000)),
        )
    }

    @Test
    fun `minutes left rounds up so it never says 0 min left`() {
        // 30s from the end must read "1 min left", not "0 min left".
        assertEquals(1, minutesLeftOf(progress(positionMs = 1_170_000)))
        // Exactly on a minute boundary stays exact: 120s left = 2 min.
        assertEquals(2, minutesLeftOf(progress(positionMs = 1_080_000)))
        // Past the end (position beyond duration) clamps to 0, not negative.
        assertEquals(0, minutesLeftOf(progress(positionMs = 1_300_000)))
    }

    @Test
    fun `a brief accidental click shows nothing`() {
        // 20s is resumable in the dialog sense, but below the Continue
        // Watching floor — same rule here, same reason.
        assertNull(posterIndicatorFor(progress(positionMs = 20_000)))
    }

    @Test
    fun `unknown duration shows nothing`() {
        assertNull(posterIndicatorFor(progress(positionMs = 300_000, durationMs = 0)))
    }
}
