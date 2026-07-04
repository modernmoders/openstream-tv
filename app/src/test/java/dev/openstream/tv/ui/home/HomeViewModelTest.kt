package dev.openstream.tv.ui.home

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.FakeWatchProgressDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.home.HomeViewModel.RowState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fan-out behavior (§4.1.5/§4.1.8): every browsable catalog becomes a row;
 * each row loads independently; failures become visible Failed rows.
 */
class HomeViewModelTest {

    private lateinit var server: MockAddonServer
    private lateinit var addonRepository: AddonRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var progressRepository: ProgressRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockAddonServer()
        val client = OkHttpAddonClient(
            OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        )
        addonRepository = AddonRepository(client, FakeInstalledAddonDao())
        catalogRepository = CatalogRepository(client)
        progressRepository =
            ProgressRepository(FakeWatchProgressDao(), CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `no addons yields empty state, not rows`() = runTest(timeout = 60.seconds) {
        val viewModel = HomeViewModel(addonRepository, catalogRepository, progressRepository)
        val state = viewModel.uiState.first { !it.initializing }
        assertTrue(state.rows.isEmpty())
        assertTrue(!state.hasAddons)
    }

    @Test
    fun `installed addon produces a loaded row from its browsable catalog`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = HomeViewModel(addonRepository, catalogRepository, progressRepository)
        val state = viewModel.uiState.first {
            it.rows.isNotEmpty() && it.rows.all { r -> r !is RowState.Loading }
        }

        val row = state.rows.single() as RowState.Loaded
        assertEquals("top", row.ref.catalog.id)
        assertEquals(5, row.items.size)
    }

    @Test
    fun `catalog failure becomes a visible Failed row`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        // no catalog route -> 404 on fetch
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = HomeViewModel(addonRepository, catalogRepository, progressRepository)
        val state = viewModel.uiState.first {
            it.rows.isNotEmpty() && it.rows.all { r -> r !is RowState.Loading }
        }

        assertTrue(state.rows.single() is RowState.Failed)
    }

    @Test
    fun `resumable progress appears as continue watching, newest first`() = runTest(timeout = 60.seconds) {
        fun progress(id: String, updatedAt: Long) = WatchProgress(
            ref = MediaRef.addon(id),
            metaId = id, metaType = "movie", title = id, poster = null,
            positionMs = 300_000, durationMs = 1_200_000, updatedAt = updatedAt,
        )
        progressRepository.save(progress("tt1", updatedAt = 1))
        progressRepository.save(progress("tt2", updatedAt = 2))

        val viewModel = HomeViewModel(addonRepository, catalogRepository, progressRepository)
        val state = viewModel.uiState.first { it.continueWatching.isNotEmpty() }

        assertEquals(listOf("tt2", "tt1"), state.continueWatching.map { it.ref.externalId })
    }
}
