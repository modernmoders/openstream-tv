package dev.openstream.tv.addon

import kotlinx.serialization.Serializable

/**
 * Stream object — one playable (or not-yet-playable) source for a video.
 * Spec: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md
 *
 * v1 plays [url] streams only. Torrent ([infoHash]), YouTube ([ytId]) and
 * browser ([externalUrl]) sources are parsed and kept — the UI shows them as
 * "unsupported source" instead of hiding data (DECISIONS.md; MASTER_PLAN §4.1.4).
 */
@Serializable
data class Stream(
    val url: String? = null,
    val ytId: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val externalUrl: String? = null,
    /** Usually the addon/quality label, e.g. "AIOStreams 4K". */
    val name: String? = null,
    /** Legacy description field (pre-`description`). */
    val title: String? = null,
    val description: String? = null,
    val subtitles: List<Subtitle> = emptyList(),
    val behaviorHints: StreamBehaviorHints = StreamBehaviorHints(),
) {
    /** The only stream kind the v1 internal/external players can open. */
    val isPlayableInV1: Boolean get() = !url.isNullOrBlank()

    /** Human text for the stream row; addons put quality/size/source here. */
    val displayDescription: String get() = description ?: title ?: ""
}

@Serializable
data class StreamBehaviorHints(
    val countryWhitelist: List<String> = emptyList(),
    /** True when the URL isn't https+mp4 clean; irrelevant for native players. */
    val notWebReady: Boolean = false,
    /**
     * Autoplay's purpose-built mechanism (MASTER_PLAN §7.1 Tier 1): the next
     * episode's stream with the same bingeGroup is the same rip/quality.
     */
    val bingeGroup: String? = null,
    val proxyHeaders: ProxyHeaders? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null,
)

/** HTTP headers the player must send/expect when opening the stream URL. */
@Serializable
data class ProxyHeaders(
    val request: Map<String, String> = emptyMap(),
    val response: Map<String, String> = emptyMap(),
)
