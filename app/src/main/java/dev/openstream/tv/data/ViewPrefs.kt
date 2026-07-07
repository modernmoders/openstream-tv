package dev.openstream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

/**
 * How episode numbers read in series/anime episode lists.
 * SEASONAL = per-season ("Episode 32" inside Season 3); ABSOLUTE = the
 * straight-through count across all seasons ("Episode 115"). Anime addons
 * disagree on this (MAL/AIOMetadata often number absolutely, Cinemeta per
 * season), so the viewer picks — and we compute absolute ourselves so the
 * choice holds no matter what the addon sends. Default seasonal.
 */
enum class EpisodeNumbering { SEASONAL, ABSOLUTE }

data class DiscoverViewPrefs(
    /** Poster columns in the Discover grid (§5.1 density). */
    val columns: Int = 6,
    val sort: DiscoverSortMode = DiscoverSortMode.ADDON_ORDER,
)

/** Global poster density bounds (§5.1). 6 was the launch default; the owner
 *  counts 6 visible posters as too few, so Settings offers 4–8. */
const val MIN_POSTER_COLUMNS = 4
const val MAX_POSTER_COLUMNS = 8
const val DEFAULT_POSTER_COLUMNS = 6

/**
 * Per-screen view preferences (owner request 2026-07-05: view/sort options
 * customizable on the screen where they're used) plus the GLOBAL poster
 * density used by home/search rows (§5.1 — Discover keeps its own per-screen
 * View chip). Interface so ViewModels stay JVM-testable with an in-memory fake.
 */
interface ViewPrefs {
    val discover: Flow<DiscoverViewPrefs>

    /** Poster columns for home/search rows (4–8, default 6). */
    val posterColumns: Flow<Int>

    /**
     * Expert mode (owner directive 2026-07-06): technical tools — the addon
     * manager today, diagnostics later — exist only behind this toggle, at
     * the bottom of Settings. Everyone else gets the friendly surface only.
     */
    val expertMode: Flow<Boolean>

    /** Episode numbering style for series/anime episode lists (default seasonal). */
    val episodeNumbering: Flow<EpisodeNumbering>

    /** Subtle focus/select sounds (owner round 10). Default on. */
    val uiSounds: Flow<Boolean>

    suspend fun setDiscoverColumns(columns: Int)
    suspend fun setDiscoverSort(sort: DiscoverSortMode)
    suspend fun setPosterColumns(columns: Int)
    suspend fun setExpertMode(enabled: Boolean)
    suspend fun setEpisodeNumbering(mode: EpisodeNumbering)
    suspend fun setUiSounds(enabled: Boolean)
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

    override val posterColumns: Flow<Int> =
        context.viewPrefsStore.data.map { prefs ->
            // Out-of-range persisted value (future setting change) → default.
            (prefs[POSTER_COLUMNS] ?: DEFAULT_POSTER_COLUMNS)
                .takeIf { it in MIN_POSTER_COLUMNS..MAX_POSTER_COLUMNS }
                ?: DEFAULT_POSTER_COLUMNS
        }

    override suspend fun setDiscoverColumns(columns: Int) {
        context.viewPrefsStore.edit { it[DISCOVER_COLUMNS] = columns }
    }

    override suspend fun setDiscoverSort(sort: DiscoverSortMode) {
        context.viewPrefsStore.edit { it[DISCOVER_SORT] = sort.name }
    }

    override val expertMode: Flow<Boolean> =
        context.viewPrefsStore.data.map { prefs -> prefs[EXPERT_MODE] ?: false }

    override val episodeNumbering: Flow<EpisodeNumbering> =
        context.viewPrefsStore.data.map { prefs ->
            // Unknown value (renamed enum in an update) falls back to seasonal.
            prefs[EPISODE_NUMBERING]
                ?.let { raw -> EpisodeNumbering.entries.firstOrNull { it.name == raw } }
                ?: EpisodeNumbering.SEASONAL
        }

    override suspend fun setPosterColumns(columns: Int) {
        context.viewPrefsStore.edit {
            it[POSTER_COLUMNS] = columns.coerceIn(MIN_POSTER_COLUMNS, MAX_POSTER_COLUMNS)
        }
    }

    override suspend fun setExpertMode(enabled: Boolean) {
        context.viewPrefsStore.edit { it[EXPERT_MODE] = enabled }
    }

    override suspend fun setEpisodeNumbering(mode: EpisodeNumbering) {
        context.viewPrefsStore.edit { it[EPISODE_NUMBERING] = mode.name }
    }

    override val uiSounds: Flow<Boolean> =
        context.viewPrefsStore.data.map { prefs -> prefs[UI_SOUNDS] ?: true }

    override suspend fun setUiSounds(enabled: Boolean) {
        context.viewPrefsStore.edit { it[UI_SOUNDS] = enabled }
    }

    private companion object {
        val DISCOVER_COLUMNS = intPreferencesKey("discover_columns")
        val DISCOVER_SORT = stringPreferencesKey("discover_sort")
        val POSTER_COLUMNS = intPreferencesKey("poster_columns")
        val EXPERT_MODE = booleanPreferencesKey("expert_mode")
        val EPISODE_NUMBERING = stringPreferencesKey("episode_numbering")
        val UI_SOUNDS = booleanPreferencesKey("ui_sounds")
    }
}
