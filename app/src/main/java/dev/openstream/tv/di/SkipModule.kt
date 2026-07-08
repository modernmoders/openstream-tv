package dev.openstream.tv.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.openstream.tv.player.skip.AniSkipClient
import dev.openstream.tv.player.skip.AnimeMalIdResolver
import dev.openstream.tv.player.skip.KitsuAnimeMalIdResolver
import dev.openstream.tv.player.skip.OkHttpAniSkipClient
import javax.inject.Singleton

/** Wires the anime intro/credits skip (AniSkip) implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SkipModule {
    @Binds
    @Singleton
    abstract fun aniSkipClient(impl: OkHttpAniSkipClient): AniSkipClient

    @Binds
    @Singleton
    abstract fun animeMalIdResolver(impl: KitsuAnimeMalIdResolver): AnimeMalIdResolver
}
