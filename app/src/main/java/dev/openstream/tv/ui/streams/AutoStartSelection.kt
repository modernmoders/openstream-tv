package dev.openstream.tv.ui.streams

import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.autoplay.StreamCascade
import dev.openstream.tv.domain.VideoCodec
import dev.openstream.tv.player.StreamAlternatives
import dev.openstream.tv.ui.streams.StreamListViewModel.GroupState

/**
 * Auto-play + "Try another server" selection. Pure so the rules are
 * table-testable. Both run through [StreamCascade.mergeForDisplay]: the three
 * AIOStreams instances are interwoven, de-duplicated, and ranked cached-first →
 * English audio (confirmed > unknown > confirmed-foreign, owner 2026-07-26) →
 * hardware-decodable → resolution (owner 2026-07-09), so the auto-pick lands on
 * a stream the box actually plays cleanly instead of an HEVC-10bit that
 * macroblocks / forces the software player. (This supersedes the 2026-07-08
 * "never reorder by language" rule — the owner's 07-10 and 07-26 directives
 * both put English audio explicitly into the ranking.)
 */
sealed interface AutoStartResult {
    /** A source that could still contribute the best stream is loading — wait. */
    data object Waiting : AutoStartResult

    /** Every source settled and nothing is playable — no auto-start. */
    data object None : AutoStartResult

    data class Found(val addon: InstalledAddon, val stream: Stream) : AutoStartResult
}

/** Loaded groups → [StreamCascade.AddonStreams], addon order preserved. */
private fun loadedAsAddonStreams(groups: List<GroupState>): List<StreamCascade.AddonStreams> {
    var index = 0
    return groups.mapNotNull { group ->
        (group as? GroupState.Loaded)?.let {
            StreamCascade.AddonStreams(it.addon.manifestUrl, index++, it.streams)
        }
    }
}

fun bestPlayableWhenSettled(
    initializing: Boolean,
    groups: List<GroupState>,
    hardwareCodecs: Set<VideoCodec> = emptySet(),
    strickenFamilies: Set<String> = emptySet(),
): AutoStartResult {
    if (initializing) return AutoStartResult.Waiting
    // The best stream can come from ANY source, so wait until they've all
    // settled before committing the auto-pick. The "Finding more streams…"
    // state covers the wait; a dead source times out to Failed, so this never
    // hangs. Supersedes the old first-in-addon-order pick.
    //
    // The caller puts a deadline on this wait — see StreamListViewModel's
    // AUTO_START_SETTLE_BUDGET_MS — because one chronically slow instance
    // (the fortheweebs Backup answers in 10s+) would otherwise set the
    // start-up delay for EVERY video.
    if (groups.any { it is GroupState.Loading }) return AutoStartResult.Waiting
    return bestPlayableAmongLoaded(groups, hardwareCodecs, strickenFamilies)
}

/**
 * The best stream out of the sources that have answered SO FAR — the same
 * ranking, just without waiting on stragglers. Used once the settle budget
 * runs out, so a slow addon delays playback by a bounded amount instead of
 * however long it feels like taking.
 */
fun bestPlayableAmongLoaded(
    groups: List<GroupState>,
    hardwareCodecs: Set<VideoCodec> = emptySet(),
    strickenFamilies: Set<String> = emptySet(),
): AutoStartResult {
    val top = StreamCascade.mergeForDisplay(loadedAsAddonStreams(groups), hardwareCodecs, strickenFamilies)
        .firstOrNull() ?: return AutoStartResult.None
    val addon = groups.filterIsInstance<GroupState.Loaded>()
        .firstOrNull { it.addon.manifestUrl == top.addonUrl }?.addon
        ?: return AutoStartResult.None
    return AutoStartResult.Found(addon, top.stream)
}

/**
 * The "Try a different stream" walk order — the same interwoven, ranked,
 * de-duplicated list the picker shows, so tapping it steps through streams
 * best-first (and never revisits a duplicate the sources all returned).
 */
fun orderedAlternatives(
    groups: List<GroupState>,
    hardwareCodecs: Set<VideoCodec> = emptySet(),
    strickenFamilies: Set<String> = emptySet(),
): List<StreamAlternatives.Alternative> =
    StreamCascade.mergeForDisplay(loadedAsAddonStreams(groups), hardwareCodecs, strickenFamilies)
        .map { StreamAlternatives.Alternative(it.addonUrl, it.stream) }
