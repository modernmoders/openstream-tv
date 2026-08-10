package dev.openstream.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Junk-file length detection (StreamLength.kt) — the solid-color placeholder
 *  cards that used to "skip to the end of the episode" (owner 2026-08-09). */
class StreamLengthTest {

    // ---- parseRuntimeMinutes: the runtime strings real addons send ----

    @Test
    fun `parses Cinemeta's 'NN min' form`() {
        assertEquals(45, parseRuntimeMinutes("45 min"))
        assertEquals(148, parseRuntimeMinutes("148 min"))
    }

    @Test
    fun `parses hour forms with and without minutes`() {
        assertEquals(101, parseRuntimeMinutes("1h 41min"))
        assertEquals(120, parseRuntimeMinutes("2h"))
        assertEquals(150, parseRuntimeMinutes("2 h 30 min"))
    }

    @Test
    fun `parses a bare number as minutes`() {
        assertEquals(90, parseRuntimeMinutes("90"))
    }

    @Test
    fun `unparseable, blank, zero and null all mean unknown`() {
        assertNull(parseRuntimeMinutes(null))
        assertNull(parseRuntimeMinutes(""))
        assertNull(parseRuntimeMinutes("  "))
        assertNull(parseRuntimeMinutes("0 min"))
        assertNull(parseRuntimeMinutes("N/A"))
    }

    // ---- isImplausiblyShort: the junk verdict ----

    @Test
    fun `a placeholder-card length is junk with or without a known runtime`() {
        val ninetySeconds = 90_000L
        assertTrue(isImplausiblyShort(ninetySeconds, expectedRuntimeMin = 42))
        assertTrue(isImplausiblyShort(ninetySeconds, expectedRuntimeMin = null))
    }

    @Test
    fun `under half the declared runtime is junk`() {
        val twentyMinutes = 20 * 60_000L
        assertTrue(isImplausiblyShort(twentyMinutes, expectedRuntimeMin = 148))
    }

    @Test
    fun `a real file near the declared runtime is kept`() {
        val fortyThreeMinutes = 43 * 60_000L
        assertFalse(isImplausiblyShort(fortyThreeMinutes, expectedRuntimeMin = 45))
    }

    @Test
    fun `moderate deviation from the declared runtime is tolerated`() {
        // Double-length finales, cut differences: over half of expected = real.
        val twentyFiveMinutes = 25 * 60_000L
        assertFalse(isImplausiblyShort(twentyFiveMinutes, expectedRuntimeMin = 45))
    }

    @Test
    fun `a short kids episode above the floor is kept when runtime is unknown`() {
        val sevenMinutes = 7 * 60_000L
        assertFalse(isImplausiblyShort(sevenMinutes, expectedRuntimeMin = null))
    }

    @Test
    fun `unset duration proves nothing`() {
        // TIME_UNSET / unprepared / live: never a junk verdict.
        assertFalse(isImplausiblyShort(0L, expectedRuntimeMin = 45))
        assertFalse(isImplausiblyShort(-9223372036854775807L, expectedRuntimeMin = 45))
    }
}
