package dev.openstream.tv.ui.streams

import androidx.lifecycle.SavedStateHandle
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.StreamRepository
import dev.openstream.tv.addon.SubtitleRepository
import dev.openstream.tv.autoplay.AutoplayController
import dev.openstream.tv.autoplay.AutoplayGateway
import dev.openstream.tv.autoplay.AutoplayOriginHolder
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.FakeWatchProgressDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.data.FakePlaybackPrefs
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import android.content.Intent
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.ExternalLaunch
import dev.openstream.tv.player.ExternalPlayerPort
import dev.openstream.tv.ui.streams.StreamListViewModel.GroupState
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamListViewModelTest {

    private lateinit var server: MockAddonServer
    private lateinit var addonRepository: AddonRepository
    private lateinit var streamRepository: StreamRepository
    private lateinit var subtitleRepository: SubtitleRepository
    private lateinit var progressRepository: ProgressRepository
    private lateinit var currentPlayback: CurrentPlayback

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockAddonServer()
        val client = OkHttpAddonClient(
            OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        )
        addonRepository = AddonRepository(client, FakeInstalledAddonDao())
        streamRepository = StreamRepository(client, addonRepository)
        subtitleRepository = SubtitleRepository(client, addonRepository)
        progressRepository =
            ProgressRepository(FakeWatchProgressDao(), CoroutineScope(Dispatchers.Unconfined))
        currentPlayback = CurrentPlayback()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private val autoplayOrigin = AutoplayOriginHolder()

    /** No players installed — external paths are exercised by ExternalPlayersTest. */
    private val noExternalPlayers = object : ExternalPlayerPort {
        override fun installedPlayers() = emptyList<ExternalPlayerPort.Choice>()
        override fun intentFor(launch: ExternalLaunch) = error("no external launches in these tests")
        override fun resultExtras(data: Intent?) = emptyMap<String, Any?>()
    }

    private val alternatives = dev.openstream.tv.player.StreamAlternatives()

    private fun viewModel(
        type: String,
        videoId: String,
        playbackPrefs: FakePlaybackPrefs = FakePlaybackPrefs(),
    ) = StreamListViewModel(
        streamRepository,
        subtitleRepository,
        currentPlayback,
        progressRepository,
        autoplayOrigin,
        noExternalPlayers,
        AutoplayController(NeverAutoplayGateway),
        alternatives,
        dev.openstream.tv.player.DecoderCapabilities(),
        playbackPrefs,
        SavedStateHandle(mapOf("type" to type, "videoId" to videoId, "title" to "T")),
    )

    /** Inert gateway: these tests never reach the §7.1.6 external Up Next flow. */
    private object NeverAutoplayGateway : AutoplayGateway {
        override suspend fun resolveMeta(type: String, id: String) = null
        override suspend fun streamAddons(type: String, videoId: String) =
            emptyList<dev.openstream.tv.addon.InstalledAddon>()
        override suspend fun fetchStreams(
            addon: dev.openstream.tv.addon.InstalledAddon, type: String, videoId: String,
        ) = emptyList<dev.openstream.tv.addon.Stream>()
    }

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

    @Test
    fun `existing progress surfaces resume position and stage applies it`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/stream/movie/tt1254207.json", Fixtures.load("streams_full"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()
        progressRepository.save(
            WatchProgress(
                ref = MediaRef.addon("tt1254207"),
                metaId = "tt1254207", metaType = "movie", title = "T", poster = null,
                positionMs = 120_000, durationMs = 600_000, updatedAt = 1,
            )
        )

        val vm = viewModel("movie", "tt1254207")
        val state = vm.uiState.first {
            it.resumePositionMs != null && it.groups.any { g -> g is GroupState.Loaded }
        }

        assertEquals(120_000L, state.resumePositionMs)

        val group = state.groups.first() as GroupState.Loaded
        assertTrue(vm.stage(group.addon, group.streams.first(), state.resumePositionMs!!))
        val request = currentPlayback.request!!
        assertEquals(120_000L, request.source.startPositionMs)
        assertEquals(MediaRef.addon("tt1254207"), request.mediaRef)
        // Autoplay's ranking context follows the staged stream (§7.1)
        assertEquals(group.addon.manifestUrl, autoplayOrigin.origin?.addonUrl)
    }

    @Test
    fun `no progress means no resume offer and stage starts from zero`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.route("/stream/movie/tt1254207.json", Fixtures.load("streams_full"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val vm = viewModel("movie", "tt1254207")
        val state = vm.uiState.first {
            !it.initializing && it.groups.any { g -> g is GroupState.Loaded }
        }

        assertNull(state.resumePositionMs)

        val group = state.groups.first() as GroupState.Loaded
        assertTrue(vm.stage(group.addon, group.streams.first()))
        assertEquals(0L, currentPlayback.request!!.source.startPositionMs)
    }

    @Test
    fun `auto-play pref fires once with the first playable stream and resume position`() =
        runTest(timeout = 60.seconds) {
            server.route("/manifest.json", Fixtures.load("manifest_full"))
            server.route("/stream/movie/tt1254207.json", Fixtures.load("streams_full"))
            server.start()
            addonRepository.install(server.url("/manifest.json")).getOrThrow()
            progressRepository.save(
                WatchProgress(
                    ref = MediaRef.addon("tt1254207"),
                    metaId = "tt1254207", metaType = "movie", title = "T", poster = null,
                    positionMs = 120_000, durationMs = 600_000, updatedAt = 1,
                )
            )
            val prefs = FakePlaybackPrefs().apply { autoPlayState.value = true }

            val vm = viewModel("movie", "tt1254207", prefs)
            val auto = vm.autoStart.first()

            assertEquals("Fixture 4K", auto.stream.name) // §4.1.7 top of the list
            assertEquals(120_000L, auto.startPositionMs) // hands-free resume
            // The shared walk list is ready for "Try another server", and
            // staging the auto-start stream points the walk at it.
            assertTrue(vm.stage(auto.addon, auto.stream, auto.startPositionMs))
            assertEquals(0, alternatives.currentIndex)
            assertTrue(alternatives.hasNext())
        }

}
