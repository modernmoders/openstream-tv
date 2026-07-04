package dev.openstream.tv.addon

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
            .map { SubtitleTrack(it.url, it.lang) },
        bingeGroup = behaviorHints.bingeGroup,
    )
}
