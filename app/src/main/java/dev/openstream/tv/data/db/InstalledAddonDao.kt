package dev.openstream.tv.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledAddonDao {

    @Query("SELECT * FROM installed_addon ORDER BY sortOrder")
    fun observeAll(): Flow<List<InstalledAddonEntity>>

    @Query("SELECT * FROM installed_addon ORDER BY sortOrder")
    suspend fun getAll(): List<InstalledAddonEntity>

    @Query("SELECT * FROM installed_addon WHERE manifestUrl = :manifestUrl")
    suspend fun get(manifestUrl: String): InstalledAddonEntity?

    @Upsert
    suspend fun upsert(entity: InstalledAddonEntity)

    @Query("DELETE FROM installed_addon WHERE manifestUrl = :manifestUrl")
    suspend fun delete(manifestUrl: String)

    @Query("UPDATE installed_addon SET enabled = :enabled WHERE manifestUrl = :manifestUrl")
    suspend fun setEnabled(manifestUrl: String, enabled: Boolean)

    @Query("UPDATE installed_addon SET sortOrder = :sortOrder WHERE manifestUrl = :manifestUrl")
    suspend fun setSortOrder(manifestUrl: String, sortOrder: Int)
}
