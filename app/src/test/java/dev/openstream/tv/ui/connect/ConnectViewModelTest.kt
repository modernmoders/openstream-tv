package dev.openstream.tv.ui.connect

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.ProfileInstaller
import dev.openstream.tv.addon.SetupNameLookup
import dev.openstream.tv.addon.SetupProfileClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.FakeProfileSyncPrefs
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.data.SetupConfig
import dev.openstream.tv.ui.connect.ConnectViewModel.UiState
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

/**
 * One-step setup state machine (owner directive 2026-07-06, simplified round
 * 2): a typed name walks name → lookup → install → done automatically, with
 * no accept screen and without the person ever seeing a URL. The mock setup
 * site answers exactly like the regenerated index.php's api mode.
 */
class ConnectViewModelTest {

    private lateinit var server: MockAddonServer
    private lateinit var dao: FakeInstalledAddonDao
    private lateinit var profileSyncPrefs: FakeProfileSyncPrefs

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockAddonServer()
        dao = FakeInstalledAddonDao()
        profileSyncPrefs = FakeProfileSyncPrefs()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun viewModel(): ConnectViewModel {
        val http = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        val client = OkHttpAddonClient(http)
        val repository = AddonRepository(client, dao)
        val config = SetupConfig(server.url("/setup/"), "TestBrand")
        return ConnectViewModel(
            SetupNameLookup(http, config),
            ProfileInstaller(client, SetupProfileClient(http), repository, profileSyncPrefs),
            config,
        )
    }

    private suspend fun ConnectViewModel.settled(): UiState =
        state.first { it !is UiState.Busy }

    @Test
    fun `typed name installs everything without a visible url`() = runTest(timeout = 60.seconds) {
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        val manifestUrl = server.url("/a/manifest.json")
        server.route(
            "/profile.json",
            """{"openstream":1,"name":"adam savoy","addons":[{"name":"Movies","url":"$manifestUrl"}]}"""
        )
        server.route(
            "/setup/",
            """{"ok":true,"name":"Adam Savoy","link":"${server.url("/profile.json")}"}"""
        )

        val vm = viewModel()
        // No accept screen: a typed name installs on its own (round 2).
        vm.submitName("adam s")
        val done = vm.settled() as UiState.Done
        assertEquals("Adam Savoy", done.displayName)
        assertEquals(1, done.count)
        assertEquals(listOf(manifestUrl), dao.getAll().map { it.manifestUrl })
        // The setup link is remembered → ProfileSync manages this box from now on.
        assertEquals(server.url("/profile.json"), profileSyncPrefs.link!!.url)
    }

    @Test
    fun `ambiguous name asks which one and a pick resolves it`() = runTest(timeout = 60.seconds) {
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        server.route(
            "/profile.json",
            """{"openstream":1,"addons":[{"name":"A","url":"${server.url("/a/manifest.json")}"}]}"""
        )
        server.route("/setup/", """{"ok":false,"choices":["Myles Manuel","Myles Mobile"]}""")

        val vm = viewModel()
        vm.submitName("myles")
        val which = vm.settled() as UiState.WhichOne
        assertEquals(listOf("Myles Manuel", "Myles Mobile"), which.choices)

        // The pick re-asks the site by full name — swap the canned answer.
        server.route(
            "/setup/",
            """{"ok":true,"name":"Myles Manuel","link":"${server.url("/profile.json")}"}"""
        )
        vm.choose("Myles Manuel")
        // The pick runs the same automatic install path straight to Done.
        assertEquals("Myles Manuel", (vm.settled() as UiState.Done).displayName)
    }

    @Test
    fun `unknown name comes back as a friendly retry`() = runTest(timeout = 60.seconds) {
        server.route("/setup/", """{"ok":false,"error":"No match — check the spelling."}""")
        server.start()

        val vm = viewModel()
        vm.submitName("zorro q")
        val ask = vm.settled() as UiState.AskName
        assertEquals("No match — check the spelling.", ask.error)
    }

    @Test
    fun `blank name never touches the network`() = runTest(timeout = 60.seconds) {
        val vm = viewModel()
        vm.submitName("   ")
        val ask = vm.state.value as UiState.AskName
        assertTrue(ask.error!!.contains("first name"))
        assertTrue(server.requestedPaths.isEmpty())
    }

    @Test
    fun `old html setup site yields a friendly message, not a crash`() = runTest(timeout = 60.seconds) {
        server.route("/setup/", "<!doctype html><html><body>old page</body></html>")
        server.start()

        val vm = viewModel()
        vm.submitName("adam s")
        val ask = vm.settled() as UiState.AskName
        assertTrue(ask.error!!.contains("setup service"))
    }

    @Test
    fun `unreachable setup site says to check the connection`() = runTest(timeout = 60.seconds) {
        server.start()
        val vm = viewModel() // captures the url before shutdown
        server.shutdown()

        vm.submitName("adam s")
        val ask = vm.settled() as UiState.AskName
        assertTrue(ask.error!!.contains("connection"))
    }

    @Test
    fun `startOver returns to a clean name prompt`() = runTest(timeout = 60.seconds) {
        server.route("/setup/", """{"ok":false,"choices":["A B","A C"]}""")
        server.start()

        val vm = viewModel()
        vm.submitName("a")
        vm.settled()
        vm.startOver()
        assertEquals(UiState.AskName(), vm.state.value)
    }
}
