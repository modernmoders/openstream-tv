package dev.openstream.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.MetaRepository
import dev.openstream.tv.data.DEFAULT_POSTER_COLUMNS
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.data.SeriesWatchRepository
import dev.openstream.tv.data.ViewPrefs
import dev.openstream.tv.domain.SeriesWatch
import dev.openstream.tv.domain.WatchProgress
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Library (owner Round-22 #2). All data is local Room rows; the reductions
 * are [LibraryModel]'s pure functions. Filter/type selections live in the VM
 * (not DataStore) on purpose: the rail keeps section state alive while the
 * app runs, but a fresh launch starting back on "All / Last Watched" is the
 * predictable behavior for a family TV — nobody wonders why the library
 * "lost" titles because a filter stuck from last week.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    seriesWatchRepository: SeriesWatchRepository,
    viewPrefs: ViewPrefs,
    private val metaRepository: MetaRepository,
) : ViewModel() {

    data class LibraryUiState(
        /** True until the first Room emission — gates the empty-state text. */
        val loading: Boolean = true,
        /** Every watched title, recency order (the pre-filter universe). */
        val entries: List<LibraryEntry> = emptyList(),
        val filter: LibraryFilter = LibraryFilter.ALL,
        val type: LibraryType = LibraryType.ALL,
        /** Global poster density (§5.1 Settings → Poster size). */
        val columns: Int = DEFAULT_POSTER_COLUMNS,
        /** Latest progress per metaKey for the tile indicators. */
        val progressByMeta: Map<String, WatchProgress> = emptyMap(),
        /** Series completion ("12 of 220") per metaKey. */
        val seriesWatchByMeta: Map<String, SeriesWatch> = emptyMap(),
        /**
         * Series-name overlay per metaKey. A progress row stores the EPISODE
         * label the player was opened with ("S1E5 · The Spa"), which is right
         * for Continue Watching but wrong on a one-tile-per-show shelf — so
         * series tiles get their show's real name (and poster) from meta.
         * Cosmetic: entries missing here just show the stored episode title,
         * so the Library still works fully offline.
         */
        val metaNames: Map<String, MetaName> = emptyMap(),
    ) {
        /** What the grid shows: name overlay, type lens, then bar selection —
         *  overlay FIRST so A–Z/Z–A sort on what the tile actually says. */
        val shown: List<LibraryEntry>
            get() {
                val display = entries.map { e ->
                    metaNames[e.metaKey]?.let { m ->
                        e.copy(title = m.name, poster = m.poster ?: e.poster)
                    } ?: e
                }
                return LibraryModel.apply(LibraryModel.applyType(display, type), filter)
            }
    }

    data class MetaName(val name: String, val poster: String?)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    /** metaKeys already resolved (or in flight) this VM's lifetime. */
    private val resolvedNames = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            progressRepository.observeAllProgress().collect { rows ->
                val entries = LibraryModel.entries(rows)
                _uiState.update { it.copy(loading = false, entries = entries) }
                enrichSeriesNames(entries)
            }
        }
        viewModelScope.launch {
            progressRepository.observeProgressByMetaKey().collect { byMeta ->
                _uiState.update { it.copy(progressByMeta = byMeta) }
            }
        }
        viewModelScope.launch {
            seriesWatchRepository.observeSeriesWatchByMetaKey().collect { byMeta ->
                _uiState.update { it.copy(seriesWatchByMeta = byMeta) }
            }
        }
        viewModelScope.launch {
            viewPrefs.posterColumns.collect { columns ->
                _uiState.update { it.copy(columns = columns) }
            }
        }
    }

    fun setFilter(filter: LibraryFilter) = _uiState.update { it.copy(filter = filter) }

    fun setType(type: LibraryType) = _uiState.update { it.copy(type = type) }

    /**
     * Resolve show names for series-like entries, one at a time (the addon
     * HTTP cache answers instantly for anything ever opened via Details, so
     * this is normally a disk read, not a network storm). Movies already
     * store their own name. Failures are non-events — the tile keeps the
     * stored episode title.
     */
    private fun enrichSeriesNames(entries: List<LibraryEntry>) {
        val pending = entries.filter {
            !it.metaType.equals("movie", ignoreCase = true) && resolvedNames.add(it.metaKey)
        }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            pending.forEach { entry ->
                metaRepository.resolveMeta(entry.metaType, entry.metaId).onSuccess { meta ->
                    if (meta.name.isNotBlank()) {
                        _uiState.update {
                            it.copy(metaNames = it.metaNames + (entry.metaKey to MetaName(meta.name, meta.poster)))
                        }
                    }
                }
            }
        }
    }
}
