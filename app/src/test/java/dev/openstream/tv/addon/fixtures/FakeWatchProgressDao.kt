package dev.openstream.tv.addon.fixtures

import dev.openstream.tv.data.db.WatchProgressDao
import dev.openstream.tv.data.db.WatchProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory WatchProgressDao mirroring the Room DAO's contract. */
class FakeWatchProgressDao : WatchProgressDao {

    private val rows =
        MutableStateFlow<Map<Pair<String, String>, WatchProgressEntity>>(emptyMap())

    override fun observeAll(): Flow<List<WatchProgressEntity>> =
        rows.map { it.values.toList() }

    override suspend fun get(sourceKind: String, externalId: String): WatchProgressEntity? =
        rows.value[sourceKind to externalId]

    override suspend fun upsert(entity: WatchProgressEntity) {
        rows.update { it + ((entity.sourceKind to entity.externalId) to entity) }
    }

    override suspend fun delete(sourceKind: String, externalId: String) {
        rows.update { it - (sourceKind to externalId) }
    }
}
