package dev.openstream.tv.ui.search

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search fans the query out to every search-capable catalog in parallel and
 * renders result rows incrementally — same visibility rules as home
 * (§4.1.5/§4.1.8: slow addons don't block, failures are visible chips).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    sealed interface RowState {
        val ref: CatalogRef

        data class Loading(override val ref: CatalogRef) : RowState
        data class Loaded(override val ref: CatalogRef, val items: List<MetaItem>) : RowState
        data class Failed(override val ref: CatalogRef, val message: String) : RowState
    }

    data class UiState(
        val query: String = "",
        val searched: Boolean = false,
        val rows: List<RowState> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var searchJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val addons = addonRepository.observeInstalled().first()
            val refs = catalogRepository.searchRefs(addons)
            _uiState.value = UiState(
                query = trimmed,
                searched = true,
                rows = refs.map { RowState.Loading(it) },
            )
            refs.forEach { ref ->
                launch {
                    val newState = catalogRepository
                        .fetch(ref, mapOf("search" to trimmed))
                        .fold(
                            onSuccess = { RowState.Loaded(ref, it) },
                            onFailure = { RowState.Failed(ref, it.toChipMessage()) },
                        )
                    // Atomic: parallel fetch completions must not overwrite
                    // each other's row updates (real lost-update, found by test)
                    _uiState.update { state ->
                        state.copy(rows = state.rows.map {
                            if (it.ref.key == ref.key) newState else it
                        })
                    }
                }
            }
        }
    }
}
