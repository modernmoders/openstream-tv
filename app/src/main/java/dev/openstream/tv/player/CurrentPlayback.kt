package dev.openstream.tv.player

import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.PlayableSource
import dev.openstream.tv.domain.SubtitleTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One playback order: the source to play plus the identity needed to track
 * progress. [mediaRef] is nullable on purpose (§8.2) — a source without one
 * simply plays untracked; nothing in the player may require it.
 */
data class PlaybackRequest(
    val source: PlayableSource,
    val mediaRef: MediaRef?,
    /** Where a Continue Watching click navigates back to (details screen). */
    val metaId: String,
    val metaType: String,
    val poster: String?,
    /**
     * Addon-fetched subtitles for THIS video (MASTER_PLAN §4.1 fan-out gap),
     * already merged into [source]'s own subtitle list once. Carried
     * separately so "try another server" (a fresh [PlayableSource] from a
     * different stream) can re-merge them without a second addon round-trip.
     */
    val addonSubtitles: List<SubtitleTrack> = emptyList(),
)

/**
 * Hand-off slot between the stream list and the player screen.
 *
 * Why not navigation args: a PlayableSource carries a header map and
 * subtitle list that don't survive URL-encoding sanely. Trade-off: if the
 * process dies mid-playback, restoring the player route finds no request and
 * falls back to the home screen — acceptable for v1 (watch progress makes
 * resume-from-home work anyway).
 */
@Singleton
class CurrentPlayback @Inject constructor() {
    var request: PlaybackRequest? = null
}
