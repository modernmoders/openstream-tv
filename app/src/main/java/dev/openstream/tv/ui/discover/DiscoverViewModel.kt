package dev.openstream.tv.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.CatalogRepository.CatalogRef
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.data.DiscoverSortMode
import dev.openstream.tv.data.DiscoverViewPrefs
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.data.ViewPrefs
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.components.toChipMessage
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Discover = a Stremio-style category tree: Type → Catalog → Genre.
 * Types and catalogs come straight from the installed manifests (never
 * re-sorted, §4.1.7); genres are the selected catalog's declared `genre`
 * options. Picking at any level resets everything below it, exactly like
 * web.stremio.com's Discover dropdowns.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val viewPrefs: ViewPrefs,
    progressRepository: ProgressRepository,
) : ViewModel() {

    data class UiState(
        /** Distinct catalog types across enabled addons, first-seen order. */
        val types: List<String> = emptyList(),
        val selectedType: String? = null,
        /** Catalogs of the selected type, addon-then-manifest order. */
        val catalogs: List<CatalogRef> = emptyList(),
        val selected: CatalogRef? = null,
        /** Genre choices the selected catalog declares; empty = no picker. */
        val genres: List<String> = emptyList(),
        /** null = no genre filter (the picker's "None"). */
        val selectedGenre: String? = null,
        /** Catalog 404s without a genre — the picker must not offer "None". */
        val genreRequired: Boolean = false,
        val items: List<MetaItem> = emptyList(),
        val loading: Boolean = false,
        /** No more pages: addon lacks `skip` support or returned nothing new. */
        val endReached: Boolean = false,
        val error: String? = null,
        /** View options (§5.1): persisted per screen, applied at render. */
        val view: DiscoverViewPrefs = DiscoverViewPrefs(),
        /** Latest watch progress per "metaType/metaId" for tile indicators (#5). */
        val progressByMeta: Map<String, WatchProgress> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /** Every discoverable catalog across enabled addons; the tree's root. */
    private var allRefs: List<CatalogRef> = emptyList()

    private var pageJob: Job? = null

    init {
        viewModelScope.launch {
            viewPrefs.discover.collect { prefs ->
                _uiState.update { it.copy(view = prefs) }
            }
        }
        viewModelScope.launch {
            progressRepository.observeProgressByMetaKey().collect { byMeta ->
                _uiState.update { it.copy(progressByMeta = byMeta) }
            }
        }
        viewModelScope.launch {
            addonRepository.observeInstalled().collectLatest { addons ->
                allRefs = catalogRepository.discoverRefs(addons)
                val types = allRefs.map { it.catalog.type }.distinct()
                _uiState.update { it.copy(types = types) }
                // Keep the current selection if it survived the addon change.
                val kept = _uiState.value.selected?.let { sel ->
                    allRefs.firstOrNull { it.key == sel.key }
                }
                when {
                    kept != null -> select(kept, keepGenre = true)
                    types.isNotEmpty() -> selectType(types.first())
                    else -> _uiState.value = UiState() // last addon removed
                }
            }
        }
    }

    /** Pick a type: the catalog picker refills and its first entry loads. */
    fun selectType(type: String) {
        allRefs.firstOrNull { it.catalog.type == type }?.let { select(it) }
    }

    /**
     * Pick a catalog. Genre resets ([keepGenre] only preserves it across an
     * addon-list refresh of the same catalog); genre-required catalogs
     * auto-select their first option so the fetch can't 404.
     */
    fun select(ref: CatalogRef, keepGenre: Boolean = false) {
        pageJob?.cancel()
        val genres = ref.catalog.genreOptions
        val required = ref.catalog.requiresExtra("genre")
        val previous = _uiState.value.selectedGenre
        val genre = when {
            keepGenre && previous != null && previous in genres -> previous
            required -> genres.firstOrNull()
            else -> null
        }
        _uiState.update { current ->
            current.copy(
                selectedType = ref.catalog.type,
                catalogs = allRefs.filter { it.catalog.type == ref.catalog.type },
                selected = ref,
                genres = genres,
                selectedGenre = genre,
                genreRequired = required,
                items = emptyList(), loading = true,
                endReached = false, error = null,
            )
        }
        loadPage(ref, genre)
    }

    /** Pick a genre (null = clear the filter); the grid reloads from page 1. */
    fun selectGenre(genre: String?) {
        val ref = _uiState.value.selected ?: return
        pageJob?.cancel()
        _uiState.update {
            it.copy(
                selectedGenre = genre,
                items = emptyList(), loading = true,
                endReached = false, error = null,
            )
        }
        loadPage(ref, genre)
    }

    fun setColumns(columns: Int) {
        viewModelScope.launch { viewPrefs.setDiscoverColumns(columns) }
    }

    fun setSort(sort: DiscoverSortMode) {
        viewModelScope.launch { viewPrefs.setDiscoverSort(sort) }
    }

    /** Called when the grid scrolls near its end. */
    fun loadMore() {
        val state = _uiState.value
        val ref = state.selected ?: return
        if (state.loading || state.endReached) return
        // Without declared skip support, one page is all there is.
        if (!ref.catalog.supportsExtra("skip")) {
            _uiState.value = state.copy(endReached = true)
            return
        }
        _uiState.value = state.copy(loading = true)
        loadPage(ref, state.selectedGenre, skip = state.items.size)
    }

    private fun loadPage(ref: CatalogRef, genre: String?, skip: Int = 0) {
        pageJob = viewModelScope.launch {
            val extra = buildMap {
                if (genre != null) put("genre", genre)
                if (skip > 0) put("skip", skip.toString())
            }
            catalogRepository.fetch(ref, extra).fold(
                onSuccess = { page ->
                    // Atomic update; dedupe by id — addons repeat items across
                    // pages, and duplicate keys crash lazy grids.
                    _uiState.update { current ->
                        val seen = current.items.mapTo(HashSet()) { it.id }
                        val fresh = page.filter { seen.add(it.id) }
                        current.copy(
                            items = current.items + fresh,
                            loading = false,
                            endReached = fresh.isEmpty(),
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { current ->
                        current.copy(
                            loading = false,
                            // A failed page-2 fetch shouldn't wipe page 1.
                            endReached = true,
                            error = if (skip == 0) e.toChipMessage() else null,
                        )
                    }
                },
            )
        }
    }
}
