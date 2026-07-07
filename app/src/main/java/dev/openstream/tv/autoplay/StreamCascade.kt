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

        // English audio wins over everything (owner round 12): auto-play must
        // pick an English-audio release unless the title is foreign-only. When
        // no candidate is English-friendly the predicate is constant, so this
        // tier is a no-op and the rip-consistency tiers below decide — a
        // foreign film is never stranded, it just isn't reshuffled.
        val hasEnglishOption = candidates.any { !isNonEnglishAudio(it.stream) }

        return candidates.sortedWith(
            compareByDescending<Candidate> { !hasEnglishOption || !isNonEnglishAudio(it.stream) }
                .thenByDescending {
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

    // --- audio-language preference (owner round 12) ---
    // We can't read a stream's audio tracks before opening it, so — exactly as
    // with resolution and cache flags — we read the addon's free-text label.
    // A release counts as English-friendly UNLESS it advertises another
    // language and NOT English/dual/multi. Plain English rips are usually
    // untagged, so "no language tag" stays English-friendly: we only push a
    // stream DOWN when it explicitly says it's another language, never up.

    private val ENGLISH_MARKER =
        Regex("""\benglish\b|\beng\b|\bdual\b|\bmulti\b|🇬🇧|🇺🇸|🇦🇺|🇨🇦""", RegexOption.IGNORE_CASE)

    private val NON_ENGLISH_MARKER = Regex(
        """\b(spanish|espanol|español|latino|french|francais|français|truefrench|vostfr|""" +
            """german|deutsch|italian|italiano|hindi|tamil|telugu|malayalam|kannada|marathi|""" +
            """bengali|gujarati|punjabi|urdu|portuguese|portugues|português|dublado|russian|""" +
            """japanese|korean|mandarin|cantonese|chinese|arabic|turkish|polish|lektor|dutch|""" +
            """swedish|danish|norwegian|finnish|thai|vietnamese|tagalog|hebrew|greek|czech|""" +
            """hungarian|romanian|ukrainian|persian|farsi|indonesian)\b|""" +
            """🇪🇸|🇲🇽|🇫🇷|🇩🇪|🇮🇹|🇷🇺|🇯🇵|🇰🇷|🇨🇳|🇮🇳|🇧🇷|🇵🇹|🇸🇦|🇹🇷|🇵🇱|🇳🇱""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * True when the label advertises a non-English audio language and gives no
     * English/dual/multi signal. Conservative on purpose: false for untagged
     * releases, so an English rip that simply doesn't announce its audio is
     * never demoted.
     */
    fun isNonEnglishAudio(stream: Stream): Boolean {
        val text = labelText(stream)
        return NON_ENGLISH_MARKER.containsMatchIn(text) && !ENGLISH_MARKER.containsMatchIn(text)
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
