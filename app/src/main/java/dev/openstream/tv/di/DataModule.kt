package dev.openstream.tv.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.openstream.tv.data.db.InstalledAddonDao
import dev.openstream.tv.data.db.OpenStreamDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): OpenStreamDatabase =
        Room.databaseBuilder(context, OpenStreamDatabase::class.java, "openstream.db")
            .build()

    @Provides
    fun installedAddonDao(db: OpenStreamDatabase): InstalledAddonDao = db.installedAddonDao()
}
