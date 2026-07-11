package dev.openstream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.addon.CatalogRepository.CatalogRef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The user's home-row customization (Phase 4 row/catalog manager). All maps
 * are keyed by [CatalogRef.key]; entries for rows that no longer exist (an
 * addon was removed or its manifest changed) are simply ignored when applied,
 * so we never have to garbage-collect them eagerly.
 */
@Serializable
data class HomeRowPrefs(
    /** Row keys in the user's order. Rows not listed keep addon order after these. */
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    /** Custom row titles; absent key = the catalog's own name. */
    val renames: Map<String, String> = emptyMap(),
)

/** One home row after prefs are applied — what Home and the manager render. */
data class HomeRow(
    val ref: CatalogRef,
    /** Custom name if renamed, otherwise the catalog's own title. */
    val title: String,
    val hidden: Boolean,
)

/**
 * Applies the user's customization to the natural (addon-order, §4.1.7) row
 * list: user-ordered rows first, then the rest in natural order — so a brand
 * new catalog still shows up, at the end, instead of vanishing. Hidden rows
 * are kept (flagged) so the manager screen can list them; Home filters.
 */
fun HomeRowPrefs.applyTo(refs: List<CatalogRef>): List<HomeRow> {
    val byKey = refs.associateBy { it.key }
    val ordered = order.mapNotNull { byKey[it] }
    val orderedKeys = ordered.mapTo(mutableSetOf()) { it.key }
    val rest = refs.filter { it.key !in orderedKeys }
    return (ordered + rest).map { ref ->
        HomeRow(
            ref = ref,
            title = renames[ref.key]?.takeIf { it.isNotBlank() } ?: ref.title,
            hidden = ref.key in hidden,
        )
    }
}

/**
 * Default Home order (owner round 14 #14): personalized recommendation rows
 * ("Trakt Recommendations" and kin) jump to the very top, above the other
 * catalogs — but ONLY while the user hasn't pinned an order of their own in
 * the row manager; an explicit order is exactly the tool for overriding this.
 * Matched on the displayed title: the row's identity ([CatalogRef.key]) is an
 * opaque addon/catalog id, while every recommendations catalog the family's
 * addons declare says "recommend" in its name.
 */
fun List<HomeRow>.withRecommendationsFirst(hasUserOrder: Boolean): List<HomeRow> {
    if (hasUserOrder) return this
    val (recs, rest) = partition { it.isRecommendations() }
    return recs + rest
}

/** The pin test behind [withRecommendationsFirst]; Home also counts these. */
fun HomeRow.isRecommendations(): Boolean = title.contains("recommend", ignoreCase = true)

/** Interface so ViewModels stay JVM-testable with an in-memory fake. */
interface HomeRowPrefsStore {
    val prefs: Flow<HomeRowPrefs>

    /** Pins the complete row order (the manager saves the full list per move). */
    suspend fun setOrder(keys: List<String>)
    suspend fun setHidden(key: String, hidden: Boolean)

    /** Null restores the catalog's own name. Callers pass a cleaned name. */
    suspend fun setRename(key: String, name: String?)
}

private val Context.homeRowPrefsStore by preferencesDataStore("home_row_prefs")

/**
 * Stored as one JSON blob under a single preference key: the three fields
 * change together from one screen, and a schema change degrades to defaults
 * instead of a migration.
 */
@Singleton
class DataStoreHomeRowPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomeRowPrefsStore {

    private val json = Json { ignoreUnknownKeys = true }

    override val prefs: Flow<HomeRowPrefs> =
        context.homeRowPrefsStore.data.map { store -> decode(store[BLOB]) }

    override suspend fun setOrder(keys: List<String>) =
        update { it.copy(order = keys) }

    override suspend fun setHidden(key: String, hidden: Boolean) =
        update { it.copy(hidden = if (hidden) it.hidden + key else it.hidden - key) }

    override suspend fun setRename(key: String, name: String?) =
        update {
            it.copy(
                renames = if (name == null) it.renames - key
                else it.renames + (key to name)
            )
        }

    /** Read-modify-write inside a single edit — atomic under DataStore. */
    private suspend fun update(transform: (HomeRowPrefs) -> HomeRowPrefs) {
        context.homeRowPrefsStore.edit { store ->
            store[BLOB] = json.encodeToString(HomeRowPrefs.serializer(), transform(decode(store[BLOB])))
        }
    }

    private fun decode(raw: String?): HomeRowPrefs =
        raw?.let { runCatching { json.decodeFromString(HomeRowPrefs.serializer(), it) }.getOrNull() }
            ?: HomeRowPrefs()

    private companion object {
        val BLOB = stringPreferencesKey("home_rows")
    }
}
