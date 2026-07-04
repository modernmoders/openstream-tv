package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end tests of OkHttpAddonClient against the mock addon server
 * (MASTER_PLAN §9.1): happy paths, malformed JSON, HTTP errors, slow
 * responses, and URL construction.
 */
class AddonClientTest {

    private lateinit var server: MockAddonServer
    private lateinit var client: OkHttpAddonClient

    @Before
    fun setUp() {
        server = MockAddonServer()
        client = OkHttpAddonClient(
            OkHttpClient.Builder()
                .callTimeout(3, TimeUnit.SECONDS) // short: keeps failure tests fast
                .build()
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `fetches and validates a manifest`() = runTest {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start()

        val manifest = client.fetchManifest(server.url("/manifest.json")).getOrThrow()
        assertEquals("org.openstream.fixture", manifest.id)
    }

    @Test
    fun `empty manifest fails with INVALID_MANIFEST`() = runTest {
        server.route("/manifest.json", "{}")
        server.start()

        val error = client.fetchManifest(server.url("/manifest.json"))
            .exceptionOrNull() as AddonRequestException
        assertEquals(AddonRequestException.Reason.INVALID_MANIFEST, error.reason)
    }

    @Test
    fun `catalog with extra builds protocol-shaped path`() = runTest {
        server.route(
            "/catalog/movie/top/search=game%20of%20thrones&skip=100.json",
            Fixtures.load("catalog_mixed"),
        )
        server.start()

        val metas = client.catalog(
            server.url(""), "movie", "top",
            // LinkedHashMap: insertion order must be preserved in the path
            linkedMapOf("search" to "game of thrones", "skip" to "100"),
        ).getOrThrow()

        assertEquals(5, metas.size)
        assertEquals(
            "/catalog/movie/top/search=game%20of%20thrones&skip=100.json",
            server.requestedPaths.single(),
        )
    }

    @Test
    fun `episode ids with colons survive path building`() = runTest {
        server.route("/stream/series/tt0108778:1:1.json", Fixtures.load("streams_full"))
        server.start()

        val streams = client.streams(server.url(""), "series", "tt0108778:1:1").getOrThrow()
        assertEquals(5, streams.size)
        assertEquals("/stream/series/tt0108778:1:1.json", server.requestedPaths.single())
    }

    @Test
    fun `malformed body fails with BAD_JSON not a crash`() = runTest {
        server.route("/stream/movie/tt1.json", Fixtures.load("malformed"))
        server.start()

        val error = client.streams(server.url(""), "movie", "tt1")
            .exceptionOrNull() as AddonRequestException
        assertEquals(AddonRequestException.Reason.BAD_JSON, error.reason)
    }

    @Test
    fun `http error fails with HTTP_STATUS`() = runTest {
        server.start() // no routes -> 404 for everything

        val error = client.streams(server.url(""), "movie", "tt1")
            .exceptionOrNull() as AddonRequestException
        assertEquals(AddonRequestException.Reason.HTTP_STATUS, error.reason)
        assertTrue(error.message!!.contains("404"))
    }

    @Test
    fun `unreachable server fails with NETWORK`() = runTest {
        server.start()
        val deadUrl = server.url("")
        server.shutdown()

        val error = client.streams(deadUrl, "movie", "tt1")
            .exceptionOrNull() as AddonRequestException
        assertEquals(AddonRequestException.Reason.NETWORK, error.reason)
    }

    @Test
    fun `slow addon still succeeds within timeout`() = runTest {
        // 1s delayed body vs 3s call timeout: slow-but-alive addons must work
        server.route("/stream/movie/tt1.json", Fixtures.load("streams_full"), delayMs = 1000)
        server.start()

        val streams = client.streams(server.url(""), "movie", "tt1").getOrThrow()
        assertEquals(5, streams.size)
    }

    @Test
    fun `subtitles endpoint parses`() = runTest {
        server.route("/subtitles/movie/tt1.json", Fixtures.load("subtitles"))
        server.start()

        val subs = client.subtitles(server.url(""), "movie", "tt1").getOrThrow()
        assertEquals(listOf("eng", "ger"), subs.map { it.lang })
    }

    @Test
    fun `meta endpoint parses`() = runTest {
        server.route("/meta/series/tt0108778.json", Fixtures.load("meta_series"))
        server.start()

        val meta = client.meta(server.url(""), "series", "tt0108778").getOrThrow()
        assertEquals("Friends", meta.name)
    }
}
