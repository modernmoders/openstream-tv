package dev.openstream.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.CatalogRepository.CatalogRef
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.addon.MetaRepository
import dev.openstream.tv.data.DEFAULT_POSTER_COLUMNS
import dev.openstream.tv.data.HomeRowPrefsStore
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.data.SeriesWatchRepository
import dev.openstream.tv.data.ViewPrefs
import dev.openstream.tv.data.applyTo
import dev.openstream.tv.data.withRecommendationsFirst
import dev.openstream.tv.domain.SeriesWatch
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.components.toChipMessage
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home-screen fan-out (MASTER_PLAN §4.1.5): every browsable catalog of every
 * enabled addon is fetched in parallel and rendered incrementally — a slow or
 * dead addon never blocks the rows that already answered; it degrades to a
 * visible failure row (§4.1.8), never a silent empty screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val metaRepository: MetaRepository,
    private val rowPrefs: HomeRowPrefsStore,
    private val viewPrefs: ViewPrefs,
    seriesWatchRepository: SeriesWatchRepository,
) : ViewModel() {

    companion object {
        /** How many Continue Watching items get their meta prefetched. */
        private const val PREFETCH_COUNT = 2
    }

    /** metaType/metaId keys already prefetched this process — fetch once. */
    private val prefetchedMeta = mutableSetOf<String>()

    sealed interface RowState {
        val ref: CatalogRef

        /** Display title — the user's rename when set (Phase 4 row manager). */
        val title: String

        data class Loading(
            override val ref: CatalogRef,
            override val title: String,
        ) : RowState

        data class Loaded(
            override val ref: CatalogRef,
            override val title: String,
            val items: List<MetaItem>,
        ) : RowState

        data class Failed(
            override val ref: CatalogRef,
            override val title: String,
            val message: String,
        ) : RowState
    }

    data class HomeUiState(
        /** True until the addon list itself has been read from Room. */
        val initializing: Boolean = true,
        val hasAddons: Boolean = false,
        /** The first row under the hero when non-empty (§5.6, round 20 #8). */
        val continueWatching: List<WatchProgress> = emptyList(),
        /** Rows in addon-order then manifest-order; each loads independently. */
        val rows: List<RowState> = emptyList(),
        /** Global poster density (§5.1 Settings → Poster size). */
        val columns: Int = DEFAULT_POSTER_COLUMNS,
        /** Latest watch progress per "metaType/metaId" for tile indicators (#5). */
        val progressByMeta: Map<String, WatchProgress> = emptyMap(),
        /** Series completion ("12 of 220") per "metaType/metaId" (Round 17). */
        val seriesWatchByMeta: Map<String, SeriesWatch> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            progressRepository.observeContinueWatching().collect { list ->
                _uiState.update { it.copy(continueWatching = list) }
                prefetchMeta(list)
            }
        }
        viewModelScope.launch {
            viewPrefs.posterColumns.collect { columns ->
                _uiState.update { it.copy(columns = columns) }
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
            // collectLatest: an addon OR row-prefs change cancels in-flight
            // fetches and restarts the fan-out. Prefs edits (row manager) make
            // this cheap in practice — the addon HTTP cache (DECISIONS #17)
            // answers the refetch from disk.
            combine(addonRepository.observeInstalled(), rowPrefs.prefs, ::Pair)
                .collectLatest { (addons, prefs) ->
                    // Hidden rows are dropped BEFORE the fan-out: a hidden
                    // row's catalog is never fetched, not just not drawn.
                    val rows = prefs.applyTo(catalogRepository.catalogRefs(addons))
                        .filterNot { it.hidden }
                        .withRecommendationsFirst(hasUserOrder = prefs.order.isNotEmpty())
                    // update{}, not value=: the Continue Watching collector above
                    // runs in parallel and its state must survive an addon change.
                    _uiState.update {
                        it.copy(
                            initializing = false,
                            hasAddons = addons.any { a -> a.enabled },
                            rows = rows.map { r -> RowState.Loading(r.ref, r.title) },
                        )
                    }
                    coroutineScope {
                        rows.forEach { row ->
                            launch {
                                val newState = catalogRepository.fetch(row.ref).fold(
                                    onSuccess = { RowState.Loaded(row.ref, row.title, it) },
                                    onFailure = { RowState.Failed(row.ref, row.title, it.toChipMessage()) },
                                )
                                // Atomic: parallel completions must not clobber
                                // each other's row updates
                                _uiState.update { state ->
                                    state.copy(rows = state.rows.map {
                                        if (it.ref.key == row.ref.key) newState else it
                                    })
                                }
                            }
                        }
                    }
                }
        }
    }

    /**
     * Warm the addon HTTP cache (DECISIONS #17) for the newest Continue
     * Watching items so clicking them opens details from disk, not network
     * (owner request 2026-07-05). Results are ignored on purpose: a failure
     * here is a non-event — the details screen does its own error handling.
     */
    private fun prefetchMeta(continueWatching: List<WatchProgress>) {
        continueWatching.take(PREFETCH_COUNT).forEach { progress ->
            if (prefetchedMeta.add("${progress.metaType}/${progress.metaId}")) {
                viewModelScope.launch {
                    metaRepository.resolveMeta(progress.metaType, progress.metaId)
                }
            }
        }
    }

    /** Home's "View ⚙" dialog (owner Round-15 #10) edits the same global
     *  poster density Settings does — changed in place, applies live. */
    fun setPosterColumns(columns: Int) {
        viewModelScope.launch { viewPrefs.setPosterColumns(columns) }
    }
}
