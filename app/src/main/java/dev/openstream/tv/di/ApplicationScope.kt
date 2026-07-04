package dev.openstream.tv.di

import javax.inject.Qualifier

/** Marks the app-lifetime CoroutineScope (provided in [DataModule]). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
