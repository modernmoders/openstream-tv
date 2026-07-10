package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamBehaviorHints
import dev.openstream.tv.autoplay.StreamCascade.AddonStreams
import dev.openstream.tv.autoplay.StreamCascade.CurrentStream
import dev.openstream.tv.autoplay.StreamCascade.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCascadeTest {

    private fun stream(
        url: String? = "https://cdn.example/v.mp4",
        name: String? = null,
        description: String? = null,
        bingeGroup: String? = null,
        filename: String? = null,
    ) = Stream(
        url = url,
        name = name,
        description = description,
        behaviorHints = StreamBehaviorHints(bingeGroup = bingeGroup, filename = filename),
    )

    private fun current(
        addonUrl: String = "https://aio.example",
        stream: Stream = stream(name = "AIOStreams 1080p", filename = "Show.S01E03.1080p.WEB.GROUP.mkv"),
    ) = CurrentStream(addonUrl, stream)

    // --- tier 1: bingeGroup ---

    @Test
    fun `bingeGroup match beats everything, even a lower-priority addon`() {
        val cur = current(stream = stream(name = "AIOStreams 1080p", bingeGroup = "aio|1080p|WEB"))
        val ranked = StreamCascade.rank(
            cur,
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "AIOStreams 4K", description = "some other rip"),
                )),
                AddonStreams("https://other.example", 1, listOf(
                    stream(name = "Other 1080p", bingeGroup = "aio|1080p|WEB"),
                )),
            ),
        )
        assertEquals("Other 1080p", ranked.first().stream.name)
    }

    @Test
    fun `null current bingeGroup never matches null candidate bingeGroup`() {
        val cur = current(stream = stream(name = "AIOStreams 1080p")) // no bingeGroup
        val ranked = StreamCascade.rank(
            cur,
            listOf(
                AddonStreams("https://other.example", 1, listOf(stream(name = "no-binge"))),
                AddonStreams("https://aio.example", 0, listOf(stream(name = "same addon"))),
            ),
        )
        // falls to tier 2: same addon wins, no accidental null==null tier-1 hit
        assertEquals("same addon", ranked.first().stream.name)
    }

    // --- tier 2: same addon > same resolution > filename pattern > cache flag ---

    @Test
    fun `same addon outranks same resolution elsewhere`() {
        val cur = current()
        val ranked = StreamCascade.rank(
            cur,
            listOf(
                AddonStreams("https://other.example", 0, listOf(stream(name = "Other 1080p"))),
                AddonStreams("https://aio.example", 1, listOf(stream(name = "AIO 720p"))),
            ),
        )
        assertEquals("AIO 720p", ranked.first().stream.name)
    }

    @Test
    fun `same resolution breaks ties within the same addon`() {
        val cur = current()
        val ranked = StreamCascade.rank(
            cur,
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "AIO 4K", filename = "Show.S01E04.2160p.WEB.OTHER.mkv"),
                    stream(name = "AIO 1080p", filename = "Show.S01E04.1080p.WEB.OTHER.mkv"),
                )),
            ),
        )
        assertEquals("AIO 1080p", ranked.first().stream.name)
    }

    @Test
    fun `filename pattern breaks ties when addon and resolution are equal`() {
        val cur = current()
        val ranked = StreamCascade.rank(
            cur,
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "AIO 1080p", filename = "Show.S01E04.1080p.BluRay.DIFFERENT.RELEASE.mkv"),
                    stream(name = "AIO 1080p", filename = "Show.S01E04.1080p.WEB.GROUP.mkv"),
                )),
            ),
        )
        assertEquals("Show.S01E04.1080p.WEB.GROUP.mkv", ranked.first().stream.behaviorHints.filename)
    }

    @Test
    fun `cache flag is the last tier-2 tie-break`() {
        val cur = current(stream = stream(name = "⚡ AIOStreams 1080p", filename = "Show.S01E03.1080p.WEB.GROUP.mkv"))
        val ranked = StreamCascade.rank(
            cur,
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "AIO 1080p uncached", filename = "Show.S01E04.1080p.WEB.GROUP.mkv"),
                    stream(name = "⚡ AIO 1080p cached", filename = "Show.S01E04.1080p.WEB.GROUP.mkv"),
                )),
            ),
        )
        assertEquals("⚡ AIO 1080p cached", ranked.first().stream.name)
    }

    // --- tier 3 + hygiene ---

    @Test
    fun `no signals at all falls back to addon priority then server order`() {
        val cur = current(stream = stream(name = null, filename = null))
        val ranked = StreamCascade.rank(
            cur,
            listOf(
                AddonStreams("https://b.example", 1, listOf(stream(name = "b-first"), stream(name = "b-second"))),
                AddonStreams("https://a.example", 0, listOf(stream(name = "a-first"), stream(name = "a-second"))),
            ),
        )
        // current addon "https://aio.example" matches neither group → pure priority order
        assertEquals(listOf("a-first", "a-second", "b-first", "b-second"), ranked.map { it.stream.name })
    }

    @Test
    fun `unplayable streams are excluded`() {
        val ranked = StreamCascade.rank(
            current(),
            listOf(AddonStreams("https://aio.example", 0, listOf(
                Stream(infoHash = "abc123"), // torrent — not playable in v1
                stream(name = "playable"),
            ))),
        )
        assertEquals(listOf("playable"), ranked.map { it.stream.name })
    }

    @Test
    fun `empty groups rank to empty`() {
        assertTrue(StreamCascade.rank(current(), emptyList()).isEmpty())
    }

    // --- feature extraction ---

    @Test
    fun `resolution parses from any label field and 2160p folds into 4k`() {
        assertEquals("1080p", StreamCascade.resolutionOf(stream(name = "AIO 1080P")))
        assertEquals("4k", StreamCascade.resolutionOf(stream(description = "big 2160p rip")))
        assertEquals("4k", StreamCascade.resolutionOf(stream(filename = "Show.4K.mkv")))
        assertEquals(null, StreamCascade.resolutionOf(stream(name = "no res here")))
    }

    @Test
    fun `cache marker detects lightning bolt and words`() {
        assertTrue(StreamCascade.hasCacheMarker(stream(name = "⚡ AIO")))
        assertTrue(StreamCascade.hasCacheMarker(stream(description = "RD cached")))
        assertTrue(StreamCascade.hasCacheMarker(stream(description = "Instant play")))
        assertFalse(StreamCascade.hasCacheMarker(stream(name = "plain")))
    }

    @Test
    fun `episode numbers are normalized out of the token set`() {
        val a = StreamCascade.normalizedTokens(stream(filename = "Show.S01E03.1080p.WEB.GROUP.mkv"))
        val b = StreamCascade.normalizedTokens(stream(filename = "Show.S01E04.1080p.WEB.GROUP.mkv"))
        assertEquals(a, b)
        assertEquals(1.0, StreamCascade.tokenSimilarity(a, b), 0.0)
    }

    @Test
    fun `similarity of disjoint or empty sets is zero`() {
        assertEquals(0.0, StreamCascade.tokenSimilarity(emptySet(), setOf("web")), 0.0)
        assertEquals(0.0, StreamCascade.tokenSimilarity(setOf("bluray"), setOf("web")), 0.0)
    }

    // --- mergeForDisplay: interweave + dedupe + rank (owner 2026-07-09) ---

    @Test
    fun `mergeForDisplay collapses the same release returned by multiple addons`() {
        val merged = StreamCascade.mergeForDisplay(
            listOf(
                AddonStreams("https://aio1.example", 0, listOf(
                    stream(name = "AIO1", filename = "Show.S01E01.1080p.WEB.GROUP.mkv"),
                )),
                AddonStreams("https://aio2.example", 1, listOf(
                    stream(name = "AIO2", filename = "Show.S01E01.1080p.WEB.GROUP.mkv"),
                )),
            ),
        )
        assertEquals(1, merged.size)
        // the copy kept is from the earliest addon
        assertEquals("https://aio1.example", merged.first().addonUrl)
    }

    @Test
    fun `mergeForDisplay ranks cached before uncached regardless of resolution`() {
        val merged = StreamCascade.mergeForDisplay(
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "4K uncached", filename = "a.2160p.mkv"),
                    stream(name = "720p cached", filename = "b.720p.mkv", description = "cached"),
                )),
            ),
        )
        assertEquals("720p cached", merged.first().stream.name)
    }

    @Test
    fun `mergeForDisplay orders by resolution within the cached tier`() {
        val merged = StreamCascade.mergeForDisplay(
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "cached 720p", filename = "a.720p.mkv", description = "cached"),
                    stream(name = "cached 4k", filename = "b.2160p.mkv", description = "cached"),
                    stream(name = "cached 1080p", filename = "c.1080p.mkv", description = "cached"),
                )),
            ),
        )
        assertEquals(
            listOf("cached 4k", "cached 1080p", "cached 720p"),
            merged.map { it.stream.name },
        )
    }

    // --- codec awareness (owner 2026-07-09: prefer streams the box can decode) ---

    @Test
    fun `videoCodecOf reads codec and bit-depth from the label`() {
        assertEquals(VideoCodec.H264, StreamCascade.videoCodecOf(stream(filename = "Show.1080p.x264-GRP.mkv")))
        assertEquals(VideoCodec.HEVC, StreamCascade.videoCodecOf(stream(filename = "Show.1080p.x265-GRP.mkv")))
        assertEquals(VideoCodec.HEVC_10BIT, StreamCascade.videoCodecOf(stream(name = "Show HEVC 10bit HDR")))
        assertEquals(VideoCodec.AV1, StreamCascade.videoCodecOf(stream(filename = "Show.AV1.mkv")))
        assertEquals(null, StreamCascade.videoCodecOf(stream(name = "Show 1080p")))
    }

    @Test
    fun `mergeForDisplay floats hardware-decodable above what the box cannot decode`() {
        // Box does H.264 + HEVC-8bit but NOT HEVC-10bit: a clean 1080p H.264 must
        // beat a 4K HEVC-10bit that would macroblock / force the software player.
        val merged = StreamCascade.mergeForDisplay(
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "4K HEVC 10bit", filename = "a.2160p.x265.10bit.mkv"),
                    stream(name = "1080p H264", filename = "b.1080p.x264.mkv"),
                )),
            ),
            hardwareCodecs = setOf(VideoCodec.H264, VideoCodec.HEVC),
        )
        assertEquals("1080p H264", merged.first().stream.name)
    }

    // --- audio language (owner 2026-07-10: read the Audio field, not the tag) ---

    @Test
    fun `hasEnglishAudio reads the Audio section, not the overall tag`() {
        // Release tagged English overall, but the Audio field says Italian.
        assertFalse(StreamCascade.hasEnglishAudio(
            stream(name = "Movie 1080p English", description = "Audio: 🇮🇹 Italian")
        ))
        assertFalse(StreamCascade.hasEnglishAudio(
            stream(description = "Audio: Japanese")
        ))
        assertTrue(StreamCascade.hasEnglishAudio(
            stream(description = "Audio: English")
        ))
        // Dual audio that names English counts as English.
        assertTrue(StreamCascade.hasEnglishAudio(
            stream(description = "Audio: English, Japanese")
        ))
        // No Audio section at all -> neutral, never demoted.
        assertTrue(StreamCascade.hasEnglishAudio(stream(name = "Movie 1080p")))
    }

    @Test
    fun `mergeForDisplay floats English audio above foreign audio`() {
        val merged = StreamCascade.mergeForDisplay(
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "4K Italian", filename = "a.2160p.x264.mkv", description = "Audio: Italian"),
                    stream(name = "720p English", filename = "b.720p.x264.mkv", description = "Audio: English"),
                )),
            ),
            hardwareCodecs = setOf(VideoCodec.H264),
        )
        // Unwatchable Italian must not out-rank a watchable English stream,
        // even at 4K.
        assertEquals("720p English", merged.first().stream.name)
    }

    @Test
    fun `mergeForDisplay applies no codec penalty when box capabilities are unknown`() {
        // Empty capability set -> everything treated as playable, so 4K wins.
        val merged = StreamCascade.mergeForDisplay(
            listOf(
                AddonStreams("https://aio.example", 0, listOf(
                    stream(name = "1080p H264", filename = "b.1080p.x264.mkv"),
                    stream(name = "4K HEVC 10bit", filename = "a.2160p.x265.10bit.mkv"),
                )),
            ),
        )
        assertEquals("4K HEVC 10bit", merged.first().stream.name)
    }
}
