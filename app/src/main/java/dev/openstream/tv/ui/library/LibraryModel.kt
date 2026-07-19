package dev.openstream.tv.ui.library

import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.WatchProgress

/**
 * Library = everything the TV has ever watched (owner Round-22 #2: "it would
 * show everything you've watched", with Stremio's Library filter bar). Built
 * entirely from the local watch_progress rows — the same source Continue
 * Watching uses — so it works offline and never depends on an addon. Trakt
 * history lives server-side (AIOMetadata scrobbles it); the app has no Trakt
 * client, so what this box watched IS the library.
 *
 * Pure reductions live here (no Android imports) so they're unit-testable
 * the same way ProgressRepository's companions are.
 */

/** One Library tile: a TITLE (movie or whole series), never per-episode. */
data class LibraryEntry(
    val metaType: String,
    val metaId: String,
    val title: String,
    val poster: String?,
    /** Most recent activity on any episode/play of this title. */
    val lastWatchedAt: Long,
    /** Finished plays: episodes for a series, 0/1 for a movie. */
    val finishedCount: Int,
) {
    val metaKey: String get() = ProgressRepository.metaKey(metaType, metaId)
    /** "Watched" = at least one finished play (a movie seen, an episode done). */
    val isWatched: Boolean get() = finishedCount > 0
}

/**
 * The filter bar's choices — Stremio's Library dropdown verbatim (owner
 * supplied the screenshot, Round 20 pic 2): one single-select mixing sorts
 * and watched-state filters, because that's the bar the family already knows.
 */
enum class LibraryFilter(val label: String) {
    ALL("All"),
    LAST_WATCHED("Last Watched"),
    A_TO_Z("A–Z"),
    Z_TO_A("Z–A"),
    MOST_WATCHED("Most Watched"),
    WATCHED("Watched"),
    NOT_WATCHED("Not Watched"),
}

/** Type lens over the library — "All" / movies only / series only. */
enum class LibraryType(val label: String) {
    ALL("All types"),
    MOVIES("Movies"),
    SERIES("Series"),
}

object LibraryModel {

    /**
     * Collapse per-episode progress rows into one entry per title. The most
     * recent row names the tile (its title/poster are freshest — addons fix
     * artwork over time), recency is the max over all rows, and finished
     * plays are counted with the same 95% line the ✓ indicators use.
     */
    fun entries(all: List<WatchProgress>): List<LibraryEntry> =
        all.groupBy { ProgressRepository.metaKey(it.metaType, it.metaId) }
            .map { (_, rows) ->
                val latest = rows.maxBy { it.updatedAt }
                LibraryEntry(
                    metaType = latest.metaType,
                    metaId = latest.metaId,
                    title = latest.title,
                    poster = latest.poster,
                    lastWatchedAt = latest.updatedAt,
                    finishedCount = rows.count { ProgressRepository.isWatched(it) },
                )
            }
            .sortedByDescending { it.lastWatchedAt }

    /** Apply one bar selection: sorts re-order, watched-state picks filter. */
    fun apply(entries: List<LibraryEntry>, filter: LibraryFilter): List<LibraryEntry> =
        when (filter) {
            // entries() already delivers recency order; ALL is the no-op lens.
            LibraryFilter.ALL, LibraryFilter.LAST_WATCHED ->
                entries.sortedByDescending { it.lastWatchedAt }
            LibraryFilter.A_TO_Z -> entries.sortedBy { it.title.lowercase() }
            LibraryFilter.Z_TO_A -> entries.sortedByDescending { it.title.lowercase() }
            // Ties (every movie is "1 watch") fall back to recency so the
            // order stays meaningful instead of arbitrary.
            LibraryFilter.MOST_WATCHED ->
                entries.sortedWith(
                    compareByDescending<LibraryEntry> { it.finishedCount }
                        .thenByDescending { it.lastWatchedAt }
                )
            LibraryFilter.WATCHED ->
                entries.filter { it.isWatched }.sortedByDescending { it.lastWatchedAt }
            LibraryFilter.NOT_WATCHED ->
                entries.filter { !it.isWatched }.sortedByDescending { it.lastWatchedAt }
        }

    /** Apply the type lens. Series/channel both count as "series-like" —
     *  the split people mean is "movies vs shows", not addon type strings. */
    fun applyType(entries: List<LibraryEntry>, type: LibraryType): List<LibraryEntry> =
        when (type) {
            LibraryType.ALL -> entries
            LibraryType.MOVIES -> entries.filter { it.metaType.equals("movie", ignoreCase = true) }
            LibraryType.SERIES -> entries.filter { !it.metaType.equals("movie", ignoreCase = true) }
        }
}
