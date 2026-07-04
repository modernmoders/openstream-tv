package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamMappingTest {

    private val streams = AddonJson
        .decodeFromString(StreamsResponse.serializer(), Fixtures.load("streams_full"))
        .streams

    @Test
    fun `url stream maps headers subtitles and bingeGroup`() {
        val source = streams[0].toPlayableSource("Friends S01E01")!!

        assertEquals("https://cdn.example.com/friends-s01e01-2160p.mkv", source.url)
        assertEquals("Friends S01E01", source.title)
        assertEquals("OpenStream", source.headers["User-Agent"])
        assertEquals("Bearer x", source.headers["Authorization"])
        assertEquals("fixture-2160p-webdl", source.bingeGroup)
        assertEquals(1, source.subtitles.size)
        assertEquals("eng", source.subtitles[0].lang)
        assertEquals(0, source.startPositionMs)
    }

    @Test
    fun `bare url stream maps with empty extras`() {
        val source = streams[4].toPlayableSource("Bare")!!
        assertEquals(emptyMap<String, String>(), source.headers)
        assertNull(source.bingeGroup)
    }

    @Test
    fun `non-url sources refuse to map`() {
        assertNull(streams[2].toPlayableSource("Torrent"))  // infoHash only
        assertNull(streams[3].toPlayableSource("External")) // externalUrl only
    }
}
