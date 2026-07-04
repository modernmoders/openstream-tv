package dev.openstream.tv.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {

    /**
     * Unordered/unfiltered on purpose: resume-eligibility and ordering live
     * in ProgressRepository as pure functions so they're unit-testable
     * without SQLite.
     */
    @Query("SELECT * FROM watch_progress")
    fun observeAll(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE sourceKind = :sourceKind AND externalId = :externalId")
    suspend fun get(sourceKind: String, externalId: String): WatchProgressEntity?

    @Upsert
    suspend fun upsert(entity: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE sourceKind = :sourceKind AND externalId = :externalId")
    suspend fun delete(sourceKind: String, externalId: String)
}
