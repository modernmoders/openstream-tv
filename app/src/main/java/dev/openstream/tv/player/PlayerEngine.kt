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
     *  usual try-the-next-stream walk; [isNetworkError] marks the ones a
     *  wait-and-reconnect on the SAME stream can cure. */
    data class Error(
        val message: String,
        val detail: String = "",
        val isDecodeError: Boolean = false,
        val isNetworkError: Boolean = false,
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

/**
 * Transport-class error codes: the connection to the debrid/CDN host dropped,
 * timed out, or the host hiccuped — the FILE is presumably fine. The owner
 * (2026-07-28) noticed a mid-movie stall jumping straight to a different
 * stream when "the stream should pause and try to buffer" first, and these are
 * exactly the codes where re-opening the same URL at the same position is the
 * right first move. A 4xx (BAD_HTTP_STATUS) is deliberately NOT here: a dead
 * link stays dead, so those still walk to the next stream immediately.
 * Pure so it's table-testable without a device.
 */
fun isNetworkErrorCode(errorCode: Int): Boolean = when (errorCode) {
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT,
    -> true
    else -> false
}
