package dev.openstream.tv.ui.sound

import android.view.KeyEvent

/**
 * Pure decision logic for UI sounds (owner round 10: subtle focus/select
 * cues) — kept free of Android audio so it's JVM-testable.
 *
 * The trigger is the remote's key-down, not an actual focus move: Compose has
 * no app-wide focus-change hook, and instrumenting every focusable would
 * spread sound concerns across the whole UI. A press at a grid edge that
 * moves nothing still ticks — the native TV launcher has the same tell, and
 * nobody notices.
 */
object UiSoundPolicy {

    enum class Cue { FOCUS, SELECT }

    /** Held-repeat ticks are rate-limited so fast d-pad travel purrs instead
     *  of machine-gunning. First press of a burst always sounds. */
    const val REPEAT_MIN_INTERVAL_MS = 90L

    /**
     * Which cue (if any) a key-down should play.
     *
     * @param keyCode        the event's key code
     * @param repeatCount    0 for a fresh press, >0 while held
     * @param nowMs          monotonic now
     * @param lastFocusCueMs when the previous focus tick played
     * @param enabled        the Settings toggle
     * @param suppressed     true while the video player owns the screen
     */
    fun cueFor(
        keyCode: Int,
        repeatCount: Int,
        nowMs: Long,
        lastFocusCueMs: Long,
        enabled: Boolean,
        suppressed: Boolean,
    ): Cue? {
        if (!enabled || suppressed) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            -> {
                val throttled = repeatCount > 0 &&
                    nowMs - lastFocusCueMs < REPEAT_MIN_INTERVAL_MS
                if (throttled) null else Cue.FOCUS
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            // Held-OK repeats are long-press gestures, never extra selects.
            -> if (repeatCount == 0) Cue.SELECT else null
            else -> null
        }
    }
}
