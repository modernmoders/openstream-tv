package dev.openstream.tv.player

import dev.openstream.tv.domain.PlayableSource
import kotlinx.coroutines.flow.Flow

/**
 * The one playback abstraction (MASTER_PLAN §3.2). v1: ExoPlayerEngine.
 * External players go through ExternalPlayerLauncher (Phase 3), not here.
 * AutoplayController (§7) consumes [events].
 */
interface PlayerEngine {
    fun play(source: PlayableSource)
    val events: Flow<PlayerEvent>
    fun release()
}

sealed interface PlayerEvent {
    /** Media prepared and playable — an autoplay attempt is a success (§7.1). */
    data object Ready : PlayerEvent

    /** Playback reached the end of the media — autoplay's trigger (§7.1). */
    data object Ended : PlayerEvent

    /** Fatal player error, already mapped to a plain-language message (§6.1).
     *  [detail] carries the raw code/cause for the diagnostics log only —
     *  it must never reach the screen. [isDecodeError] marks decoder-class
     *  failures, which get one same-stream software-decoder retry before the
     *  usual try-the-next-stream walk. */
    data class Error(
        val message: String,
        val detail: String = "",
        val isDecodeError: Boolean = false,
    ) : PlayerEvent
}

/**
 * Decoder-class error codes ([androidx.media3.common.PlaybackException]): the
 * failures a software-decoder retry of the SAME stream can plausibly cure —
 * as opposed to network/server/container failures, where only a different
 * stream helps. Pure so it's table-testable without a device.
 */
fun isDecodeErrorCode(errorCode: Int): Boolean = when (errorCode) {
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    -> true
    else -> false
}
