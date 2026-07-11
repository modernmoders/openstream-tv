package dev.openstream.tv.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import dev.openstream.tv.data.PlaybackPrefs
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Hosts the ExoPlayer inside a MediaSessionService (MASTER_PLAN §6.1) so
 * hardware media keys, Google Assistant, and OS integration work, and
 * playback survives UI churn. Media3 manages the foreground notification.
 *
 * Ownership: this service creates and releases the engine; the UI reaches it
 * through [PlayerHolder]. Started by PlayerViewModel when playback begins,
 * stopped by it when the user leaves the player (Back = stop, TV UX).
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playerHolder: PlayerHolder
    @Inject lateinit var playbackPrefs: PlaybackPrefs
    @Inject lateinit var decoderCapabilities: DecoderCapabilities

    private var engine: ExoPlayerEngine? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // One tiny DataStore read at engine-build time (the service is created
        // once per watch session, not per frame): pick up the "prefer software
        // decoder" setting so a box that glitches can be switched to software.
        val preferSoftware = runBlocking { playbackPrefs.preferSoftwareDecoder.first() }
        // The box's true hardware codecs let the engine software-decode ONLY
        // the streams that need it (per-play decision), instead of all-or-nothing.
        val newEngine = ExoPlayerEngine(this, preferSoftware, decoderCapabilities.hardwareVideoCodecs)
        engine = newEngine
        mediaSession = MediaSession.Builder(this, newEngine.exoPlayer).build()
        playerHolder.attach(newEngine)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        playerHolder.detach()
        mediaSession?.release()
        mediaSession = null
        engine?.release()
        engine = null
        super.onDestroy()
    }
}
