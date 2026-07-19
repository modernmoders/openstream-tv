package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.domain.SubtitleTrack
import dev.openstream.tv.domain.VideoCodec
import dev.openstream.tv.domain.mergeSubtitleTracks
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
    fun `codec is stamped from the release label`() {
        val hevc10 = Stream(
            url = "https://cdn.example.com/anime.mkv",
            name = "Fixture HEVC",
            description = "Show S01E01 1080p HEVC 10bit",
        )
        assertEquals(VideoCodec.HEVC_10BIT, hevc10.toPlayableSource("Show")!!.videoCodec)
        // Fixture labels carry no codec words → null (unknown, trusted to hardware).
        assertNull(streams[0].toPlayableSource("Friends S01E01")!!.videoCodec)
    }

    @Test
    fun `non-url sources refuse to map`() {
        assertNull(streams[2].toPlayableSource("Torrent"))  // infoHash only
        assertNull(streams[3].toPlayableSource("External")) // externalUrl only
    }

    @Test
    fun `addon subtitles extend the embedded list without shadowing it`() {
        val embedded = listOf(SubtitleTrack("https://cdn/embedded.srt", "eng"))
        val addonProvided = listOf(
            SubtitleTrack("https://cdn/opensubtitles-fr.srt", "fre"),
            SubtitleTrack("https://cdn/opensubtitles-en.srt", "eng"),
        )
        val merged = mergeSubtitleTracks(embedded, addonProvided)
        assertEquals(3, merged.size)
        assertEquals(embedded[0], merged[0])
    }

    @Test
    fun `addon subtitles dedupe by url against the embedded list`() {
        val embedded = listOf(SubtitleTrack("https://cdn/same.srt", "eng"))
        val addonProvided = listOf(SubtitleTrack("https://cdn/SAME.srt", "eng"))
        assertEquals(embedded, mergeSubtitleTracks(embedded, addonProvided))
    }

    @Test
    fun `subtitle object maps to a subtitle track`() {
        val subtitle = Subtitle(id = "1", url = "https://cdn/x.srt", lang = "eng")
        val track = subtitle.toSubtitleTrack()
        assertEquals("https://cdn/x.srt", track.url)
        assertEquals("eng", track.lang)
    }
}
