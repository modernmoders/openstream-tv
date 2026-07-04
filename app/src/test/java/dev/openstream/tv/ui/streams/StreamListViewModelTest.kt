package dev.openstream.tv.ui.streams

import androidx.lifecycle.SavedStateHandle
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.StreamRepository
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.ui.streams.StreamListViewModel.GroupState
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
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

class StreamListViewModelTest {

    private lateinit var server: MockAddonServer
    private lateinit var addonRepository: AddonRepository
    private lateinit var streamRepository: StreamRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockAddonServer()
        val client = OkHttpAddonClient(
            OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        )
        addonRepository = AddonRepository(client, FakeInstalledAddonDao())
        streamRepository = StreamRepository(client, addonRepository)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun viewModel(type: String, videoId: String) = StreamListViewModel(
        streamRepository,
        dev.openstream.tv.player.CurrentPlayback(),
        SavedStateHandle(mapOf("type" to type, "videoId" to videoId, "title" to "T")),
    )

    @Test
    fun `groups follow addon order and keep stream order untouched`() = runTest(timeout = 60.seconds) {
        // Two instances of the same addon (the AIOStreams case, §4.2)
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.route("/b/manifest.json", Fixtures.load("manifest_full"))
        server.route("/a/stream/movie/tt1254207.json", Fixtures.load("streams_full"))
        server.route("/b/stream/movie/tt1254207.json", Fixtures.load("streams_empty"))
        server.start()
        val a = server.url("/a/manifest.json")
        val b = server.url("/b/manifest.json")
        addonRepository.install(a).getOrThrow()
        addonRepository.install(b).getOrThrow()

        val vm = viewModel("movie", "tt1254207")
        val state = vm.uiState.first {
            it.groups.isNotEmpty() && it.groups.all { g -> g !is GroupState.Loading }
        }

        assertEquals(listOf(a, b), state.groups.map { it.addon.manifestUrl })
        val loaded = state.groups[0] as GroupState.Loaded
        // §4.1.7: exactly the fixture order — 4K first, torrent third, bare last
        assertEquals("Fixture 4K", loaded.streams[0].name)
        assertEquals("Torrent Source", loaded.streams[2].name)
        assertTrue((state.groups[1] as GroupState.Loaded).streams.isEmpty())
    }

    @Test
    fun `addon not declaring streams for the type is not queried`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        // manifest_full declares streams only for movie/series — "tv" is out
        val vm = viewModel("tv", "fix:tv:1")
        val state = vm.uiState.first { !it.initializing }

        assertTrue(state.groups.isEmpty())
        assertTrue(server.requestedPaths.none { it.startsWith("/stream/") })
    }

    @Test
    fun `failed addon becomes a visible Failed group`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start() // no stream route -> 404
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val vm = viewModel("movie", "tt1254207")
        val state = vm.uiState.first {
            it.groups.isNotEmpty() && it.groups.all { g -> g !is GroupState.Loading }
        }

        assertTrue(state.groups.single() is GroupState.Failed)
    }
}
