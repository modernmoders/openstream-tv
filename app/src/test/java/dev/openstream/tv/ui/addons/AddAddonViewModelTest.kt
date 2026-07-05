package dev.openstream.tv.ui.addons

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.RemoteEntryServer
import dev.openstream.tv.addon.SetupProfileClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.ui.addons.AddAddonViewModel.UiState
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

/**
 * Install-flow state machine (§4.1.1): url → Fetching → Preview → Installing
 * → Installed, with friendly errors on every failure path.
 */
class AddAddonViewModelTest {

    private lateinit var server: MockAddonServer
    private lateinit var dao: FakeInstalledAddonDao
    private lateinit var viewModel: AddAddonViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockAddonServer()
        dao = FakeInstalledAddonDao()
        val http = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        val client = OkHttpAddonClient(http)
        viewModel = AddAddonViewModel(
            client, AddonRepository(client, dao), RemoteEntryServer(), SetupProfileClient(http)
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    /** Wait until the async fetch/install settles into a non-transient state. */
    private suspend fun settledState(): UiState =
        viewModel.state.first { it !is UiState.Fetching && it !is UiState.Installing }

    @Test
    fun `invalid url errors without touching the network`() = runTest(timeout = 60.seconds) {
        viewModel.fetchPreview("not a url")
        val state = viewModel.state.value as UiState.Error
        assertTrue(state.message.contains("manifest.json"))
    }

    @Test
    fun `fetch shows preview and confirm persists the addon`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start()

        viewModel.fetchPreview(server.url("/manifest.json"))
        val preview = settledState() as UiState.Preview
        assertEquals("Fixture Addon", preview.manifest.name)
        assertTrue(dao.getAll().isEmpty()) // §4.1.1: nothing persisted before confirm

        viewModel.confirmInstall()
        assertEquals(UiState.Installed, settledState())
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `dismissing the preview installs nothing`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start()

        viewModel.fetchPreview(server.url("/manifest.json"))
        settledState()
        viewModel.dismissPreview()

        assertEquals(UiState.Idle, viewModel.state.value)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `unreachable addon yields friendly network error`() = runTest(timeout = 60.seconds) {
        server.start()
        val deadUrl = server.url("/manifest.json")
        server.shutdown()

        viewModel.fetchPreview(deadUrl)
        val error = settledState() as UiState.Error
        assertTrue(error.message.contains("Couldn't reach"))
    }

    @Test
    fun `remote submission drives the normal preview flow`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start()

        val outcome = viewModel.onRemoteSubmit(server.url("/manifest.json"))
        assertEquals(RemoteEntryServer.Outcome.Accepted, outcome)
        val preview = settledState() as UiState.Preview
        assertEquals("Fixture Addon", preview.manifest.name)
        assertTrue(dao.getAll().isEmpty()) // still needs on-TV confirmation (§4.1.1)
    }

    @Test
    fun `remote submission with junk is rejected and leaves state alone`() = runTest(timeout = 60.seconds) {
        val outcome = viewModel.onRemoteSubmit("not a url")
        assertTrue(outcome is RemoteEntryServer.Outcome.Rejected)
        assertTrue((outcome as RemoteEntryServer.Outcome.Rejected).message.contains("manifest.json"))
        assertEquals(UiState.Idle, viewModel.state.value)
    }

    @Test
    fun `setup link previews all addons and installs them in profile order`() = runTest(timeout = 60.seconds) {
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.route("/b/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        val urlA = server.url("/a/manifest.json")
        val urlB = server.url("/b/manifest.json")
        server.route(
            "/profile.json",
            """{"openstream":1,"name":"Family setup","addons":[
                 {"name":"First","url":"$urlA"},
                 {"name":"Second","url":"$urlB"}]}"""
        )

        viewModel.fetchPreview(server.url("/profile.json"))
        val preview = settledState() as UiState.ProfilePreview
        assertEquals("Family setup", preview.profileName)
        assertEquals(listOf("First", "Second"), preview.entries.map { it.displayName })
        assertTrue(preview.entries.all { it.ok })
        assertTrue(dao.getAll().isEmpty()) // still confirm-on-TV first (§4.1.1)

        viewModel.confirmInstallProfile()
        assertEquals(UiState.Installed, settledState())
        val installed = dao.getAll().sortedBy { it.sortOrder }.map { it.manifestUrl }
        assertEquals(listOf(urlA, urlB), installed) // §4.1.7: profile order kept
    }

    @Test
    fun `setup link with a broken entry still installs the good ones`() = runTest(timeout = 60.seconds) {
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        val urlA = server.url("/a/manifest.json")
        server.route(
            "/profile.json",
            """{"openstream":1,"addons":[
                 {"name":"Good","url":"$urlA"},
                 {"name":"Broken","url":"not a url"}]}"""
        )

        viewModel.fetchPreview(server.url("/profile.json"))
        val preview = settledState() as UiState.ProfilePreview
        assertEquals(listOf(true, false), preview.entries.map { it.ok })

        viewModel.confirmInstallProfile()
        assertEquals(UiState.Installed, settledState())
        assertEquals(listOf(urlA), dao.getAll().map { it.manifestUrl })
    }

    @Test
    fun `link that is neither addon nor profile yields friendly error`() = runTest(timeout = 60.seconds) {
        server.route("/nonsense", """{"hello":"world"}""")
        server.start()

        viewModel.fetchPreview(server.url("/nonsense"))
        val error = settledState() as UiState.Error
        assertTrue(error.message.contains("isn't an addon or a setup link"))
    }

    @Test
    fun `non-manifest response yields friendly error`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", "{}") // parses, but unusable manifest
        server.start()

        viewModel.fetchPreview(server.url("/manifest.json"))
        val error = settledState() as UiState.Error
        assertTrue(error.message.contains("not with a valid addon manifest"))
    }
}
