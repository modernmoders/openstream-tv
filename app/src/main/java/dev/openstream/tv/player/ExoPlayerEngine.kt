package dev.openstream.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.openstream.tv.domain.PlayableSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * ExoPlayer-backed [PlayerEngine] (MASTER_PLAN §6.1).
 *
 * - Generous buffering for long-form streaming over variable networks.
 * - No container/mime allowlists anywhere: MPEG-TS for future Live TV must
 *   play through this exact same path (§8.5).
 * - Per-source request headers (stream behaviorHints.proxyHeaders).
 *
 * Lifecycle: one engine per playback session, released in onCleared() of the
 * owning ViewModel. MediaSessionService integration is the next Phase 2 unit.
 */
class ExoPlayerEngine(context: Context) : PlayerEngine {

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("OpenStreamTV")
        .setAllowCrossProtocolRedirects(true)

    /** Exposed for PlayerView binding; UI must not manage its lifecycle. */
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                // min/max buffer 50s/120s; keep defaults for start thresholds
                .setBufferDurationsMs(
                    50_000, 120_000,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                .build()
        )
        .build()

    override fun play(source: PlayableSource) {
        // Headers are a factory property, not per-item; one engine plays one
        // source at a time so setting them before prepare() is safe.
        httpFactory.setDefaultRequestProperties(source.headers)

        val subtitleConfigs = source.subtitles.map { track ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.url))
                .setLanguage(track.lang)
                .setMimeType(guessSubtitleMime(track.url))
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(source.mimeTypeHint)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build())
            .build()

        exoPlayer.setMediaItem(item, source.startPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override val events: Flow<PlayerEvent> = callbackFlow {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    // READY also re-fires after seeks/rebuffers; consumers
                    // treat it as "the current source did open", nothing more.
                    Player.STATE_READY -> trySend(PlayerEvent.Ready)
                    Player.STATE_ENDED -> trySend(PlayerEvent.Ended)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // detail = the raw story for the diagnostics log (codec names
                // are exactly what the 32-bit boxes' failures need diagnosed).
                val cause = error.cause?.let { " — ${it::class.simpleName}: ${it.message}" }.orEmpty()
                trySend(PlayerEvent.Error(error.toPlainLanguage(), "${error.errorCodeName}$cause"))
            }
        }
        exoPlayer.addListener(listener)
        awaitClose { exoPlayer.removeListener(listener) }
    }

    override fun release() {
        exoPlayer.release()
    }

    /** §6.1: every PlaybackException maps to a plain-language message. */
    private fun PlaybackException.toPlainLanguage(): String = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "The stream's server can't be reached"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "The stream's server rejected the request"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "The stream is gone from its server"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            "This stream's file is damaged or not really a video"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This device can't decode this video format"
        else -> "Playback failed (${errorCodeName.removePrefix("ERROR_CODE_")})"
    }

    private fun guessSubtitleMime(url: String): String = when {
        url.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        url.endsWith(".ssa", ignoreCase = true) ||
            url.endsWith(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP // .srt is the addon-world default
    }
}
