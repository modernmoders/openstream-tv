package dev.openstream.tv.player

import dev.openstream.tv.domain.PlayableSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hand-off slot between the stream list and the player screen.
 *
 * Why not navigation args: a PlayableSource carries a header map and
 * subtitle list that don't survive URL-encoding sanely. Trade-off: if the
 * process dies mid-playback, restoring the player route finds no source and
 * falls back to the home screen — acceptable for v1 (progress persistence in
 * the next unit makes resume-from-home work anyway).
 */
@Singleton
class CurrentPlayback @Inject constructor() {
    var source: PlayableSource? = null
}
