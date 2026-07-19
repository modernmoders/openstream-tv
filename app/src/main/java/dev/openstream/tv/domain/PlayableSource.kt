package dev.openstream.tv.domain

/**
 * Everything playable resolves to this — VOD stream today, live channel
 * later (MASTER_PLAN §3.2/§8.2: playback flows must never assume "came from
 * an addon"). This is the Live-TV insurance policy: keep it addon-agnostic.
 */
data class PlayableSource(
    /** http(s) URL Media3 or an external player can open. */
    val url: String,
    val title: String,
    /** e.g. "video/mp2t" for future MPEG-TS; null lets Media3 sniff. */
    val mimeTypeHint: String? = null,
    /** Extra request headers (from stream behaviorHints.proxyHeaders). */
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** From stream behaviorHints; used by autoplay (§7). */
    val bingeGroup: String? = null,
    val startPositionMs: Long = 0,
    /**
     * Best-effort codec (from the release label for addon streams; a live
     * channel may declare its own later). Null = unknown. The player uses it
     * to pick software decoding up front when the box's hardware decoder is
     * known to mangle this codec — the "rainbow macroblocking" fix.
     */
    val videoCodec: VideoCodec? = null,
)

data class SubtitleTrack(
    val url: String,
    /** Language code or free-text label, shown as-is. */
    val lang: String,
)

/**
 * Merge two subtitle-track lists, de-duped by URL (MASTER_PLAN §4.1 "subtitles
 * fan-out" gap): [base] wins ties — a stream's own embedded track is never
 * shadowed by an addon-fetched duplicate of the same file — so addons only
 * ever ADD tracks a stream didn't already carry.
 */
fun mergeSubtitleTracks(base: List<SubtitleTrack>, extra: List<SubtitleTrack>): List<SubtitleTrack> {
    if (extra.isEmpty()) return base
    val seen = base.mapTo(mutableSetOf()) { it.url.trim().lowercase() }
    return base + extra.filter { it.url.isNotBlank() && seen.add(it.url.trim().lowercase()) }
}
