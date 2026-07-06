package dev.openstream.tv.diagnostics

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Where failure details go instead of the screen (MASTER_PLAN §10, owner:
 * "Don't show them the errors and stuff, but log them"). A fun interface so
 * data-layer classes can take it as a defaulted constructor param — tests
 * that build them directly stay one-liners, Hilt binds [DiagnosticsLog].
 */
fun interface DiagnosticsSink {
    /** Fire-and-forget: must never block or fail the caller (elder rule). */
    fun record(tag: String, message: String)

    companion object {
        val NONE = DiagnosticsSink { _, _ -> }
    }
}

/**
 * On-device diagnostics log: viewers see quiet friendly fallbacks; the
 * detail lands here, readable in Settings → Expert mode → App log.
 *
 * One plain-text file, newest line last, trimmed so it can never grow
 * unbounded on the 32-bit boxes. Addon URLs are user secrets (they embed
 * personal config tokens) — every line is sanitized before it is written,
 * so nothing that reads this log can leak one.
 */
class DiagnosticsLog(
    private val logFile: File,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : DiagnosticsSink {
    private val mutex = Mutex()

    override fun record(tag: String, message: String) {
        scope.launch { runCatching { append(tag, message) } }
    }

    suspend fun append(tag: String, message: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(clock()))
            val line = "$stamp  [$tag]  ${sanitize(message)}"
            val lines = readLines() + line
            val kept = if (lines.size > MAX_LINES) lines.takeLast(TRIM_TO) else lines
            logFile.writeText(kept.joinToString("\n"))
        }
    }

    /** Newest first — the viewer shows the most recent problem on top. */
    suspend fun read(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock { readLines().asReversed() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { logFile.delete(); Unit }
    }

    private fun readLines(): List<String> =
        if (logFile.exists()) logFile.readLines().filter { it.isNotBlank() } else emptyList()

    companion object {
        // ~400 short lines ≈ tens of KB: weeks of failures, trivial to load.
        private const val MAX_LINES = 400
        private const val TRIM_TO = 300
        private const val MAX_MESSAGE = 300

        private val URL = Regex("""(?i)\b[a-z][a-z0-9+.-]*://\S+""")

        /** Strip anything URL-shaped (tokens live in addon URLs) and cap length. */
        fun sanitize(message: String): String =
            URL.replace(message, "‹address hidden›").take(MAX_MESSAGE)
    }
}

/**
 * The log line for a failure: the same friendly reason a chip would show,
 * plus the exception type and (sanitized) detail the chip deliberately hides.
 */
fun Throwable.toDiagnosticDetail(): String {
    val detail = message?.takeIf { it.isNotBlank() }?.let { ": ${it.trim()}" }.orEmpty()
    return "${this::class.simpleName ?: "Error"}$detail"
}
