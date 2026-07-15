package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamBehaviorHints
import dev.openstream.tv.domain.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-20 #9 bug-check pass: the label parsers eat FREE TEXT from addons the
 * app doesn't control — every visual tag, encoding marker, language pennant
 * and separator style the ecosystem produces (plus garbage) must never crash
 * a parser and must keep the "never demote what we can't reason about"
 * contract. The corpus below is drawn from real AIOStreams/Torrentio label
 * shapes seen on the owner's instances.
 */
class StreamLabelCorpusTest {

    private fun stream(
        name: String? = null,
        description: String? = null,
        filename: String? = null,
    ) = Stream(
        url = "https://cdn.example/v.mp4",
        name = name,
        description = description,
        behaviorHints = StreamBehaviorHints(filename = filename),
    )

    /** Real-world-shaped label fragments: tags, encodings, languages, junk. */
    private val corpus = listOf(
        // Plain releases and dotted filenames
        "Show.S01E03.1080p.WEB.H264-GROUP.mkv",
        "Movie.2024.2160p.UHD.BluRay.x265.10bit.HDR.TrueHD.7.1.Atmos-TERM.mkv",
        "Naruto.S02E05.480p.DVDRip.XviD.mp3",
        // Visual/HDR tags in every popular spelling
        "🎥 4k HDR10+ DV | x265 Main10 ⚡ Cached",
        "1080p Dolby.Vision 10-bit HEVC",
        "720p SDR AVC AAC 2.0",
        // AIOStreams pennant labels (small caps + separators)
        "⛿ ᴇɴ · ᴊᴀ » 💾 1.2 GB",
        "⛿ ɪᴛ | WEBRip",
        "⛿ ᴊᴀ · sᴜʙ (ᴇɴ) » S01 pack",
        "⛿",
        // Bracket language listings (the Round-19 WIKIRIP shape)
        "[EAC3 JPN ITA Sub ITA ENG]",
        "[AC3 ENG JPN Sub ENG]",
        "[DTS-HD MA 5.1] [Multi-Sub]",
        // Title words that LOOK like language codes must stay neutral
        "The.Spa.2024.1080p.WEB.mkv",
        "Ita.Movie.About.Italy.720p.mkv",
        // Cache / provider markers
        "⚡ Instant | RD+ | 👤 12 seeders",
        "[TB Cached] 1080p WEB-DL",
        // Codec zoo
        "AV1 2160p opus", "vp9 1080p", "h.265 720p", "x.264 480p", "HEVC 1440p",
        // Unicode stress: CJK, RTL, emoji soup, combining marks
        "進撃の巨人 S04E28 1080p", "مسلسل الحفرة 720p", "🍿🎬📺🔥💯",
        "Amélie.2001.1080p.é̶̤͝m̸̘̈ój̶i̷.mkv",
        // Degenerate inputs
        "", " ", "....", "]][[", "⛿⛿⛿ sub sub sub", "a".repeat(4000),
    )

    @Test
    fun `every parser survives the whole corpus in every field`() {
        // Each fragment rides in as name, description, and filename — plus
        // an all-three combination — through every public entry point.
        val shapes = corpus.flatMap { text ->
            listOf(
                stream(name = text),
                stream(description = text),
                stream(filename = text),
                stream(name = text, description = text, filename = text),
            )
        } + stream() // all-null fields
        for (s in shapes) {
            // No assertion beyond type sanity: the test IS "does not throw",
            // plus the contracts that unknown input never demotes.
            val english = StreamCascade.hasEnglishAudio(s)
            val codec = StreamCascade.videoCodecOf(s)
            val res = StreamCascade.resolutionOf(s)
            val rank = StreamCascade.resolutionRank(s)
            StreamCascade.hasCacheMarker(s)
            assertNotNull(StreamCascade.dedupKey(s))
            val tokens = StreamCascade.normalizedTokens(s)
            assertEquals(1.0, StreamCascade.tokenSimilarity(tokens, tokens).takeIf { tokens.isNotEmpty() } ?: 1.0, 0.0)
            assertTrue(StreamCascade.canHardwareDecode(s, emptySet()))
            if (res == null) assertEquals(0, rank)
            if (codec == null) assertTrue(StreamCascade.canHardwareDecode(s, setOf(VideoCodec.H264)))
            // `english` is Boolean by type; touching it keeps the call live.
            @Suppress("UNUSED_EXPRESSION") english
        }
    }

    // --- targeted expectations across the visual-tag / encoding corpus ---

    @Test
    fun `pennant small caps decide audio language, subs never count`() {
        assertTrue(StreamCascade.hasEnglishAudio(stream(description = "⛿ ᴇɴ · ᴊᴀ » 💾 1.2 GB")))
        assertFalse(StreamCascade.hasEnglishAudio(stream(description = "⛿ ɪᴛ | WEBRip")))
        assertFalse(StreamCascade.hasEnglishAudio(stream(description = "⛿ ᴊᴀ · sᴜʙ (ᴇɴ) » S01 pack")))
        // A bare pennant carries no info — neutral, never a demotion.
        assertTrue(StreamCascade.hasEnglishAudio(stream(description = "⛿")))
    }

    @Test
    fun `filename listings outrank the pennant in both directions`() {
        // Lying pennant says EN — filename says audio is JPN+ITA only.
        assertFalse(
            StreamCascade.hasEnglishAudio(
                stream(description = "⛿ ᴇɴ · ᴊᴀ", filename = "[EAC3 JPN ITA Sub ITA ENG]"),
            ),
        )
        // And the reverse: pennant says IT, filename lists English audio.
        assertTrue(
            StreamCascade.hasEnglishAudio(
                stream(description = "⛿ ɪᴛ", filename = "[AC3 ENG JPN Sub ENG]"),
            ),
        )
        // Title words never count as a listing ("The.Spa" ≠ Spanish).
        assertTrue(StreamCascade.hasEnglishAudio(stream(filename = "The.Spa.2024.1080p.WEB.mkv")))
        assertTrue(StreamCascade.hasEnglishAudio(stream(filename = "Ita.Movie.About.Italy.720p.mkv")))
    }

    @Test
    fun `encoding tags parse to the right codec, ten-bit flavors included`() {
        assertEquals(
            VideoCodec.HEVC_10BIT,
            StreamCascade.videoCodecOf(
                stream(name = "Movie.2024.2160p.UHD.BluRay.x265.10bit.HDR.TrueHD.7.1.Atmos-TERM.mkv"),
            ),
        )
        assertEquals(VideoCodec.HEVC_10BIT, StreamCascade.videoCodecOf(stream(name = "1080p Dolby.Vision HEVC")))
        assertEquals(VideoCodec.HEVC, StreamCascade.videoCodecOf(stream(name = "h.265 720p")))
        assertEquals(VideoCodec.AV1, StreamCascade.videoCodecOf(stream(name = "AV1 2160p opus")))
        assertEquals(VideoCodec.VP9, StreamCascade.videoCodecOf(stream(name = "vp9 1080p")))
        assertEquals(VideoCodec.H264, StreamCascade.videoCodecOf(stream(name = "720p SDR AVC AAC 2.0")))
        assertEquals(null, StreamCascade.videoCodecOf(stream(name = "Naruto.S02E05.480p.DVDRip.XviD.mp3")))
    }

    @Test
    fun `resolution and cache markers read through emoji clutter`() {
        assertEquals("4k", StreamCascade.resolutionOf(stream(name = "🎥 4k HDR10+ DV | x265 Main10 ⚡ Cached")))
        assertEquals("4k", StreamCascade.resolutionOf(stream(name = "Movie.2160p.mkv")))
        assertEquals("1440p", StreamCascade.resolutionOf(stream(name = "HEVC 1440p")))
        assertTrue(StreamCascade.hasCacheMarker(stream(name = "⚡ Instant | RD+ | 👤 12 seeders")))
        assertTrue(StreamCascade.hasCacheMarker(stream(name = "[TB Cached] 1080p WEB-DL")))
        assertFalse(StreamCascade.hasCacheMarker(stream(name = "Show.S01E03.1080p.WEB.H264-GROUP.mkv")))
    }
}
