package dev.openstream.tv.addon

import kotlinx.serialization.Serializable

/**
 * Subtitle object.
 * Spec: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/subtitles.md
 */
@Serializable
data class Subtitle(
    val id: String = "",
    val url: String = "",
    /** ISO 639-2 when the addon behaves; free text otherwise — display as-is. */
    val lang: String = "",
)
