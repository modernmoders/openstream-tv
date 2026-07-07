package dev.openstream.tv.diagnostics

import dev.openstream.tv.addon.fixtures.FakeProfileSyncPrefs
import dev.openstream.tv.data.DiagnosticsUploadPrefs
import dev.openstream.tv.data.ProfileLink
import dev.openstream.tv.data.SetupConfig
import java.net.URLDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsUploadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val client = OkHttpClient()

    private class FakeUploadPrefs(var last: Long = 0) : DiagnosticsUploadPrefs {
        override suspend fun lastUploadMs(): Long = last
        override suspend fun saveLastUploadMs(now: Long) { last = now }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun newLog() =
        DiagnosticsLog(tmp.newFile(), CoroutineScope(Dispatchers.IO), clock = { 0L })

    private fun upload(
        log: DiagnosticsLog,
        prefs: FakeUploadPrefs = FakeUploadPrefs(),
        link: ProfileLink? = ProfileLink("https://example.com/setup/adam-savoy-cYoj.json"),
        setupUrl: String = server.url("/").toString(),
    ) = DiagnosticsUpload(
        log = log,
        config = SetupConfig(setupUrl, "Test"),
        profileSyncPrefs = FakeProfileSyncPrefs(link),
        prefs = prefs,
        httpClient = client,
    )

    // ---- profileSlug: the box's safe identity ------------------------------

    @Test
    fun `slug is the profile filename stem`() {
        assertEquals(
            "adam-savoy-cYoj-ZKYTwQ",
            DiagnosticsUpload.profileSlug("https://x.example/setup/adam-savoy-cYoj-ZKYTwQ.json"),
        )
    }

    @Test
    fun `slug ignores a query string`() {
        assertEquals("a-b", DiagnosticsUpload.profileSlug("https://x.example/setup/a-b.json?v=2"))
    }

    @Test
    fun `slug rejects non-json blank and weird names`() {
        assertNull(DiagnosticsUpload.profileSlug("https://x.example/setup/"))
        assertNull(DiagnosticsUpload.profileSlug("https://x.example/setup/index.php"))
        assertNull(DiagnosticsUpload.profileSlug("https://x.example/setup/.json"))
        assertNull(DiagnosticsUpload.profileSlug("https://x.example/setup/bad%20name.json"))
    }

    // ---- uploadIfDue -------------------------------------------------------

    @Test
    fun `due upload posts who and log then advances the throttle`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.start()
        val log = newLog()
        log.append("player", "second entry")
        val prefs = FakeUploadPrefs(last = 0)

        upload(log, prefs).uploadIfDue(nowMs = DiagnosticsUpload.MIN_INTERVAL_MS + 1)

        val recorded = server.takeRequest()
        val body = URLDecoder.decode(recorded.body.readUtf8(), "UTF-8")
        assertTrue(body.contains("api=log"))
        assertTrue(body.contains("who=adam-savoy-cYoj"))
        assertTrue(body.contains("second entry"))
        assertEquals(DiagnosticsUpload.MIN_INTERVAL_MS + 1, prefs.last)
    }

    @Test
    fun `throttled within a day - no request`() = runTest {
        server.start()
        val log = newLog()
        log.append("a", "line")
        val prefs = FakeUploadPrefs(last = 1_000)

        upload(log, prefs).uploadIfDue(nowMs = 1_000 + DiagnosticsUpload.MIN_INTERVAL_MS - 1)

        assertEquals(0, server.requestCount)
        assertEquals(1_000, prefs.last)
    }

    @Test
    fun `never-connected box - no request`() = runTest {
        server.start()
        val log = newLog()
        log.append("a", "line")

        upload(log, link = null).uploadIfDue(nowMs = DiagnosticsUpload.MIN_INTERVAL_MS + 1)

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `empty log - no request`() = runTest {
        server.start()

        upload(newLog()).uploadIfDue(nowMs = DiagnosticsUpload.MIN_INTERVAL_MS + 1)

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `unconfigured build - no request`() = runTest {
        server.start()
        val log = newLog()
        log.append("a", "line")

        upload(log, setupUrl = "").uploadIfDue(nowMs = DiagnosticsUpload.MIN_INTERVAL_MS + 1)

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server error keeps the throttle so next launch retries`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        val log = newLog()
        log.append("a", "line")
        val prefs = FakeUploadPrefs(last = 0)

        upload(log, prefs).uploadIfDue(nowMs = DiagnosticsUpload.MIN_INTERVAL_MS + 1)

        assertEquals(1, server.requestCount)
        assertEquals(0, prefs.last) // unchanged: retry on next launch
    }
}
