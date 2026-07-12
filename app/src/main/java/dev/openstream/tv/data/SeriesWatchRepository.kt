package dev.openstream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.data.db.WatchProgressDao
import dev.openstream.tv.data.db.toDomain
import dev.openstream.tv.domain.SeriesWatch
import dev.openstream.tv.domain.WatchProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * "12 of 220 episodes" for browse tiles (owner Round 17). The WATCHED side
 * comes straight from the progress rows; the TOTAL side an addon only tells
 * us when a series' full episode list loads (Details), so totals are cached
 * as they're seen — one small int per series, keyed like the progress map
 * ("metaType/metaId"). A series whose Details was never opened simply has no
 * total yet and shows nothing; opening it once fills the number in.
 */

/** Storage for the cached per-series episode totals; interface so ViewModel
 *  tests run on the JVM with an in-memory fake. */
interface SeriesEpisodeCounts {
    /** metaKey → total episodes, live. */
    val counts: Flow<Map<String, Int>>

    suspend fun set(metaKey: String, total: Int)
}

/**
 * A DataStore, not a Room table, on purpose: totals are a cache of addon
 * data (rebuildable by browsing), so losing them costs nothing — no schema
 * migration risk for a nice-to-have display.
 */
private val Context.seriesEpisodeCounts by preferencesDataStore("series_episode_counts")

@Singleton
class DataStoreSeriesEpisodeCounts @Inject constructor(
    @ApplicationContext private val context: Context,
) : SeriesEpisodeCounts {

    override val counts: Flow<Map<String, Int>> =
        context.seriesEpisodeCounts.data.map { prefs ->
            buildMap {
                prefs.asMap().forEach { (key, value) -> (value as? Int)?.let { put(key.name, it) } }
            }
        }

    override suspend fun set(metaKey: String, total: Int) {
        val key = intPreferencesKey(metaKey)
        context.seriesEpisodeCounts.edit { prefs ->
            if (prefs[key] != total) prefs[key] = total
        }
    }
}

@Singleton
class SeriesWatchRepository @Inject constructor(
    private val episodeCounts: SeriesEpisodeCounts,
    private val dao: WatchProgressDao,
) {

    /** Remember how many episodes [metaType]/[metaId] has, as last listed. */
    suspend fun recordEpisodeCount(metaType: String, metaId: String, totalEpisodes: Int) {
        if (totalEpisodes <= 0) return
        episodeCounts.set(ProgressRepository.metaKey(metaType, metaId), totalEpisodes)
    }

    /**
     * Live series completion per metaKey — only titles with at least one
     * watched episode AND a known total appear, so untouched artwork stays
     * pristine (watched-system principle). Re-emits as episodes finish.
     */
    fun observeSeriesWatchByMetaKey(): Flow<Map<String, SeriesWatch>> =
        combine(dao.observeAll(), episodeCounts.counts) { entities, totals ->
            val watched = watchedCountByMetaKey(entities.map { it.toDomain() })
            buildMap {
                totals.forEach { (metaKey, total) ->
                    val count = watched[metaKey] ?: return@forEach
                    if (total > 0) {
                        // An addon can shrink its episode list after episodes
                        // were watched — clamp so the display never overflows.
                        put(metaKey, SeriesWatch(count.coerceAtMost(total), total))
                    }
                }
            }
        }

    companion object {
        /** Finished-episode count per title — the pure reduction, testable. */
        fun watchedCountByMetaKey(all: List<WatchProgress>): Map<String, Int> =
            all.filter { ProgressRepository.isWatched(it) }
                .groupingBy { ProgressRepository.metaKey(it.metaType, it.metaId) }
                .eachCount()
    }
}
