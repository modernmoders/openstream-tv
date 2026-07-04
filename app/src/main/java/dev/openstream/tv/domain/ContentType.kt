package dev.openstream.tv.domain

/**
 * Content types from the addon protocol.
 *
 * CHANNEL and TV exist from day one per MASTER_PLAN §8 (Live-TV
 * future-proofing): parsers must never drop `channel`/`tv` items even though
 * v1 renders no UI for them. Unknown types map to OTHER instead of failing.
 */
enum class ContentType {
    MOVIE, SERIES, CHANNEL, TV, OTHER;

    companion object {
        fun from(raw: String?): ContentType = when (raw?.lowercase()) {
            "movie" -> MOVIE
            "series" -> SERIES
            "channel" -> CHANNEL
            "tv" -> TV
            else -> OTHER
        }
    }
}
