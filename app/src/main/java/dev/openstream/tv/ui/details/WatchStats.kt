package dev.openstream.tv.ui.details

import dev.openstream.tv.addon.Video
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.WatchProgress

/**
 * Season/show watch roll-ups for the Details screen (watched system):
 * "31 of 98 episodes watched" in the show header, a mini check on a fully
 * watched season's pill, "3 / 14" on the selected one. Pure — the episode
 * list and progress map are both already in the ViewModel's state.
 */
data class WatchStats(val watched: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && watched == total
    val fraction: Float get() = if (total > 0) watched.toFloat() / total else 0f
}

/** True when this episode's stored progress crosses the watched line (95%). */
private fun isWatched(video: Video, progress: Map<String, WatchProgress>): Boolean =
    progress[video.id]?.let(ProgressRepository::isWatched) == true

/** Roll-up across the whole show — every episode the addon lists. */
fun showWatchStats(videos: List<Video>, progress: Map<String, WatchProgress>): WatchStats =
    WatchStats(watched = videos.count { isWatched(it, progress) }, total = videos.size)

/**
 * Per-season roll-ups, keyed by season number. Episodes without a season
 * (channels, season-less series) are left out — they have no pill to badge.
 */
fun seasonWatchStats(
    videos: List<Video>,
    progress: Map<String, WatchProgress>,
): Map<Int, WatchStats> =
    videos.filter { it.season != null }
        .groupBy { it.season!! }
        .mapValues { (_, episodes) ->
            WatchStats(
                watched = episodes.count { isWatched(it, progress) },
                total = episodes.size,
            )
        }
