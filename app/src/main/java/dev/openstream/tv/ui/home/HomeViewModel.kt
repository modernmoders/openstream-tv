package dev.openstream.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.AddonRequestException
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.CatalogRepository.CatalogRef
import dev.openstream.tv.addon.MetaItem
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
) : ViewModel() {

    sealed interface RowState {
        val ref: CatalogRef

        data class Loading(override val ref: CatalogRef) : RowState
        data class Loaded(override val ref: CatalogRef, val items: List<MetaItem>) : RowState
        data class Failed(override val ref: CatalogRef, val message: String) : RowState
    }

    data class HomeUiState(
        /** True until the addon list itself has been read from Room. */
        val initializing: Boolean = true,
        val hasAddons: Boolean = false,
        /** Rows in addon-order then manifest-order; each loads independently. */
        val rows: List<RowState> = emptyList(),
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    /**
     * Short, URL-free text for the on-screen failure chip. Addon URLs are
     * user secrets (they can embed personal config tokens) — never render
     * them on screen; the full exception stays available for a future debug
     * overlay (§6.1).
     */
    private fun Throwable.toChipMessage(): String =
        when ((this as? AddonRequestException)?.reason) {
            AddonRequestException.Reason.NETWORK -> "couldn't reach the addon"
            AddonRequestException.Reason.HTTP_STATUS -> "the addon answered with an error"
            AddonRequestException.Reason.BAD_JSON -> "the addon sent an unreadable response"
            AddonRequestException.Reason.INVALID_URL,
            AddonRequestException.Reason.INVALID_MANIFEST,
            null -> "failed to load"
        }

    init {
        viewModelScope.launch {
            // collectLatest: an addon change cancels in-flight fetches and
            // restarts the fan-out against the new addon set.
            addonRepository.observeInstalled().collectLatest { addons ->
                val refs = catalogRepository.catalogRefs(addons)
                _uiState.value = HomeUiState(
                    initializing = false,
                    hasAddons = addons.any { it.enabled },
                    rows = refs.map { RowState.Loading(it) },
                )
                coroutineScope {
                    refs.forEach { ref ->
                        launch {
                            val newState = catalogRepository.fetch(ref).fold(
                                onSuccess = { RowState.Loaded(ref, it) },
                                onFailure = { RowState.Failed(ref, it.toChipMessage()) },
                            )
                            _uiState.value = _uiState.value.copy(
                                rows = _uiState.value.rows.map {
                                    if (it.ref.key == ref.key) newState else it
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
