package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Stream

/**
 * Stream-selection cascade for the next episode (§7.1 step 3).
 *
 * One comparator expresses all three tiers as strict lexicographic priority:
 *   Tier 1  bingeGroup match (the protocol's purpose-built mechanism)
 *   Tier 2  same addon > same resolution > filename similarity > cache flag
 *   Tier 3  addon priority + server order (what's left of the comparator)
 * so [rank] returns the complete ordered attempt list — the controller plays
 * candidates from the front and falls through on open failures (§7.1 step 5).
 *
 * Pure: exhaustively table-tested (§9.2).
 */
object StreamCascade {

    /** What we know about the stream that just finished playing. */
    data class CurrentStream(
        val addonUrl: String,
        val stream: Stream,
    )

    /** One addon's stream response, in the user's addon order. */
    data class AddonStreams(
        val addonUrl: String,
        /** Position in the user's addon order — lower wins ties. */
        val addonIndex: Int,
        /** Streams exactly as the addon returned them (§4.1.7). */
        val streams: List<Stream>,
    )

    data class Candidate(
        val addonUrl: String,
        val addonIndex: Int,
        /** Position within the addon's response — lower wins ties. */
        val serverIndex: Int,
        val stream: Stream,
    )

    /** All playable candidates, best attempt first. Empty = nothing playable. */
    fun rank(current: CurrentStream, groups: List<AddonStreams>): List<Candidate> {
        val currentBinge = current.stream.behaviorHints.bingeGroup
        val currentRes = resolutionOf(current.stream)
        val currentTokens = normalizedTokens(current.stream)
        val currentCached = hasCacheMarker(current.stream)

        val candidates = groups.flatMap { group ->
            group.streams.mapIndexedNotNull { i, stream ->
                if (stream.isPlayableInV1) Candidate(group.addonUrl, group.addonIndex, i, stream) else null
            }
        }

        return candidates.sortedWith(
            compareByDescending<Candidate> {
                currentBinge != null && it.stream.behaviorHints.bingeGroup == currentBinge
            }
                .thenByDescending { it.addonUrl == current.addonUrl }
                .thenByDescending { currentRes != null && resolutionOf(it.stream) == currentRes }
                .thenByDescending { tokenSimilarity(currentTokens, normalizedTokens(it.stream)) }
                .thenByDescending { hasCacheMarker(it.stream) == currentCached }
                .thenBy { it.addonIndex }
                .thenBy { it.serverIndex }
        )
    }

    /**
     * Merge every addon's streams into ONE ranked, de-duplicated list for the
     * stream picker (owner 2026-07-09: interweave the sources instead of showing
     * separate AIOStreams-1 / -2 / -3 blocks). The three AIOStreams instances
     * wrap overlapping scrapers, so the same release comes back from all of
     * them; we keep a single best copy per release (the cached copy from the
     * earliest addon) and order the result cached-first, then by resolution,
     * then the addon/server order the sources intended. Pure — table-tested.
     */
    fun mergeForDisplay(groups: List<AddonStreams>): List<Candidate> {
        val playable = groups.flatMap { group ->
            group.streams.mapIndexedNotNull { i, stream ->
                if (stream.isPlayableInV1) Candidate(group.addonUrl, group.addonIndex, i, stream) else null
            }
        }
        // De-dupe across addons: pre-order so the copy kept (first per key) is
        // the cached one from the earliest addon.
        val deduped = playable
            .sortedWith(
                compareByDescending<Candidate> { hasCacheMarker(it.stream) }
                    .thenBy { it.addonIndex }
                    .thenBy { it.serverIndex }
            )
            .distinctBy { dedupKey(it.stream) }
        // Final display order: cached first, then higher resolution, then the
        // addon/server order.
        return deduped.sortedWith(
            compareByDescending<Candidate> { hasCacheMarker(it.stream) }
                .thenByDescending { resolutionRank(it.stream) }
                .thenBy { it.addonIndex }
                .thenBy { it.serverIndex }
        )
    }

    /** Same release across sources → same key. Torrent infoHash is authoritative;
     *  otherwise the exact release filename, else the whole label. */
    fun dedupKey(stream: Stream): String =
        stream.infoHash?.lowercase()
            ?: stream.behaviorHints.filename?.takeIf { it.isNotBlank() }?.lowercase()
            ?: labelText(stream).lowercase()

    /** 4k > 1440p > 1080p > 720p > 480p > unknown, for display ranking. */
    fun resolutionRank(stream: Stream): Int = when (resolutionOf(stream)) {
        "4k" -> 5
        "1440p" -> 4
        "1080p" -> 3
        "720p" -> 2
        "480p" -> 1
        else -> 0
    }

    // --- feature extraction (heuristics over free-text stream labels) ---

    private val RESOLUTION = Regex("""\b(2160p|1440p|1080p|720p|480p|4k)\b""", RegexOption.IGNORE_CASE)

    /** "1080p"/"4k"… from name+description+filename, lowercased; null if absent. */
    fun resolutionOf(stream: Stream): String? =
        RESOLUTION.find(labelText(stream))?.value?.lowercase()?.let { if (it == "2160p") "4k" else it }

    /**
     * Debrid/instant-cache marker: AIOStreams and friends flag cached results
     * with ⚡ or the words "cached"/"instant" in the label.
     */
    fun hasCacheMarker(stream: Stream): Boolean {
        val text = labelText(stream)
        return text.contains('⚡') || Regex("""\b(cached|instant)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private val EPISODE_TOKEN = Regex("""s\d{1,3}e\d{1,3}|\b\d+\b""", RegexOption.IGNORE_CASE)
    private val SEPARATORS = Regex("""[^a-z0-9]+""")

    /**
     * Episode-number-normalized token set (§7.1 tier 2): strip SxxExx and bare
     * numbers so "Show.S01E03.1080p.WEB.GROUP" and "Show.S01E04.1080p.WEB.GROUP"
     * compare as identical release patterns.
     */
    fun normalizedTokens(stream: Stream): Set<String> =
        labelText(stream).lowercase()
            .replace(EPISODE_TOKEN, " ")
            .split(SEPARATORS)
            .filter { it.length > 1 }
            .toSet()

    /** Jaccard similarity, 0.0 (disjoint) .. 1.0 (same release pattern). */
    fun tokenSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return (a intersect b).size.toDouble() / (a union b).size
    }

    private fun labelText(stream: Stream): String =
        listOfNotNull(stream.name, stream.description ?: stream.title, stream.behaviorHints.filename)
            .joinToString(" ")
}
