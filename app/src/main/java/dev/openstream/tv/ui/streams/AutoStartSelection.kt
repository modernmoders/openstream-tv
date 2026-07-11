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
 * hardware-decodable → resolution (owner 2026-07-09), so the auto-pick lands on
 * a stream the box actually plays cleanly instead of an HEVC-10bit that
 * macroblocks / forces the software player. Ranking never reorders by LANGUAGE
 * (owner 2026-07-08 — that swapped anime dubs away); the source order, which
 * already carries AIOStreams' language sort, is the finest tiebreaker.
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
): AutoStartResult {
    if (initializing) return AutoStartResult.Waiting
    // The best stream can come from ANY source, so wait until they've all
    // settled before committing the auto-pick. The "Finding more streams…"
    // state covers the wait; a dead source times out to Failed, so this never
    // hangs. Supersedes the old first-in-addon-order pick.
    if (groups.any { it is GroupState.Loading }) return AutoStartResult.Waiting
    val top = StreamCascade.mergeForDisplay(loadedAsAddonStreams(groups), hardwareCodecs)
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
): List<StreamAlternatives.Alternative> =
    StreamCascade.mergeForDisplay(loadedAsAddonStreams(groups), hardwareCodecs)
        .map { StreamAlternatives.Alternative(it.addonUrl, it.stream) }
