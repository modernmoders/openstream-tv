package dev.openstream.tv.ui.streams

import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.autoplay.StreamCascade
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
    // Prefer an English-audio release (owner round 12). Walk the addons in
    // order, waiting on any still-loading one (a higher-priority English pick
    // may still arrive), and take the first that offers an English-friendly
    // playable stream. Only when every addon has settled with no English
    // option do we fall back to the first playable — the foreign-only title
    // case, where nothing must be stranded.
    for (group in groups) {
        when (group) {
            is GroupState.Loading -> return AutoStartResult.Waiting
            is GroupState.Failed -> continue
            is GroupState.Loaded -> {
                val english = group.streams.firstOrNull {
                    it.isPlayableInV1 && !StreamCascade.isNonEnglishAudio(it)
                }
                if (english != null) return AutoStartResult.Found(group.addon, english)
            }
        }
    }
    // Everything settled, no English-friendly stream anywhere: foreign-only
    // fallback — first playable in addon order (the original behavior).
    for (group in groups) {
        val stream = (group as? GroupState.Loaded)?.streams?.firstOrNull { it.isPlayableInV1 } ?: continue
        return AutoStartResult.Found(group.addon, stream)
    }
    return AutoStartResult.None
}

/**
 * Flatten to the "Try another server" walk order: addon order, then stream
 * order (§4.1.7) — but English-audio streams first when the video has any
 * (owner round 12), so tapping "Try a different stream" keeps landing on
 * English before it ever reaches a foreign-audio release. Stable, so a
 * foreign-only video is left in untouched addon order.
 */
fun orderedAlternatives(groups: List<GroupState>): List<StreamAlternatives.Alternative> {
    val alternatives = groups.flatMap { group ->
        (group as? GroupState.Loaded)?.streams
            ?.filter { it.isPlayableInV1 }
            ?.map { StreamAlternatives.Alternative(group.addon.manifestUrl, it) }
            .orEmpty()
    }
    val hasEnglish = alternatives.any { !StreamCascade.isNonEnglishAudio(it.stream) }
    return if (hasEnglish) {
        alternatives.sortedBy { StreamCascade.isNonEnglishAudio(it.stream) }
    } else {
        alternatives
    }
}
