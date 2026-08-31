package dev.openstream.tv.addon

import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the hand-rolled HTTP loop with real sockets — HttpURLConnection
 * speaks genuine browser-shaped HTTP/1.1, which is exactly what we must parse.
 */
class RemoteEntryServerTest {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val server = RemoteEntryServer()

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun get(port: Int, path: String): Pair<Int, String> {
        val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3_000
        conn.readTimeout = 3_000
        val body = (if (conn.responseCode >= 400) conn.errorStream else conn.inputStream)
            .readBytes().toString(Charsets.UTF_8)
        return conn.responseCode to body
    }

    private fun postAdd(port: Int, url: String): String {
        val conn = URL("http://127.0.0.1:$port/add").openConnection() as HttpURLConnection
        conn.connectTimeout = 3_000
        conn.readTimeout = 3_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.write("url=${URLEncoder.encode(url, "UTF-8")}".toByteArray())
        return conn.inputStream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `serves the entry form`() {
        val port = server.start(scope) { RemoteEntryServer.Outcome.Accepted }!!
        val (code, body) = get(port, "/")
        assertEquals(200, code)
        assertTrue(body.contains("Add addon"))
        assertTrue(body.contains("form method=\"post\""))
    }

    @Test
    fun `accepted submission reaches the callback url-decoded`() {
        val received = AtomicReference<String>()
        val port = server.start(scope) { received.set(it); RemoteEntryServer.Outcome.Accepted }!!
        val submitted = "https://example.org/some%20path/manifest.json?a=b&c=d"
        val body = postAdd(port, submitted)
        assertEquals(submitted, received.get())
        assertTrue(body.contains("Sent to the TV"))
    }

    @Test
    fun `rejected submission shows our message and never echoes the url`() {
        val port = server.start(scope) { RemoteEntryServer.Outcome.Rejected("Not an addon URL.") }!!
        val secret = "https://private.example/abc123token/manifest.json"
        val body = postAdd(port, secret)
        assertTrue(body.contains("Not an addon URL."))
        // SECURITY: manifest URLs are secrets — the page must never reflect them.
        assertFalse(body.contains("abc123token"))
    }

    @Test
    fun `blank submission asks for a url without invoking the callback`() {
        val called = AtomicBoolean(false)
        val port = server.start(scope) { called.set(true); RemoteEntryServer.Outcome.Accepted }!!
        val body = postAdd(port, "   ")
        assertTrue(body.contains("Paste a URL first."))
        assertFalse(called.get())
    }

    @Test
    fun `unknown path is 404`() {
        val port = server.start(scope) { RemoteEntryServer.Outcome.Accepted }!!
        val (code, _) = get(port, "/favicon.ico")
        assertEquals(404, code)
    }

    /** Bind [port] for the test, or null if something on this machine already
     *  holds it — an externally-held port is exactly as "taken" as one we
     *  squat ourselves. Shared CI runners DO squat ports in our range
     *  (BindException in test setup failed the 2026-07-28 PR run). */
    private fun squat(port: Int): ServerSocket? =
        try { ServerSocket(port) } catch (e: java.net.BindException) { null }

    @Test
    fun `binds a later port in range when the first is taken`() {
        val first = squat(RemoteEntryServer.PORTS.first)
        try {
            val port = server.start(scope) { RemoteEntryServer.Outcome.Accepted }!!
            // Not "first + 1" exactly: on a busy machine other ports in the
            // range may be taken too — the contract is "skips taken ports,
            // stays in range", not which free port it lands on.
            assertTrue(port in RemoteEntryServer.PORTS)
            assertTrue(port != RemoteEntryServer.PORTS.first)
        } finally {
            first?.close()
        }
    }

    @Test
    fun `returns null when every port in range is taken`() {
        val squatters = RemoteEntryServer.PORTS.mapNotNull { squat(it) }
        try {
            assertNull(server.start(scope) { RemoteEntryServer.Outcome.Accepted })
        } finally {
            squatters.forEach { it.close() }
        }
    }

    @Test
    fun `stop closes the port`() {
        val port = server.start(scope) { RemoteEntryServer.Outcome.Accepted }!!
        server.stop()
        try {
            get(port, "/")
            throw AssertionError("expected connection to be refused after stop()")
        } catch (expected: ConnectException) {
            // refused = socket really closed
        }
    }

    @Test
    fun `parseFormBody decodes percent-encoding and plus-as-space`() {
        val parsed = RemoteEntryServer.parseFormBody(
            "url=https%3A%2F%2Fhost%2Fmanifest.json&note=two+words"
        )
        assertEquals("https://host/manifest.json", parsed["url"])
        assertEquals("two words", parsed["note"])
    }
}
