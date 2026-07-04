package dev.openstream.tv.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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

    private var engine: ExoPlayerEngine? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val newEngine = ExoPlayerEngine(this)
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
