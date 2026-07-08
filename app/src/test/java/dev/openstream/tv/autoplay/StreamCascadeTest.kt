package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamBehaviorHints
import dev.openstream.tv.autoplay.StreamCascade.AddonStreams
import dev.openstream.tv.autoplay.StreamCascade.CurrentStream
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
}
