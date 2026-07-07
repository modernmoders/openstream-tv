package dev.openstream.tv.addon

/**
 * Absolute ("straight-through") episode numbers for a series, keyed by video id.
 *
 * Anime addons disagree on numbering: some emit per-season (Season 3 · Episode
 * 32), some the absolute count (Episode 115). When the viewer prefers absolute
 * numbers we compute them ourselves so the choice holds regardless of what the
 * addon sends: every episode in a real season (>= 1) counted in season/episode
 * order, with specials (season 0) excluded from the count.
 *
 * Naruto sanity check: seasons 1–3 hold 35 + 48 + 48 episodes, so Season 3
 * Episode 32 ("Your Opponent Is Me!") = 35 + 48 + 32 = absolute episode 115.
 */
fun absoluteEpisodeNumbers(videos: List<Video>): Map<String, Int> =
    videos
        .filter { (it.season ?: 0) >= 1 && it.id.isNotBlank() }
        .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
        .mapIndexed { index, video -> video.id to index + 1 }
        .toMap()
