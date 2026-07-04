package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Meta resolution chain (§4.1.6): declared addons in user order, then
 * Cinemeta fallback for IMDb ids. The mock server plays both the installed
 * addon and "Cinemeta" (base URL injected).
 */
class MetaRepositoryTest {

    private lateinit var server: MockAddonServer
    private lateinit var addonRepository: AddonRepository
    private lateinit var client: OkHttpAddonClient

    @Before
    fun setUp() {
        server = MockAddonServer()
        client = OkHttpAddonClient(
            OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        )
        addonRepository = AddonRepository(client, FakeInstalledAddonDao())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repo(cinemetaBase: String = "http://localhost:1") =
        MetaRepository(client, addonRepository, cinemetaBase)

    @Test
    fun `uses a declaring addon before any fallback`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        // manifest_full: meta declared for series/channel with prefix "fix"
        server.route("/meta/series/fix:series:1.json", Fixtures.load("meta_series"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val meta = repo().resolveMeta("series", "fix:series:1").getOrThrow()

        assertEquals("Friends", meta.name)
        assertTrue(server.requestedPaths.contains("/meta/series/fix:series:1.json"))
    }

    @Test
    fun `skips addons that do not declare meta for the id`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        // Cinemeta fallback served by the same mock server under /cine
        server.route("/cine/meta/series/tt0108778.json", Fixtures.load("meta_series"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        // "tt" id + type series: fixture addon declares meta only for prefix
        // "fix", so it must NOT be queried (§4.1.3) — straight to Cinemeta.
        val meta = repo(cinemetaBase = server.url("/cine"))
            .resolveMeta("series", "tt0108778").getOrThrow()

        assertEquals("Friends", meta.name)
        assertTrue(server.requestedPaths.none { it == "/meta/series/tt0108778.json" })
        assertTrue(server.requestedPaths.contains("/cine/meta/series/tt0108778.json"))
    }

    @Test
    fun `non-imdb id with no declaring addon fails with a message`() = runTest(timeout = 60.seconds) {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        addonRepository.install(server.url("/manifest.json")).getOrThrow()

        val result = repo().resolveMeta("movie", "custom:123")

        assertTrue(result.isFailure)
        // No network calls should have been made beyond the install
        assertTrue(server.requestedPaths.none { it.startsWith("/meta/") })
    }
}
