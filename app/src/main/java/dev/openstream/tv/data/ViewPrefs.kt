package dev.openstream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How Discover orders the loaded items (client-side; §5.1 view options). */
enum class DiscoverSortMode { ADDON_ORDER, ALPHABETICAL, NEWEST, TOP_RATED }

data class DiscoverViewPrefs(
    /** Poster columns in the Discover grid (§5.1 density). */
    val columns: Int = 6,
    val sort: DiscoverSortMode = DiscoverSortMode.ADDON_ORDER,
)

/**
 * Per-screen view preferences (owner request 2026-07-05: view/sort options
 * customizable on the screen where they're used). Interface so ViewModels
 * stay JVM-testable with an in-memory fake.
 */
interface ViewPrefs {
    val discover: Flow<DiscoverViewPrefs>
    suspend fun setDiscoverColumns(columns: Int)
    suspend fun setDiscoverSort(sort: DiscoverSortMode)
}

private val Context.viewPrefsStore by preferencesDataStore("view_prefs")

@Singleton
class DataStoreViewPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewPrefs {

    override val discover: Flow<DiscoverViewPrefs> =
        context.viewPrefsStore.data.map { prefs ->
            DiscoverViewPrefs(
                columns = prefs[DISCOVER_COLUMNS] ?: DiscoverViewPrefs().columns,
                // Unknown value (renamed enum in an update) falls back to default.
                sort = prefs[DISCOVER_SORT]
                    ?.let { raw -> DiscoverSortMode.entries.firstOrNull { it.name == raw } }
                    ?: DiscoverSortMode.ADDON_ORDER,
            )
        }

    override suspend fun setDiscoverColumns(columns: Int) {
        context.viewPrefsStore.edit { it[DISCOVER_COLUMNS] = columns }
    }

    override suspend fun setDiscoverSort(sort: DiscoverSortMode) {
        context.viewPrefsStore.edit { it[DISCOVER_SORT] = sort.name }
    }

    private companion object {
        val DISCOVER_COLUMNS = intPreferencesKey("discover_columns")
        val DISCOVER_SORT = stringPreferencesKey("discover_sort")
    }
}
