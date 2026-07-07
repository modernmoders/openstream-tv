package dev.openstream.tv.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the two UI cues (soft focus tick, warmer select dink) through one
 * app-lifetime SoundPool. Decisions live in [UiSoundPolicy]; this class is
 * the thin Android shell around them.
 *
 * The samples are ~50/200 ms mono WAVs generated for this app (res/raw) —
 * quiet by design AND played quiet: subtle is the whole request.
 */
@Singleton
class UiSounds @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** Mirrors the Settings toggle; MainActivity keeps it current. */
    @Volatile var enabled: Boolean = true

    /** True while the video player owns the screen — no ticks over content
     *  (scrubbing alone would rattle constantly). PlayerViewModel sets it. */
    @Volatile var suppressed: Boolean = false

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val focusId = pool.load(context, R.raw.ui_focus, 1)
    private val selectId = pool.load(context, R.raw.ui_select, 1)

    private var lastFocusCueMs = 0L

    /** Feed every activity key-down here (MainActivity.dispatchKeyEvent). */
    fun onKeyDown(event: KeyEvent) {
        val now = SystemClock.uptimeMillis()
        when (
            UiSoundPolicy.cueFor(
                keyCode = event.keyCode,
                repeatCount = event.repeatCount,
                nowMs = now,
                lastFocusCueMs = lastFocusCueMs,
                enabled = enabled,
                suppressed = suppressed,
            )
        ) {
            UiSoundPolicy.Cue.FOCUS -> {
                lastFocusCueMs = now
                // Volumes < 1 do the "subtle" work; the tick sits under the
                // room, the select is just barely more present.
                pool.play(focusId, 0.20f, 0.20f, 1, 0, 1f)
            }
            UiSoundPolicy.Cue.SELECT -> pool.play(selectId, 0.35f, 0.35f, 1, 0, 1f)
            null -> Unit
        }
    }
}
