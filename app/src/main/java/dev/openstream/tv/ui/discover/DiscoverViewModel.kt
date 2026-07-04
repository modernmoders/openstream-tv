package dev.openstream.tv.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.CatalogRepository.CatalogRef
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.ui.components.toChipMessage
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Discover = deep-browse one curated catalog at a time. The owner curates
 * catalogs server-side (merged lists from multiple curators in AIOMetadata);
 * this screen surfaces each curated list as a first-class browsable grid
 * with `skip` pagination.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    data class UiState(
        val catalogs: List<CatalogRef> = emptyList(),
        val selected: CatalogRef? = null,
        val items: List<MetaItem> = emptyList(),
        val loading: Boolean = false,
        /** No more pages: addon lacks `skip` support or returned nothing new. */
        val endReached: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var pageJob: Job? = null

    init {
        viewModelScope.launch {
            addonRepository.observeInstalled().collectLatest { addons ->
                val refs = catalogRepository.catalogRefs(addons)
                // Keep the current selection if it survived the addon change.
                val kept = _uiState.value.selected?.let { sel ->
                    refs.firstOrNull { it.key == sel.key }
                }
                _uiState.value = _uiState.value.copy(catalogs = refs)
                select(kept ?: refs.firstOrNull() ?: return@collectLatest)
            }
        }
    }

    fun select(ref: CatalogRef) {
        pageJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selected = ref, items = emptyList(), loading = true,
            endReached = false, error = null,
        )
        loadPage(ref)
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
        loadPage(ref, skip = state.items.size)
    }

    private fun loadPage(ref: CatalogRef, skip: Int = 0) {
        pageJob = viewModelScope.launch {
            val extra = if (skip > 0) mapOf("skip" to skip.toString()) else emptyMap()
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
