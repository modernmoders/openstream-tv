package dev.openstream.tv.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.openstream.tv.addon.AddonClient
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.autoplay.AddonAutoplayGateway
import dev.openstream.tv.autoplay.AutoplayGateway
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AutoplayModule {
    @Binds
    abstract fun autoplayGateway(impl: AddonAutoplayGateway): AutoplayGateway

    companion object {
        /**
         * Autoplay's stream fetches wait out slow addons: the interactive 15s
         * read timeout (DECISIONS #9) would kill a 20s-delayed response that
         * the §7.1 60s patience rule promises to survive (§7.2 acceptance).
         * newBuilder() shares the base client's pool and dispatcher.
         */
        @Provides
        @Singleton
        @Named("patientAddonClient")
        fun patientAddonClient(base: OkHttpClient): AddonClient =
            OkHttpAddonClient(base.newBuilder().readTimeout(50, TimeUnit.SECONDS).build())
    }
}
