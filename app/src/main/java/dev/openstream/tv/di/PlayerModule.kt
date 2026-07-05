package dev.openstream.tv.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.openstream.tv.player.ExternalPlayerLauncher
import dev.openstream.tv.player.ExternalPlayerPort

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    abstract fun externalPlayerPort(impl: ExternalPlayerLauncher): ExternalPlayerPort
}
