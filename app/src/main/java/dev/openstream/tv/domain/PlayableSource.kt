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
