package dev.openstream.tv.ui.discover

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
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

class DiscoverViewModelTest {

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
    fun `selects first browsable catalog and loads its first page`() = runTest {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository)
        val state = viewModel.uiState.first { it.items.isNotEmpty() }

        assertEquals("top", state.selected?.catalog?.id)
        assertEquals(5, state.items.size)
    }

    @Test
    fun `loadMore passes skip and dedupes repeated items`() = runTest {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        // Page 2: same fixture -> all duplicates -> endReached, no growth
        server.route("/catalog/movie/top/skip=5.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository)
        viewModel.uiState.first { it.items.isNotEmpty() }

        viewModel.loadMore()
        val state = viewModel.uiState.first { !it.loading }

        assertTrue(server.requestedPaths.contains("/catalog/movie/top/skip=5.json"))
        assertEquals(5, state.items.size) // duplicates dropped
        assertTrue(state.endReached)      // nothing fresh -> stop paginating
    }

    @Test
    fun `first-page failure surfaces as error not crash`() = runTest {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start() // no catalog route -> 404
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository)
        val state = viewModel.uiState.first { it.error != null }

        assertTrue(state.items.isEmpty())
    }
}
