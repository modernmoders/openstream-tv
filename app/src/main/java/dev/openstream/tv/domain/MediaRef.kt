package dev.openstream.tv.domain

/**
 * Opaque identity of a playable thing, used as the watch-progress key
 * (MASTER_PLAN §8.2/§8.4: progress must NOT be keyed by IMDb id or assume
 * "came from an addon" — a future Live-TV channel gets kind="hdhr" etc.).
 *
 * For addon content: movies use the meta id, episodes use the video id.
 */
data class MediaRef(
    val sourceKind: String,
    val externalId: String,
) {
    companion object {
        const val KIND_ADDON = "addon"

        fun addon(externalId: String) = MediaRef(KIND_ADDON, externalId)
    }
}
