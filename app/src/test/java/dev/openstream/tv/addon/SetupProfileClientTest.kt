package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.MockAddonServer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SetupProfileClientTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var server: MockAddonServer? = null

    @After
    fun tearDown() {
        server?.shutdown()
    }

    private fun profileJson(name: String) =
        """{"openstream":1,"name":"$name","addons":[{"name":"A","url":"https://example.com/manifest.json"}]}"""

    @Test
    fun `profile fetch sees an edited file even when the host stamps a long max-age`() = runTest {
        // Dreamhost serves static JSON with Cache-Control: max-age=172800 (2
        // days). Box .117 honored it and went dark to profile edits — every
        // sync "succeeded" from disk cache without a single network request
        // (2026-07-11). The profile is the remote-management channel, so the
        // fetch must always revalidate against the server.
        val srv = MockAddonServer().also { server = it }
        val longCache = mapOf("Cache-Control" to "max-age=172800", "ETag" to "\"v1\"")
        srv.route("/profile.json", profileJson("Old"), headers = longCache)
        srv.start()
        val client = OkHttpClient.Builder()
            .cache(Cache(File(tmp.root, "http"), 1_000_000))
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
        val profiles = SetupProfileClient(client)
        val url = srv.url("/profile.json")

        assertEquals("Old", profiles.fetch(url).getOrThrow().name)

        // The owner edits the hosted file; the URL never changes.
        srv.route("/profile.json", profileJson("New"), headers = mapOf("Cache-Control" to "max-age=172800", "ETag" to "\"v2\""))

        assertEquals("New", profiles.fetch(url).getOrThrow().name)
    }
}
