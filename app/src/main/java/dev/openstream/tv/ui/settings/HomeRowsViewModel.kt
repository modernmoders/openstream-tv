package dev.openstream.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.data.HomeRow
import dev.openstream.tv.data.HomeRowPrefsStore
import dev.openstream.tv.data.applyTo
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Row/catalog manager (Phase 4 unit 1): the same customized row list Home
 * renders, but with hidden rows still visible so they can be brought back.
 * Every edit writes through [HomeRowPrefsStore]; Home reacts via its own
 * collector — there is no duplicate state to sync.
 */
@HiltViewModel
class HomeRowsViewModel @Inject constructor(
    addonRepository: AddonRepository,
    catalogRepository: CatalogRepository,
    private val store: HomeRowPrefsStore,
) : ViewModel() {

    /** Null until the first Room read lands — the screen shows nothing rather
     *  than flashing "no rows" at users who do have rows. */
    val rows: StateFlow<List<HomeRow>?> =
        combine(addonRepository.observeInstalled(), store.prefs) { addons, prefs ->
            prefs.applyTo(catalogRepository.catalogRefs(addons))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Move a row one position up (-1) or down (+1). Saving pins the FULL
     * current order: rows the user never touched keep their place even if an
     * addon later reorders its catalogs — predictability beats freshness here.
     */
    fun move(key: String, delta: Int) {
        val current = rows.value.orEmpty().map { it.ref.key }
        val from = current.indexOf(key)
        val to = from + delta
        if (from == -1 || to !in current.indices) return
        val reordered = current.toMutableList().apply {
            removeAt(from)
            add(to, key)
        }
        viewModelScope.launch { store.setOrder(reordered) }
    }

    fun setHidden(key: String, hidden: Boolean) {
        viewModelScope.launch { store.setHidden(key, hidden) }
    }

    /** Blank input restores the catalog's own name. */
    fun rename(key: String, name: String) {
        val cleaned = name.trim().takeIf { it.isNotEmpty() }
        viewModelScope.launch { store.setRename(key, cleaned) }
    }
}
