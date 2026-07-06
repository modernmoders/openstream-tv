package dev.openstream.tv.diagnostics

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun log(file: File) =
        DiagnosticsLog(file, CoroutineScope(Dispatchers.IO), clock = { 0L })

    @Test
    fun `append then read returns newest first`() = runTest {
        val log = log(tmp.newFile())
        log.append("a", "first")
        log.append("b", "second")

        val lines = log.read()
        assertEquals(2, lines.size)
        assertTrue("newest on top: ${lines[0]}", lines[0].contains("second"))
        assertTrue(lines[1].contains("first"))
        assertTrue(lines[0].contains("[b]"))
    }

    @Test
    fun `urls are stripped before hitting disk`() = runTest {
        val file = tmp.newFile()
        val log = log(file)
        log.append("catalog", "failed: https://example.com/SECRET-token/manifest.json timed out")

        // Both the read API and the raw file must be clean — the file is the
        // thing a future export/copy could leak.
        assertTrue(log.read().single().contains("‹address hidden›"))
        val raw = file.readText()
        assertTrue("raw file leaked a URL: $raw", "SECRET" !in raw && "example.com" !in raw)
    }

    @Test
    fun `stremio scheme urls are stripped too`() = runTest {
        val log = log(tmp.newFile())
        log.append("addon", "bad link stremio://example.com/TOKEN/manifest.json given")
        assertTrue("TOKEN" !in log.read().single())
    }

    @Test
    fun `file is trimmed so it cannot grow unbounded`() = runTest {
        val log = log(tmp.newFile())
        repeat(450) { log.append("t", "line $it") }

        val lines = log.read()
        assertTrue("kept ${lines.size} lines", lines.size <= 400)
        // Newest survive the trim, oldest fall off.
        assertTrue(lines.first().endsWith("line 449"))
        assertTrue(lines.none { it.endsWith("line 0") })
    }

    @Test
    fun `clear empties the log`() = runTest {
        val log = log(tmp.newFile())
        log.append("a", "something")
        log.clear()
        assertEquals(emptyList<String>(), log.read())
    }

    @Test
    fun `record is fire and forget on the injected scope`() = runTest {
        val log = log(tmp.newFile())
        log.record("tag", "async line")
        // record launches on a real IO scope — poll briefly for the write.
        var lines = log.read()
        var tries = 0
        while (lines.isEmpty() && tries++ < 200) {
            Thread.sleep(5)
            lines = log.read()
        }
        assertEquals(1, lines.size)
    }

    @Test
    fun `diagnostic detail carries class and message`() {
        assertEquals("IllegalStateException: boom", IllegalStateException("boom").toDiagnosticDetail())
        assertEquals("IllegalStateException", IllegalStateException().toDiagnosticDetail())
    }
}
