package dev.openstream.tv.ui.home

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.ui.home.HomeViewModel.RowState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockAddonServer()
        val client = OkHttpAddonClient(
            OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        )
        addonRepository = AddonRepository(client, FakeInstalledAddonDao())
        catalogRepository = CatalogRepository(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `no addons yields empty state, not rows`() = runTest {
        val viewModel = HomeViewModel(addonRepository, catalogRepository)
        val state = viewModel.uiState.first { !it.initializing }
        assertTrue(state.rows.isEmpty())
        assertTrue(!state.hasAddons)
    }

    @Test
    fun `installed addon produces a loaded row from its browsable catalog`() = runTest {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = HomeViewModel(addonRepository, catalogRepository)
        val state = viewModel.uiState.first {
            it.rows.isNotEmpty() && it.rows.all { r -> r !is RowState.Loading }
        }

        val row = state.rows.single() as RowState.Loaded
        assertEquals("top", row.ref.catalog.id)
        assertEquals(5, row.items.size)
    }

    @Test
    fun `catalog failure becomes a visible Failed row`() = runTest {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        // no catalog route -> 404 on fetch
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = HomeViewModel(addonRepository, catalogRepository)
        val state = viewModel.uiState.first {
            it.rows.isNotEmpty() && it.rows.all { r -> r !is RowState.Loading }
        }

        assertTrue(state.rows.single() is RowState.Failed)
    }
}
