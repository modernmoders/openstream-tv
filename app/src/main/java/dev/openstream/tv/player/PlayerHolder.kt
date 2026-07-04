package dev.openstream.tv.player

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hands the service-owned engine to the UI. [PlaybackService] attaches its
 * engine on create and detaches on destroy; the player screen observes and
 * binds when it appears. Null = no active playback session.
 */
@Singleton
class PlayerHolder @Inject constructor() {

    private val _engine = MutableStateFlow<ExoPlayerEngine?>(null)
    val engine: StateFlow<ExoPlayerEngine?> = _engine

    fun attach(engine: ExoPlayerEngine) {
        _engine.value = engine
    }

    fun detach() {
        _engine.value = null
    }
}
