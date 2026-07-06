package dev.openstream.tv.player

import dev.openstream.tv.addon.Stream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ordered playable streams for the video currently heading into (or in)
 * the player, shared between the stream list and the player screen — same
 * hand-off rationale as [CurrentPlayback]. This is what "Try another server"
 * walks: no wrap-around, so a full lap of broken streams ends at an honest
 * error instead of spinning forever.
 *
 * The stream list rewrites [list] as its fan-out loads and sets
 * [currentIndex] when a stream is staged; autoplay episode swaps [clear] it
 * (the next episode's alternatives are unknown until its own list loads).
 */
@Singleton
class StreamAlternatives @Inject constructor() {

    data class Alternative(val addonUrl: String, val stream: Stream)

    @Volatile
    var list: List<Alternative> = emptyList()

    @Volatile
    var currentIndex: Int = -1

    fun hasNext(): Boolean = currentIndex + 1 < list.size

    /** Step to the next alternative, or null when the list is exhausted. */
    fun advance(): Alternative? = list.getOrNull(currentIndex + 1)?.also { currentIndex++ }

    fun clear() {
        list = emptyList()
        currentIndex = -1
    }
}
