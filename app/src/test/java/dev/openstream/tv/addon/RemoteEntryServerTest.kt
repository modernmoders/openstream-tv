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

    @Test
    fun `binds the next port in range when the first is taken`() {
        ServerSocket(RemoteEntryServer.PORTS.first).use {
            val port = server.start(scope) { RemoteEntryServer.Outcome.Accepted }!!
            assertEquals(RemoteEntryServer.PORTS.first + 1, port)
        }
    }

    @Test
    fun `returns null when every port in range is taken`() {
        val squatters = RemoteEntryServer.PORTS.map { ServerSocket(it) }
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
