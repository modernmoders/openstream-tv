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

    /** Fatal player error, already mapped to a plain-language message (§6.1). */
    data class Error(val message: String) : PlayerEvent
}
