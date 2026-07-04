package dev.openstream.tv.addon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddonUrlsTest {

    @Test
    fun `accepts https manifest urls and trims whitespace`() {
        assertEquals(
            "https://addon.example.com/manifest.json",
            AddonUrls.normalizeManifestUrl("  https://addon.example.com/manifest.json \n"),
        )
    }

    @Test
    fun `rewrites stremio scheme to https`() {
        assertEquals(
            "https://addon.example.com/cfg123/manifest.json",
            AddonUrls.normalizeManifestUrl("stremio://addon.example.com/cfg123/manifest.json"),
        )
    }

    @Test
    fun `rejects non-manifest and non-http inputs`() {
        assertNull(AddonUrls.normalizeManifestUrl("https://addon.example.com/"))
        assertNull(AddonUrls.normalizeManifestUrl("ftp://addon.example.com/manifest.json"))
        assertNull(AddonUrls.normalizeManifestUrl("not a url"))
    }

    @Test
    fun `derives base url from manifest url`() {
        assertEquals(
            "https://addon.example.com/cfg123",
            AddonUrls.baseUrlOf("https://addon.example.com/cfg123/manifest.json"),
        )
    }
}
