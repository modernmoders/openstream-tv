package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Repository logic tests with an in-memory fake DAO (the Room DAO itself is
 * exercised on-device in later phases). Network side uses MockAddonServer.
 */
class AddonRepositoryTest {

    private lateinit var server: MockAddonServer
    private lateinit var dao: FakeInstalledAddonDao
    private lateinit var repository: AddonRepository

    @Before
    fun setUp() {
        server = MockAddonServer()
        dao = FakeInstalledAddonDao()
        repository = AddonRepository(
            OkHttpAddonClient(
                OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
            ),
            dao,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `install fetches manifest and persists enabled at end of list`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start()

        val installed = repository.install(server.url("/manifest.json")).getOrThrow()

        assertEquals("org.openstream.fixture", installed.manifest.id)
        assertTrue(installed.enabled)
        assertEquals(0, installed.sortOrder)
        assertEquals(1, repository.observeInstalled().first().size)
    }

    @Test
    fun `two instances with the same manifest id coexist`() = runTest(timeout = 60.seconds) {
        // MASTER_PLAN §4.2: multiple AIOStreams configs share a manifest id
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.route("/b/manifest.json", Fixtures.load("manifest_full"))
        server.start()

        repository.install(server.url("/a/manifest.json")).getOrThrow()
        repository.install(server.url("/b/manifest.json")).getOrThrow()

        val installed = repository.observeInstalled().first()
        assertEquals(2, installed.size)
        assertEquals(installed[0].manifest.id, installed[1].manifest.id)
        assertEquals(listOf(0, 1), installed.map { it.sortOrder })
    }

    @Test
    fun `reinstall refreshes manifest but keeps order and enabled flag`() = runTest(timeout = 60.seconds) {
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.route("/b/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        val urlA = server.url("/a/manifest.json")
        repository.install(urlA).getOrThrow()
        repository.install(server.url("/b/manifest.json")).getOrThrow()
        repository.setEnabled(urlA, false)

        val again = repository.install(urlA).getOrThrow()

        assertEquals(0, again.sortOrder)     // kept position, not moved to end
        assertFalse(again.enabled)           // kept user's disabled state
        assertEquals(2, repository.observeInstalled().first().size)
    }

    @Test
    fun `install failure stores nothing`() = runTest(timeout = 60.seconds) {
        server.start() // 404 for everything

        val result = repository.install(server.url("/manifest.json"))

        assertTrue(result.isFailure)
        assertTrue(repository.observeInstalled().first().isEmpty())
    }

    @Test
    fun `invalid url fails fast without network`() = runTest(timeout = 60.seconds) {
        val result = repository.install("definitely not a url")
        val error = result.exceptionOrNull() as AddonRequestException
        assertEquals(AddonRequestException.Reason.INVALID_URL, error.reason)
    }

    @Test
    fun `uninstall and reorder update the observed list`() = runTest(timeout = 60.seconds) {
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.route("/b/manifest.json", Fixtures.load("manifest_full"))
        server.route("/c/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        val a = server.url("/a/manifest.json")
        val b = server.url("/b/manifest.json")
        val c = server.url("/c/manifest.json")
        listOf(a, b, c).forEach { repository.install(it).getOrThrow() }

        repository.uninstall(b)
        repository.reorder(listOf(c, a))

        val installed = repository.observeInstalled().first()
        assertEquals(listOf(c, a), installed.map { it.manifestUrl })
    }
}
