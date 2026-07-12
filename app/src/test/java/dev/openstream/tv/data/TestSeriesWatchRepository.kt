package dev.openstream.tv.data

import dev.openstream.tv.addon.fixtures.FakeWatchProgressDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SeriesEpisodeCounts] mirroring the DataStore impl's contract. */
class FakeSeriesEpisodeCounts : SeriesEpisodeCounts {
    private val map = MutableStateFlow<Map<String, Int>>(emptyMap())

    override val counts: Flow<Map<String, Int>> = map

    override suspend fun set(metaKey: String, total: Int) {
        map.update { it + (metaKey to total) }
    }
}

/**
 * A [SeriesWatchRepository] over in-memory stores, for ViewModel tests that
 * need one but don't exercise series completion themselves.
 */
fun testSeriesWatchRepository() =
    SeriesWatchRepository(FakeSeriesEpisodeCounts(), FakeWatchProgressDao())
