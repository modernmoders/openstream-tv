package dev.openstream.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubbingTest {

    @Test
    fun `steps accelerate with the press streak`() {
        assertEquals(10_000L, Scrubbing.stepMs(1))
        assertEquals(10_000L, Scrubbing.stepMs(4))
        assertEquals(30_000L, Scrubbing.stepMs(5))
        assertEquals(30_000L, Scrubbing.stepMs(11))
        assertEquals(60_000L, Scrubbing.stepMs(12))
        assertEquals(60_000L, Scrubbing.stepMs(19))
        assertEquals(120_000L, Scrubbing.stepMs(20))
        assertEquals(120_000L, Scrubbing.stepMs(500))
    }

    @Test
    fun `first press starts from the live position`() {
        val target = Scrubbing.nextTarget(
            currentTargetMs = null, positionMs = 60_000, durationMs = 3_600_000,
            direction = 1, streak = 1,
        )
        assertEquals(70_000L, target)
    }

    @Test
    fun `later presses accumulate on the preview target, not the position`() {
        val target = Scrubbing.nextTarget(
            currentTargetMs = 90_000, positionMs = 60_000, durationMs = 3_600_000,
            direction = 1, streak = 2,
        )
        assertEquals(100_000L, target)
    }

    @Test
    fun `rewind moves backwards`() {
        val target = Scrubbing.nextTarget(
            currentTargetMs = null, positionMs = 60_000, durationMs = 3_600_000,
            direction = -1, streak = 1,
        )
        assertEquals(50_000L, target)
    }

    @Test
    fun `target clamps to the start`() {
        val target = Scrubbing.nextTarget(
            currentTargetMs = 5_000, positionMs = 5_000, durationMs = 3_600_000,
            direction = -1, streak = 1,
        )
        assertEquals(0L, target)
    }

    @Test
    fun `target clamps to the end`() {
        val target = Scrubbing.nextTarget(
            currentTargetMs = 3_595_000, positionMs = 0, durationMs = 3_600_000,
            direction = 1, streak = 1,
        )
        assertEquals(3_600_000L, target)
    }

    @Test
    fun `unset duration (TIME_UNSET is negative) clamps to zero, never crashes`() {
        val target = Scrubbing.nextTarget(
            currentTargetMs = null, positionMs = 0, durationMs = Long.MIN_VALUE + 1,
            direction = 1, streak = 1,
        )
        assertEquals(0L, target)
    }
}
