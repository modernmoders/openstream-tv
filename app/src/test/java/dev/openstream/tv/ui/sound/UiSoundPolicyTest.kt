package dev.openstream.tv.ui.sound

import android.view.KeyEvent
import dev.openstream.tv.ui.sound.UiSoundPolicy.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiSoundPolicyTest {

    private fun cue(
        keyCode: Int,
        repeat: Int = 0,
        now: Long = 1_000L,
        lastFocus: Long = 0L,
        enabled: Boolean = true,
        suppressed: Boolean = false,
    ) = UiSoundPolicy.cueFor(keyCode, repeat, now, lastFocus, enabled, suppressed)

    @Test
    fun `dpad directions play the focus tick`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        ).forEach { key -> assertEquals(Cue.FOCUS, cue(key)) }
    }

    @Test
    fun `ok and enter play the select cue`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A,
        ).forEach { key -> assertEquals(Cue.SELECT, cue(key)) }
    }

    @Test
    fun `other keys are silent`() {
        assertNull(cue(KeyEvent.KEYCODE_BACK))
        assertNull(cue(KeyEvent.KEYCODE_MENU))
        assertNull(cue(KeyEvent.KEYCODE_A))
        assertNull(cue(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun `disabled or suppressed silences everything`() {
        assertNull(cue(KeyEvent.KEYCODE_DPAD_DOWN, enabled = false))
        assertNull(cue(KeyEvent.KEYCODE_DPAD_CENTER, enabled = false))
        assertNull(cue(KeyEvent.KEYCODE_DPAD_DOWN, suppressed = true))
        assertNull(cue(KeyEvent.KEYCODE_DPAD_CENTER, suppressed = true))
    }

    @Test
    fun `held dpad repeats are throttled, fresh presses are not`() {
        val interval = UiSoundPolicy.REPEAT_MIN_INTERVAL_MS
        // A repeat right after the last tick stays quiet…
        assertNull(cue(KeyEvent.KEYCODE_DPAD_DOWN, repeat = 3, now = 100, lastFocus = 100 - interval + 1))
        // …but once the interval has passed it ticks again…
        assertEquals(
            Cue.FOCUS,
            cue(KeyEvent.KEYCODE_DPAD_DOWN, repeat = 3, now = 100 + interval, lastFocus = 100),
        )
        // …and a fresh press (repeat 0) always ticks, however recent the last.
        assertEquals(
            Cue.FOCUS,
            cue(KeyEvent.KEYCODE_DPAD_DOWN, repeat = 0, now = 101, lastFocus = 100),
        )
    }

    @Test
    fun `held OK never repeats the select cue`() {
        assertNull(cue(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1))
        assertNull(cue(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 10, now = 999_999))
    }
}
