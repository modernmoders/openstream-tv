package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.MockAddonServer
import java.io.IOException
import java.nio.file.Files
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The cold-start cache policy (owner feedback 2026-07-05): catalog/meta
 * served from disk on relaunch, stale beats offline errors, streams never
 * cached. Raw OkHttp calls — the policy is pure HTTP, below AddonClient.
 */
class AddonHttpCacheTest {

    private lateinit var server: MockAddonServer

    @Before
    fun setUp() {
        server = MockAddonServer()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client(ttlSeconds: Long): OkHttpClient {
        val policy = AddonHttpCache(ttlSeconds)
        val cacheDir = Files.createTempDirectory("addon-http-cache").toFile()
        return OkHttpClient.Builder()
            .cache(AddonHttpCache.diskCache(cacheDir))
            .addInterceptor(policy.staleIfOffline)
            .addNetworkInterceptor(policy.responsePolicy)
            .build()
    }

    private fun get(client: OkHttpClient, url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            resp.body.string()
        }

    @Test
    fun `fresh catalog is served from disk with zero network`() {
        server.route("/catalog/movie/top.json", """{"metas":[]}""")
        server.start()
        val client = client(ttlSeconds = 1800)
        val url = server.url("/catalog/movie/top.json")

        assertEquals("""{"metas":[]}""", get(client, url))
        server.shutdown() // addon gone — only the disk cache can answer now
        assertEquals("""{"metas":[]}""", get(client, url))
        assertEquals(1, server.requestedPaths.size)
    }

    @Test
    fun `CDN Age header must not make fresh responses stale on arrival`() {
        // Cloudflare fronts Cinemeta and sends Age in the thousands; if it
        // survives, max-age=1800 is exceeded immediately and every launch
        // refetches (found live on the emulator, 2026-07-05).
        server.route(
            "/catalog/movie/top.json", """{"metas":[]}""",
            headers = mapOf("Age" to "999999", "Expires" to "Thu, 01 Jan 1970 00:00:00 GMT"),
        )
        server.start()
        val client = client(ttlSeconds = 1800)
        val url = server.url("/catalog/movie/top.json")

        assertEquals("""{"metas":[]}""", get(client, url))
        assertEquals("""{"metas":[]}""", get(client, url)) // must be a cache hit
        assertEquals(1, server.requestedPaths.size)
    }

    @Test
    fun `stale catalog is served when the addon is unreachable`() {
        server.route("/catalog/movie/top.json", """{"metas":[]}""")
        server.start()
        // ttl=0: cached copy is stale immediately, so the second fetch tries
        // the network first, fails, and falls back to the stale copy.
        val client = client(ttlSeconds = 0)
        val url = server.url("/catalog/movie/top.json")

        assertEquals("""{"metas":[]}""", get(client, url))
        server.shutdown()
        assertEquals("""{"metas":[]}""", get(client, url))
        assertEquals(1, server.requestedPaths.size)
    }

    @Test
    fun `streams are never served from cache`() {
        server.route("/stream/movie/tt1.json", """{"streams":[]}""")
        server.start()
        val client = client(ttlSeconds = 1800)
        val url = server.url("/stream/movie/tt1.json")

        assertEquals("""{"streams":[]}""", get(client, url))
        server.shutdown()
        val failed = try {
            get(client, url)
            false
        } catch (e: IOException) {
            true
        }
        assertTrue("stale stream response must not be replayed", failed)
    }
}
