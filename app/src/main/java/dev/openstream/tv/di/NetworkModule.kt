package dev.openstream.tv.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.openstream.tv.addon.AddonClient
import dev.openstream.tv.addon.OkHttpAddonClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * One shared OkHttpClient (one connection pool / dispatcher for the app).
     * 15s call timeout matches the per-addon budget in MASTER_PLAN §4.1.5 —
     * a slow addon fails alone; the fan-out renders whatever else arrived.
     */
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AddonModule {
    @Binds
    @Singleton
    abstract fun addonClient(impl: OkHttpAddonClient): AddonClient
}
