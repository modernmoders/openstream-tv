package dev.openstream.tv.addon

import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Type the URL on your computer" helper: a deliberately tiny HTTP server
 * that serves one form and accepts one POST, alive ONLY while the Add-addon
 * screen is open. The browser is just a long-range keyboard — the submitted
 * URL goes to the TV screen's normal fetch → preview → confirm flow
 * (§4.1.1 stays on-screen; the server never installs anything itself).
 *
 * Hand-rolled on ServerSocket instead of a server dependency: two routes,
 * form-encoded bodies only, Connection: close — ~100 lines beats a new
 * library (KISS). Pure JVM so the whole thing is unit-testable.
 *
 * SECURITY: manifest URLs are secrets. The server never echoes the
 * submitted URL back into HTML, never logs it, and exposes no read
 * endpoint (nothing about installed addons is reachable from the LAN).
 */
class RemoteEntryServer @Inject constructor() {

    /** What the browser page should say about a submission. */
    sealed interface Outcome {
        data object Accepted : Outcome
        data class Rejected(val message: String) : Outcome
    }

    private var socket: ServerSocket? = null

    /**
     * Bind the first free port in [PORTS] and serve until [stop]. Returns
     * the bound port, or null if all ports are taken. [onSubmit] is called
     * on an IO thread with the raw pasted URL.
     */
    fun start(scope: CoroutineScope, onSubmit: (String) -> Outcome): Int? {
        val server = PORTS.firstNotNullOfOrNull { port ->
            runCatching { ServerSocket(port) }.getOrNull()
        } ?: return null
        socket = server
        scope.launch(Dispatchers.IO) {
            // stop() closing the socket makes accept() throw → loop exits.
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching { client.use { handle(it, onSubmit) } }
            }
        }
        return server.localPort
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket, onSubmit: (String) -> Outcome) {
        client.soTimeout = 5_000
        // ISO-8859-1: HTTP headers are ASCII and it maps bytes 1:1, so
        // Content-Length (bytes) can safely drive a char read below.
        val reader = InputStreamReader(client.getInputStream(), Charsets.ISO_8859_1)
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val c = reader.read()
            if (c == -1) return
            head.append(c.toChar())
        }
        val requestLine = head.lineSequence().first()
        val contentLength = head.lineSequence()
            .firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val buf = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(buf, read, contentLength - read)
                if (n == -1) break
                read += n
            }
            String(buf, 0, read)
        } else ""

        val response = when {
            requestLine.startsWith("GET / ") -> page(null)
            requestLine.startsWith("POST /add") -> {
                val url = parseFormBody(body)["url"]?.trim().orEmpty()
                when (val outcome = if (url.isBlank()) Outcome.Rejected(EMPTY_MSG) else onSubmit(url)) {
                    is Outcome.Accepted -> page(
                        "✓ Sent to the TV — the addon preview is on screen. " +
                            "Press Install there, or paste another link here."
                    )
                    is Outcome.Rejected -> page("✗ " + outcome.message)
                }
            }
            else -> null
        }
        val out = client.getOutputStream()
        out.write(
            response?.let { httpResponse("200 OK", it) }
                ?: httpResponse("404 Not Found", "<h1>404</h1>")
        )
        out.flush()
    }

    private fun httpResponse(status: String, body: String): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return ("HTTP/1.1 $status\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1) + bytes
    }

    /** The one page. [message] is always OUR text, never user input (no echo). */
    private fun page(message: String?): String = """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>OpenStream TV — Add addon</title>
        <style>
          body{background:#0e0e16;color:#eee;font:16px/1.5 system-ui,sans-serif;
               display:flex;justify-content:center;padding:48px 16px}
          main{max-width:560px;width:100%}
          h1{font-size:22px} p{color:#9a9ab0}
          form{display:flex;gap:8px;margin:20px 0}
          input{flex:1;padding:12px;border-radius:8px;border:1px solid #333;
                background:#181822;color:#eee;font-size:15px}
          button{padding:12px 20px;border-radius:8px;border:0;background:#e5e1e6;
                 color:#111;font-weight:600;cursor:pointer}
          .msg{padding:12px;border-radius:8px;background:#181822}
        </style></head><body><main>
        <h1>OpenStream TV — Add addon</h1>
        <p>Paste the addon's manifest URL. The preview appears on your TV;
           confirm the install there.</p>
        <form method="post" action="/add">
          <input name="url" autofocus autocomplete="off"
                 placeholder="https://…/manifest.json">
          <button>Send to TV</button>
        </form>
        ${message?.let { "<div class=\"msg\">$it</div>" } ?: ""}
        </main></body></html>
    """.trimIndent()

    companion object {
        /** Fixed, guessable ports — the TV shows the one that bound. */
        val PORTS = 8385..8389

        private const val EMPTY_MSG = "Paste a URL first."

        /** application/x-www-form-urlencoded, the only body browsers send here. */
        fun parseFormBody(body: String): Map<String, String> =
            body.split('&').filter { it.isNotBlank() }.associate { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }

        /** The box's LAN IPv4 (wifi or ethernet), or null off-network. */
        fun lanAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
