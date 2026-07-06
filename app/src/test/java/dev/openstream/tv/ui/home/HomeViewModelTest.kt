package dev.openstream.tv.ui.home

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.MetaRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.FakeWatchProgressDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.data.FakeHomeRowPrefsStore
import dev.openstream.tv.data.FakeViewPrefs
import dev.openstream.tv.data.HomeRowPrefs
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.home.HomeViewModel.RowState
import androidx.lifecycle.viewModelScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private lateinit var client: OkHttpAddonClient
    private lateinit var addonRepository: AddonRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var progressRepository: ProgressRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockAddonServer()
        client = OkHttpAddonClient(
            OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        )
        addonRepository = AddonRepository(client, FakeInstalledAddonDao())
        catalogRepository = CatalogRepository(client)
        progressRepository =
            ProgressRepository(FakeWatchProgressDao(), CoroutineScope(Dispatchers.Unconfined))
    }

    /**
     * "Cinemeta" is the same mock server under a /cine prefix (like
     * MetaRepositoryTest); tests that expect no prefetch traffic keep the
     * unroutable default so a stray fetch fails fast and silently.
     */
    private val viewModels = mutableListOf<HomeViewModel>()

    private fun viewModel(
        cinemetaBase: String = "http://localhost:1",
        rowPrefs: FakeHomeRowPrefsStore = FakeHomeRowPrefsStore(),
        viewPrefs: FakeViewPrefs = FakeViewPrefs(),
    ) = HomeViewModel(
        addonRepository, catalogRepository, progressRepository,
        MetaRepository(client, addonRepository, cinemetaBase),
        rowPrefs,
        viewPrefs,
    ).also { viewModels += it }

    @After
    fun tearDown() {
        // Order matters (the "1 in 3 runs" Main-dispatcher flake, session 13):
        // tests assert on the FIRST interesting state, leaving the fan-out's
        // other fetches in flight. Kill the server (fails them fast), cancel
        // every ViewModel's scope, and only THEN resetMain — otherwise a
        // straggler resumes on Dispatchers.Main after reset and poisons
        // whichever test runs next.
        server.shutdown()
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `no addons yields empty state, not rows`() = runTest(timeout = 60.seconds) {
        val viewModel = viewModel()
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

        val viewModel = viewModel()
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

        val viewModel = viewModel()
        val state = viewModel.uiState.first {
            it.rows.isNotEmpty() && it.rows.all { r -> r !is RowState.Loading }
        }

        assertTrue(state.rows.single() is RowState.Failed)
    }

    @Test
    fun `hidden row is dropped before the fan-out — its catalog is never fetched`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()
        val rowKey = "${server.url("/manifest.json")}|movie|top"

        val viewModel = viewModel(
            rowPrefs = FakeHomeRowPrefsStore(HomeRowPrefs(hidden = setOf(rowKey)))
        )
        val state = viewModel.uiState.first { !it.initializing }

        assertTrue(state.rows.isEmpty())
        assertTrue(state.hasAddons) // hidden ≠ uninstalled: no empty-state prompt
        // Fetches ride real OkHttp threads; give a buggy one time to arrive.
        withContext(Dispatchers.Default) { delay(300) }
        assertTrue(server.requestedPaths.none { it.startsWith("/catalog/") })
    }

    @Test
    fun `renamed row carries the custom title, and live prefs edits re-apply`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()
        val rowKey = "${server.url("/manifest.json")}|movie|top"
        val rowPrefs = FakeHomeRowPrefsStore(HomeRowPrefs(renames = mapOf(rowKey to "Films")))

        val viewModel = viewModel(rowPrefs = rowPrefs)
        val loaded = viewModel.uiState.first {
            it.rows.isNotEmpty() && it.rows.all { r -> r !is RowState.Loading }
        }
        assertEquals("Films", loaded.rows.single().title)

        // The row manager edits prefs while Home is alive — combine re-applies.
        rowPrefs.setRename(rowKey, null)
        val reverted = viewModel.uiState.first {
            it.rows.all { r -> r !is RowState.Loading && r.title != "Films" }
        }
        assertTrue(reverted.rows.single().title.isNotBlank())
    }

    @Test
    fun `user row order overrides addon order`() = runTest(timeout = 60.seconds) {
        // Two addons on one server under path prefixes (baseUrl = manifest
        // URL minus /manifest.json), one catalog each.
        for (prefix in listOf("/a", "/b")) {
            server.route("$prefix/manifest.json", Fixtures.load("manifest_full"))
            server.route("$prefix/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        }
        server.start()
        addonRepository.install(server.url("/a/manifest.json")).getOrThrow()
        addonRepository.install(server.url("/b/manifest.json")).getOrThrow()
        val keyA = "${server.url("/a/manifest.json")}|movie|top"
        val keyB = "${server.url("/b/manifest.json")}|movie|top"

        val viewModel = viewModel(
            rowPrefs = FakeHomeRowPrefsStore(HomeRowPrefs(order = listOf(keyB)))
        )
        // Wait for BOTH fetches to land: leaving them in flight lets an OkHttp
        // callback resume Dispatchers.Main after resetMain() and fail later tests.
        val state = viewModel.uiState.first {
            it.rows.size == 2 && it.rows.all { r -> r !is RowState.Loading }
        }

        // B is pinned first by the user; A follows in natural addon order.
        assertEquals(listOf(keyB, keyA), state.rows.map { it.ref.key })
    }

    @Test
    fun `poster density setting flows into home state live`() = runTest(timeout = 60.seconds) {
        val viewPrefs = FakeViewPrefs()
        val viewModel = viewModel(viewPrefs = viewPrefs)
        assertEquals(6, viewModel.uiState.first { !it.initializing }.columns)

        viewPrefs.setPosterColumns(8)
        assertEquals(8, viewModel.uiState.first { it.columns != 6 }.columns)

        viewPrefs.setPosterColumns(99) // out of range → clamped by the store
        assertEquals(8, viewModel.uiState.value.columns)
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

        val viewModel = viewModel()
        val state = viewModel.uiState.first { it.continueWatching.isNotEmpty() }

        assertEquals(listOf("tt2", "tt1"), state.continueWatching.map { it.ref.externalId })
    }

    @Test
    fun `prefetches meta for the newest two continue watching items only`() = runTest(timeout = 60.seconds) {
        fun progress(id: String, updatedAt: Long) = WatchProgress(
            ref = MediaRef.addon(id),
            metaId = id, metaType = "movie", title = id, poster = null,
            positionMs = 300_000, durationMs = 1_200_000, updatedAt = updatedAt,
        )
        // No installed addon declares meta -> the tt ids fall through to
        // "Cinemeta", played by the mock server under /cine.
        server.route("/cine/meta/movie/tt2.json", Fixtures.load("meta_series"))
        server.route("/cine/meta/movie/tt3.json", Fixtures.load("meta_series"))
        server.start()
        progressRepository.save(progress("tt1", updatedAt = 1))
        progressRepository.save(progress("tt2", updatedAt = 2))
        progressRepository.save(progress("tt3", updatedAt = 3))

        val viewModel = viewModel(cinemetaBase = server.url("/cine"))
        viewModel.uiState.first { it.continueWatching.size == 3 }

        // Prefetch rides real OkHttp threads; poll on a real dispatcher
        // (runTest's virtual clock would skip the waiting).
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                while (!server.requestedPaths.contains("/cine/meta/movie/tt2.json") ||
                    !server.requestedPaths.contains("/cine/meta/movie/tt3.json")
                ) {
                    delay(25)
                }
                // Settle window: a buggy third fetch would arrive about now.
                delay(200)
            }
        }
        assertTrue(server.requestedPaths.none { it.endsWith("/tt1.json") })
    }
}
