package dev.openstream.tv.data

import dev.openstream.tv.addon.fixtures.FakeWatchProgressDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * A [ProgressRepository] over an in-memory DAO for ViewModel tests that need
 * one but don't exercise progress themselves (Discover/Search tile indicators).
 */
fun testProgressRepository() =
    ProgressRepository(FakeWatchProgressDao(), CoroutineScope(Dispatchers.Unconfined))
