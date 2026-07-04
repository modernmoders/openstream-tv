package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Video

/**
 * Next-episode resolution from a series meta's `videos` array (§7.1 step 1).
 *
 * Pure functions: fully table-tested without Android or coroutines.
 */
object NextEpisode {

    /**
     * Binge order: by (season, episode). Specials (season 0) sort after all
     * regular seasons — nobody wants a behind-the-scenes reel between
     * S01E10 and S02E01. Videos without season/episode numbers keep their
     * array position at the end (channels/uploads aren't binge material).
     */
    fun orderVideos(videos: List<Video>): List<Video> {
        // sortedBy is stable, so equal keys (e.g. the unnumbered tail) keep array order
        return videos.sortedBy(::sortKey)
    }

    /** The episode to autoplay after [currentVideoId], or null at series end. */
    fun nextAfter(videos: List<Video>, currentVideoId: String): Video? {
        val ordered = orderVideos(videos)
        val currentIndex = ordered.indexOfFirst { it.id == currentVideoId }
        if (currentIndex == -1) return null
        return ordered.getOrNull(currentIndex + 1)
    }

    private fun sortKey(video: Video): Long {
        val season = video.season
        val episode = video.episode
        // Unnumbered → very end; specials (season 0) → after regular seasons
        val seasonKey: Long = when {
            season == null || episode == null -> UNNUMBERED
            season == 0 -> SPECIALS
            else -> season.toLong()
        }
        return seasonKey * 100_000 + (episode ?: 0)
    }

    private const val SPECIALS = 20_000L
    private const val UNNUMBERED = 20_001L
}
