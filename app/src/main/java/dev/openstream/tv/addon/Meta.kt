package dev.openstream.tv.addon

import dev.openstream.tv.domain.ContentType
import kotlinx.serialization.Serializable

/**
 * Meta object (details page) and Meta Preview (catalog row item) share one DTO:
 * the preview is a strict subset of the full object.
 * Spec: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.md
 */
@Serializable
data class MetaItem(
    val id: String = "",
    /** Raw type string; channel/tv must survive parsing (MASTER_PLAN §8). */
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    /** "poster" (2:3), "square" (1:1) or "landscape" (16:9) — ratio is a contract (MASTER_PLAN §5.2). */
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val released: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val language: String? = null,
    val country: String? = null,
    val awards: String? = null,
    val website: String? = null,
    val genres: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val links: List<MetaLink> = emptyList(),
    /** Episodes/uploads for series and channels; absent for movies. */
    val videos: List<Video> = emptyList(),
    val behaviorHints: MetaBehaviorHints = MetaBehaviorHints(),
) {
    val contentType: ContentType get() = ContentType.from(type)
    val isUsable: Boolean get() = id.isNotBlank() && name.isNotBlank()
}

@Serializable
data class MetaLink(
    val name: String = "",
    val category: String = "",
    val url: String = "",
)

/**
 * One video of a meta item (an episode for series, an upload for channels).
 *
 * The spec says `title` is required, but real addons (including Cinemeta)
 * often send `name` instead — [displayTitle] absorbs that.
 */
@Serializable
data class Video(
    val id: String = "",
    val title: String? = null,
    val name: String? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    /**
     * Streams embedded directly in the meta. Spec: if present, clients must
     * NOT query stream addons for this video.
     */
    val streams: List<Stream> = emptyList(),
    val available: Boolean? = null,
) {
    val displayTitle: String get() = title ?: name ?: id
}

@Serializable
data class MetaBehaviorHints(
    /** Open the details page directly on this video's stream list. */
    val defaultVideoId: String? = null,
)
