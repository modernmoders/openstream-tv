package dev.openstream.tv.addon

import kotlinx.serialization.Serializable

/**
 * Top-level response envelopes for `/{resource}/{type}/{id}.json`.
 * All default to empty so an addon returning `{}` yields "no results",
 * not a crash (MASTER_PLAN §4.1.8).
 */

@Serializable
data class CatalogResponse(val metas: List<MetaItem> = emptyList())

@Serializable
data class MetaResponse(val meta: MetaItem? = null)

@Serializable
data class StreamsResponse(val streams: List<Stream> = emptyList())

@Serializable
data class SubtitlesResponse(val subtitles: List<Subtitle> = emptyList())
