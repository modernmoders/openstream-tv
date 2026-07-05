package dev.openstream.tv.addon.fixtures

import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * The project's mock addon (MASTER_PLAN §9.1): a local HTTP server serving
 * canned protocol responses. Register routes with [route]; unregistered paths
 * get 404. Supports per-route delays (slow-addon scenarios) and arbitrary
 * bodies (malformed JSON scenarios).
 *
 * Usage:
 * ```
 * val server = MockAddonServer()
 * server.route("/manifest.json", Fixtures.load("manifest_full"))
 * server.start()
 * ... server.url("/manifest.json") ...
 * server.shutdown()
 * ```
 */
class MockAddonServer {
    private val server = MockWebServer()
    private val routes = mutableMapOf<String, MockResponse>()

    /** The last paths requested, for asserting URL construction. */
    val requestedPaths = mutableListOf<String>()

    fun route(
        path: String,
        body: String,
        status: Int = 200,
        delayMs: Long = 0,
        headers: Map<String, String> = emptyMap(),
    ) {
        val response = MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        if (delayMs > 0) response.setBodyDelay(delayMs, TimeUnit.MILLISECONDS)
        routes[path] = response
    }

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: "/"
                requestedPaths += path
                return routes[path] ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    fun url(path: String): String = server.url(path).toString()

    fun shutdown() = server.shutdown()
}

/** Loads canned JSON from app/src/test/resources/fixtures/. */
object Fixtures {
    fun load(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name.json")) {
            "missing fixture: fixtures/$name.json"
        }.bufferedReader().use { it.readText() }
}
