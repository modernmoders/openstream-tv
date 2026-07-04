package dev.openstream.tv.addon.fixtures

import dev.openstream.tv.data.db.InstalledAddonDao
import dev.openstream.tv.data.db.InstalledAddonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-in for the Room DAO, shared by repository/viewmodel tests. */
class FakeInstalledAddonDao : InstalledAddonDao {
    private val state = MutableStateFlow<Map<String, InstalledAddonEntity>>(emptyMap())

    override fun observeAll(): Flow<List<InstalledAddonEntity>> =
        state.map { it.values.sortedBy { e -> e.sortOrder } }

    override suspend fun getAll(): List<InstalledAddonEntity> =
        state.value.values.sortedBy { it.sortOrder }

    override suspend fun get(manifestUrl: String): InstalledAddonEntity? =
        state.value[manifestUrl]

    override suspend fun upsert(entity: InstalledAddonEntity) {
        state.value = state.value + (entity.manifestUrl to entity)
    }

    override suspend fun delete(manifestUrl: String) {
        state.value = state.value - manifestUrl
    }

    override suspend fun setEnabled(manifestUrl: String, enabled: Boolean) {
        state.value[manifestUrl]?.let { upsert(it.copy(enabled = enabled)) }
    }

    override suspend fun setSortOrder(manifestUrl: String, sortOrder: Int) {
        state.value[manifestUrl]?.let { upsert(it.copy(sortOrder = sortOrder)) }
    }
}
