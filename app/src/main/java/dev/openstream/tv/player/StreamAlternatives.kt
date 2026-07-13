package dev.openstream.tv.player

import dev.openstream.tv.addon.Stream
import dev.openstream.tv.autoplay.StreamCascade
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

    /**
     * Step to the next alternative, or null when the list is exhausted.
     *
     * With [preferDifferentFrom] set (owner Round 19: "Try a different
     * stream" landed on the glitched WIKIRIP's own per-episode sibling —
     * same encode, same glitch), the walk first skips candidates whose
     * release pattern matches the stream being abandoned; when everything
     * left looks the same, a similar stream still beats a dead end.
     */
    fun advance(preferDifferentFrom: Stream? = null): Alternative? {
        val next = currentIndex + 1
        if (next >= list.size) return null
        if (preferDifferentFrom != null) {
            val currentTokens = StreamCascade.normalizedTokens(preferDifferentFrom)
            for (i in next until list.size) {
                val similarity = StreamCascade.tokenSimilarity(
                    currentTokens, StreamCascade.normalizedTokens(list[i].stream)
                )
                if (similarity < SAME_RELEASE_SIMILARITY) {
                    currentIndex = i
                    return list[i]
                }
            }
        }
        currentIndex = next
        return list[next]
    }

    fun clear() {
        list = emptyList()
        currentIndex = -1
    }

    private companion object {
        /** Jaccard similarity at or above this = same release family (the
         *  WIKIRIP season pack vs its per-episode twin measures exactly 0.5). */
        const val SAME_RELEASE_SIMILARITY = 0.5
    }
}
