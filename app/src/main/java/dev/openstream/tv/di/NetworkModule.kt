package dev.openstream.tv.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.openstream.tv.addon.AddonClient
import dev.openstream.tv.addon.AddonHttpCache
import dev.openstream.tv.addon.OkHttpAddonClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * One shared OkHttpClient (one connection pool / dispatcher for the app).
     *
     * The §4.1.5 15s per-addon budget is enforced with connect/read timeouts,
     * deliberately NOT callTimeout: callTimeout counts dispatcher QUEUE time,
     * and a single AIOMetadata instance exposes 60+ catalogs — the home
     * fan-out fills the queue, and queued-but-healthy calls would die at 15s
     * (found live: Discover showed "couldn't reach the addon" for an addon
     * that answered curl in 3s). Read timeout only fires when the server
     * actually stalls.
     *
     * Per-host concurrency is raised from OkHttp's default 5: many catalogs
     * per single addon host is this app's normal traffic shape.
     *
     * Catalog/meta responses disk-cache for 30 min (AddonHttpCache) so a
     * relaunch renders from disk instead of refetching 60+ catalogs — the
     * owner's onn boxes are 32-bit with 2–3 GB RAM and network is the
     * dominant cold-start cost.
     */
    @Provides
    @Singleton
    fun okHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val policy = AddonHttpCache()
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cache(AddonHttpCache.diskCache(context.cacheDir))
            .addInterceptor(policy.staleIfOffline)
            .addNetworkInterceptor(policy.responsePolicy)
            .apply {
                dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = 32
                        maxRequestsPerHost = 16
                    }
                )
            }
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AddonModule {
    @Binds
    @Singleton
    abstract fun addonClient(impl: OkHttpAddonClient): AddonClient

    companion object {
        /**
         * Metadata fallback for IMDb ids (MASTER_PLAN §4.1.6). The one addon
         * the app knows out of the box; everything else is user-installed.
         */
        @Provides
        @javax.inject.Named("cinemetaBaseUrl")
        fun cinemetaBaseUrl(): String = "https://v3-cinemeta.strem.io"
    }
}
