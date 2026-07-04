package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.Fixtures
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestParsingTest {

    private val manifest: Manifest =
        AddonJson.decodeFromString(Manifest.serializer(), Fixtures.load("manifest_full"))

    @Test
    fun `parses basic fields and ignores unknown keys`() {
        assertEquals("org.openstream.fixture", manifest.id)
        assertEquals("1.2.3", manifest.version)
        assertEquals("Fixture Addon", manifest.name)
        assertTrue(manifest.isUsable)
        assertTrue(manifest.behaviorHints.p2p)
        assertFalse(manifest.behaviorHints.adult)
    }

    @Test
    fun `parses mixed string and object resources`() {
        // "catalog" as plain string: inherits manifest-level types/idPrefixes
        val catalog = manifest.resource("catalog")!!
        assertNull(catalog.types)
        assertNull(catalog.idPrefixes)

        // "meta" as object with own types + idPrefixes
        val meta = manifest.resource("meta")!!
        assertEquals(listOf("series", "channel"), meta.types)
        assertEquals(listOf("fix"), meta.idPrefixes)

        // "stream" as object with types but no idPrefixes
        val stream = manifest.resource("stream")!!
        assertEquals(listOf("movie", "series"), stream.types)
        assertNull(stream.idPrefixes)
    }

    @Test
    fun `channel and tv types are preserved`() {
        // MASTER_PLAN §8.1: parsers must not drop channel/tv
        assertTrue("channel" in manifest.types)
        assertTrue("tv" in manifest.types)
        assertTrue(manifest.catalogs.any { it.type == "tv" })
    }

    @Test
    fun `declares honors per-resource types and idPrefixes`() {
        // meta resource declares only series/channel with prefix fix
        assertTrue(manifest.declares("meta", "series", "fix:series:1"))
        assertFalse(manifest.declares("meta", "movie", "fix:series:1"))   // type not declared
        assertFalse(manifest.declares("meta", "series", "tt0108778"))     // prefix mismatch
        // stream resource has no own idPrefixes -> manifest-level tt/fix apply
        assertTrue(manifest.declares("stream", "movie", "tt1254207"))
        assertFalse(manifest.declares("stream", "movie", "yt_id:abc"))
        // undeclared resource
        assertFalse(manifest.declares("addon_catalog", "movie"))
    }

    @Test
    fun `catalog extra modern and legacy notations both work`() {
        val top = manifest.catalogs.first { it.id == "top" }
        assertTrue(top.supportsExtra("search"))
        assertTrue(top.supportsExtra("genre"))
        assertFalse(top.isSearchOnly)
        assertEquals(listOf("Action", "Comedy"), top.extra.first { it.name == "genre" }.options)

        val searchOnly = manifest.catalogs.first { it.id == "searchonly" }
        assertTrue(searchOnly.isSearchOnly)

        val legacy = manifest.catalogs.first { it.id == "legacy" }
        assertTrue(legacy.supportsExtra("genre"))
        assertTrue(legacy.requiresExtra("search"))
        assertTrue(legacy.isSearchOnly)
    }

    @Test
    fun `empty manifest parses but is not usable`() {
        val empty = AddonJson.decodeFromString(Manifest.serializer(), "{}")
        assertFalse(empty.isUsable)
    }

    @Test(expected = SerializationException::class)
    fun `truly malformed json throws for the client to wrap`() {
        AddonJson.decodeFromString(Manifest.serializer(), """{ "id": [ }""")
    }
}
