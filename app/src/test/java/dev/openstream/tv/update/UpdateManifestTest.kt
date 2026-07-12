package dev.openstream.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val good = """
        {"versionCode":51,"versionName":"0.3.0-alpha.51",
         "apkUrl":"https://savoy.click/setup/app/sstreams-51.apk"}
    """.trimIndent()

    @Test
    fun `parses a well-formed manifest`() {
        val m = parseUpdateManifest(good)
        assertEquals(51L, m?.versionCode)
        assertEquals("0.3.0-alpha.51", m?.versionName)
        assertEquals("https://savoy.click/setup/app/sstreams-51.apk", m?.apkUrl)
    }

    @Test
    fun `malformed or incomplete manifests parse to null, never throw`() {
        assertNull(parseUpdateManifest("not json"))
        assertNull(parseUpdateManifest(""))
        assertNull(parseUpdateManifest("""{"versionCode":51}""")) // no apkUrl
        assertNull(parseUpdateManifest("""{"apkUrl":"https://x/y.apk"}""")) // no code
    }

    @Test
    fun `only an https apk url is trusted`() {
        // The manifest is fetched over the network; a downgrade to http (or a
        // junk scheme) must never reach the installer.
        val http = """{"versionCode":51,"versionName":"x","apkUrl":"http://savoy.click/a.apk"}"""
        assertNull(parseUpdateManifest(http))
    }

    @Test
    fun `an update is offered only for a STRICTLY newer versionCode`() {
        val m = parseUpdateManifest(good)!!
        assertTrue(m.isNewerThan(50))
        assertFalse(m.isNewerThan(51)) // same build: nothing to do
        assertFalse(m.isNewerThan(52)) // server behind: NEVER offer a downgrade
    }
}
