package dev.openstream.tv.addon

import dev.openstream.tv.autoplay.StreamCascade
import dev.openstream.tv.domain.PlayableSource
import dev.openstream.tv.domain.SubtitleTrack

/**
 * Protocol stream → playback-layer source. The only place addon-world types
 * cross into player-world types (§8.2: nothing below this line may assume
 * "stream came from an addon").
 */
fun Stream.toPlayableSource(title: String): PlayableSource? {
    val streamUrl = url?.takeIf { it.isNotBlank() } ?: return null
    return PlayableSource(
        url = streamUrl,
        title = title,
        headers = behaviorHints.proxyHeaders?.request.orEmpty(),
        subtitles = subtitles
            .filter { it.url.isNotBlank() }
            .map { it.toSubtitleTrack() },
        bingeGroup = behaviorHints.bingeGroup,
        // StreamCascade's label heuristics are pure — reaching into autoplay
        // here is a convenience import, not a flow dependency (§8.2 intact:
        // PlayableSource itself stays addon-agnostic).
        videoCodec = StreamCascade.videoCodecOf(this),
    )
}

/** The addon-protocol subtitle object → the player-world track (§8.2 boundary). */
fun Subtitle.toSubtitleTrack(): SubtitleTrack = SubtitleTrack(url, lang)
