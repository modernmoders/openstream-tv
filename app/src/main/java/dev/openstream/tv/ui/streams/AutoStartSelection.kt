package dev.openstream.tv.ui.streams

import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.player.StreamAlternatives
import dev.openstream.tv.ui.streams.StreamListViewModel.GroupState

/**
 * Auto-play-first-stream selection (owner request 2026-07-06). Pure so the
 * timing rule is table-testable: the "first stream in the list" is only
 * final once every addon BEFORE the first one with results has settled —
 * the fan-out (§4.1.5) renders incrementally, and a slow first addon must
 * not lose its top spot to a fast second one.
 */
sealed interface AutoStartResult {
    /** Some addon that could still claim the top spot is loading — wait. */
    data object Waiting : AutoStartResult

    /** Every addon settled and nothing is playable — no auto-start. */
    data object None : AutoStartResult

    data class Found(val addon: InstalledAddon, val stream: Stream) : AutoStartResult
}

fun firstPlayableWhenSettled(initializing: Boolean, groups: List<GroupState>): AutoStartResult {
    if (initializing) return AutoStartResult.Waiting
    // Play the first playable stream in the user's addon order — the one they
    // "normally watch" (owner 2026-07-08: don't reorder the auto-pick, e.g. to
    // prefer English — it swapped anime away from their usual release). Wait on
    // any still-loading earlier addon so a slow #1 keeps its top spot (§4.1.5).
    for (group in groups) {
        when (group) {
            is GroupState.Loading -> return AutoStartResult.Waiting
            is GroupState.Failed -> continue
            is GroupState.Loaded -> {
                val stream = group.streams.firstOrNull { it.isPlayableInV1 }
                if (stream != null) return AutoStartResult.Found(group.addon, stream)
            }
        }
    }
    return AutoStartResult.None
}

/**
 * Flatten to the "Try another server" walk order: addon order, then stream
 * order (§4.1.7). Untouched — tapping "Try a different stream" walks the list
 * exactly as the addons returned it.
 */
fun orderedAlternatives(groups: List<GroupState>): List<StreamAlternatives.Alternative> =
    groups.flatMap { group ->
        (group as? GroupState.Loaded)?.streams
            ?.filter { it.isPlayableInV1 }
            ?.map { StreamAlternatives.Alternative(group.addon.manifestUrl, it) }
            .orEmpty()
    }
