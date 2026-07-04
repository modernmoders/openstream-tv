package dev.openstream.tv.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.openstream.tv.data.db.InstalledAddonDao
import dev.openstream.tv.data.db.OpenStreamDatabase
import dev.openstream.tv.data.db.WatchProgressDao
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** v1 → v2: watch_progress table (Phase 2 unit 4). Must match the entity exactly. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `watch_progress` (
                `sourceKind` TEXT NOT NULL,
                `externalId` TEXT NOT NULL,
                `metaId` TEXT NOT NULL,
                `metaType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `poster` TEXT,
                `positionMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`sourceKind`, `externalId`)
            )
            """.trimIndent()
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): OpenStreamDatabase =
        Room.databaseBuilder(context, OpenStreamDatabase::class.java, "openstream.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun installedAddonDao(db: OpenStreamDatabase): InstalledAddonDao = db.installedAddonDao()

    @Provides
    fun watchProgressDao(db: OpenStreamDatabase): WatchProgressDao = db.watchProgressDao()

    /**
     * Scope for writes that must outlive a ViewModel (e.g. saving the final
     * playback position from onCleared()). SupervisorJob: one failed write
     * must not kill the scope.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
