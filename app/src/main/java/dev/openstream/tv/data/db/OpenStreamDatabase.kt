package dev.openstream.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App database. Version bumps require a Room migration — keep entities
 * boring; prefer JSON-text columns for protocol payloads (see
 * [InstalledAddonEntity]).
 */
@Database(
    entities = [InstalledAddonEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OpenStreamDatabase : RoomDatabase() {
    abstract fun installedAddonDao(): InstalledAddonDao
}
