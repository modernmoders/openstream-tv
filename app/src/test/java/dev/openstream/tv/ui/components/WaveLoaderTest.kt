package dev.openstream.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keyframe math for the wave-dots loader (owner's "SStreams Loader" design,
 * 2026-07-11): each dot rests for 40% of the cycle, rises 30%, falls 30%.
 */
class WaveLoaderTest {

    @Test
    fun `dot rests at cycle start — no rise, dim`() {
        val pose = waveDotPose(0f)
        assertEquals(0f, pose.rise, 1e-4f)
        assertEquals(WAVE_ALPHA_REST, pose.alpha, 1e-4f)
    }

    @Test
    fun `dot peaks at 30 percent — full rise, full brightness`() {
        val pose = waveDotPose(0.3f)
        assertEquals(1f, pose.rise, 1e-4f)
        assertEquals(1f, pose.alpha, 1e-4f)
    }

    @Test
    fun `dot is back at rest from 60 percent to cycle end`() {
        for (phase in listOf(0.6f, 0.75f, 0.99f)) {
            assertEquals("phase $phase", 0f, waveDotPose(phase).rise, 1e-4f)
        }
    }

    @Test
    fun `rise is monotonic on the way up and mirrors on the way down`() {
        assertTrue(waveDotPose(0.10f).rise < waveDotPose(0.20f).rise)
        // smoothstep easing is point-symmetric, so the fall retraces the rise
        assertEquals(waveDotPose(0.20f).rise, waveDotPose(0.40f).rise, 1e-4f)
    }

    @Test
    fun `stagger delays each dot by 160ms and wraps negative time into the cycle`() {
        // Dot 0 at t=0 is at phase 0; dot 3 hasn't started yet — its phase
        // must wrap to the tail of the cycle (resting), never go negative.
        assertEquals(0f, wavePhase(timeMs = 0f, dot = 0), 1e-4f)
        val early = wavePhase(timeMs = 0f, dot = 3)
        assertTrue(early in 0f..1f)
        assertEquals(0f, waveDotPose(early).rise, 1e-4f)
        // One full stagger later, dot 1 is exactly where dot 0 was.
        assertEquals(
            wavePhase(timeMs = 500f, dot = 0),
            wavePhase(timeMs = 500f + WAVE_STAGGER_MS, dot = 1),
            1e-4f,
        )
    }
}
