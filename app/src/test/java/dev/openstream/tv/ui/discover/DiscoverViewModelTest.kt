package dev.openstream.tv.ui.discover

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.data.FakeViewPrefs
import dev.openstream.tv.data.testProgressRepository
import dev.openstream.tv.data.testSeriesWatchRepository
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
import kotlin.time.Duration.Companion.seconds
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
    fun `builds the type tree and loads the first catalog of the first type`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository, FakeViewPrefs(), testProgressRepository(), testSeriesWatchRepository())
        val state = viewModel.uiState.first { it.items.isNotEmpty() }

        // Types in first-seen manifest order; search-only catalogs contribute
        // nothing (so no "tv" from the legacy catalog).
        assertEquals(listOf("movie", "series"), state.types)
        assertEquals("movie", state.selectedType)
        assertEquals(listOf("top", "bygenre"), state.catalogs.map { it.catalog.id })
        assertEquals("top", state.selected?.catalog?.id)
        assertEquals(listOf("Action", "Comedy"), state.genres)
        assertEquals(null, state.selectedGenre) // optional genre starts on None
        assertEquals(5, state.items.size)
    }

    @Test
    fun `picking a genre refetches with the genre extra`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.route("/catalog/movie/top/genre=Action.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository, FakeViewPrefs(), testProgressRepository(), testSeriesWatchRepository())
        viewModel.uiState.first { it.items.isNotEmpty() }

        viewModel.selectGenre("Action")
        val state = viewModel.uiState.first { !it.loading && it.items.isNotEmpty() }

        assertTrue(server.requestedPaths.contains("/catalog/movie/top/genre=Action.json"))
        assertEquals("Action", state.selectedGenre)
    }

    @Test
    fun `loadMore keeps the active genre filter`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.route("/catalog/movie/top/genre=Action.json", Fixtures.load("catalog_mixed"))
        server.route("/catalog/movie/top/genre=Action&skip=5.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository, FakeViewPrefs(), testProgressRepository(), testSeriesWatchRepository())
        viewModel.uiState.first { it.items.isNotEmpty() }
        viewModel.selectGenre("Action")
        viewModel.uiState.first { !it.loading && it.items.isNotEmpty() }

        viewModel.loadMore()
        viewModel.uiState.first { !it.loading }

        assertTrue(server.requestedPaths.contains("/catalog/movie/top/genre=Action&skip=5.json"))
    }

    @Test
    fun `genre-required catalog auto-selects its first genre`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.route("/catalog/movie/bygenre/genre=Drama.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository, FakeViewPrefs(), testProgressRepository(), testSeriesWatchRepository())
        val first = viewModel.uiState.first { it.items.isNotEmpty() }

        viewModel.select(first.catalogs.first { it.catalog.id == "bygenre" })
        val state = viewModel.uiState.first { !it.loading && it.items.isNotEmpty() }

        assertTrue(server.requestedPaths.contains("/catalog/movie/bygenre/genre=Drama.json"))
        assertEquals("Drama", state.selectedGenre)
        assertTrue(state.genreRequired)
    }

    @Test
    fun `switching type resets catalog and genre to that type's first`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.route("/catalog/movie/top/genre=Action.json", Fixtures.load("catalog_mixed"))
        server.route("/catalog/series/sbygenre/genre=Drama.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository, FakeViewPrefs(), testProgressRepository(), testSeriesWatchRepository())
        viewModel.uiState.first { it.items.isNotEmpty() }
        viewModel.selectGenre("Action") // must not leak into the series fetch
        viewModel.uiState.first { !it.loading && it.items.isNotEmpty() }

        viewModel.selectType("series")
        val state = viewModel.uiState.first { !it.loading && it.items.isNotEmpty() }

        assertEquals("series", state.selectedType)
        assertEquals(listOf("sbygenre"), state.catalogs.map { it.catalog.id })
        assertEquals("sbygenre", state.selected?.catalog?.id)
        // sbygenre requires a genre -> its first option, not the stale Action.
        assertEquals("Drama", state.selectedGenre)
        assertTrue(server.requestedPaths.contains("/catalog/series/sbygenre/genre=Drama.json"))
    }

    @Test
    fun `view options flow through the prefs seam into state`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository, FakeViewPrefs(), testProgressRepository(), testSeriesWatchRepository())
        viewModel.uiState.first { it.items.isNotEmpty() }

        viewModel.setColumns(8)
        viewModel.setSort(dev.openstream.tv.data.DiscoverSortMode.TOP_RATED)
        val state = viewModel.uiState.first {
            it.view.columns == 8 && it.view.sort == dev.openstream.tv.data.DiscoverSortMode.TOP_RATED
        }

        assertEquals(8, state.view.columns)
    }

    @Test
    fun `loadMore passes skip and dedupes repeated items`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        // Page 2: same fixture -> all duplicates -> endReached, no growth
        server.route("/catalog/movie/top/skip=5.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository, FakeViewPrefs(), testProgressRepository(), testSeriesWatchRepository())
        viewModel.uiState.first { it.items.isNotEmpty() }

        viewModel.loadMore()
        val state = viewModel.uiState.first { !it.loading }

        assertTrue(server.requestedPaths.contains("/catalog/movie/top/skip=5.json"))
        assertEquals(5, state.items.size) // duplicates dropped
        assertTrue(state.endReached)      // nothing fresh -> stop paginating
    }

    @Test
    fun `first-page failure surfaces as error not crash`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start() // no catalog route -> 404
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val viewModel = DiscoverViewModel(addonRepository, catalogRepository, FakeViewPrefs(), testProgressRepository(), testSeriesWatchRepository())
        val state = viewModel.uiState.first { it.error != null }

        assertTrue(state.items.isEmpty())
    }
}
