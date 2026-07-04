package dev.openstream.tv.ui.search

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.ui.search.SearchViewModel.RowState
import java.util.concurrent.TimeUnit
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

class SearchViewModelTest {

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
    fun `search queries every search-capable catalog with the search extra`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        // manifest_full search-capable catalogs: top (optional search),
        // searchonly (required), legacy (extraSupported) -> 3 rows
        server.route("/catalog/movie/top/search=batman.json", Fixtures.load("catalog_mixed"))
        server.route("/catalog/series/searchonly/search=batman.json", Fixtures.load("catalog_mixed"))
        server.route("/catalog/tv/legacy/search=batman.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = SearchViewModel(addonRepository, catalogRepository)
        viewModel.search("batman")
        val state = viewModel.uiState.first {
            it.rows.isNotEmpty() && it.rows.all { r -> r !is RowState.Loading }
        }

        assertEquals(3, state.rows.size)
        assertTrue(state.rows.all { it is RowState.Loaded })
        assertTrue(
            server.requestedPaths.contains("/catalog/series/searchonly/search=batman.json")
        )
    }

    @Test
    fun `blank query is ignored`() = runTest(timeout = 60.seconds) {
        val viewModel = SearchViewModel(addonRepository, catalogRepository)
        viewModel.search("   ")
        assertTrue(!viewModel.uiState.value.searched)
    }

    @Test
    fun `failed catalog search becomes a Failed row`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top/search=x.json", Fixtures.load("catalog_mixed"))
        // searchonly + legacy have no routes -> 404 -> Failed rows
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = SearchViewModel(addonRepository, catalogRepository)
        viewModel.search("x")
        val state = viewModel.uiState.first {
            it.rows.isNotEmpty() && it.rows.all { r -> r !is RowState.Loading }
        }

        assertEquals(1, state.rows.count { it is RowState.Loaded })
        assertEquals(2, state.rows.count { it is RowState.Failed })
    }
}
