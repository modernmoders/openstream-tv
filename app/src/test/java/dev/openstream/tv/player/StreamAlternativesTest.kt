package dev.openstream.tv.player

import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamBehaviorHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamAlternativesTest {

    private fun stream(filename: String, name: String? = null) = Stream(
        url = "https://cdn.example/v.mp4",
        name = name,
        description = null,
        behaviorHints = StreamBehaviorHints(filename = filename),
    )

    private fun alt(filename: String) =
        StreamAlternatives.Alternative("https://aio.example", stream(filename))

    // Real shape of the owner's 2026-07-12 report: the auto-picked WIKIRIP
    // season pack glitched; "Try a different stream" walked straight onto the
    // per-episode WIKIRIP sibling — same encode, same glitch.
    private val wikiripPack =
        stream("Naruto (2002) [S01][1080p H265][EAC3 JPN ITA Sub ITA ENG][16.3 GiB][WIKIRIP]")
    private val wikiripEpisode =
        alt("Naruto - S01E13 - Gli specchi diabolici di Haku [1080p H265][EAC3 JPN ITA Sub ITA ENG][tt0409591] [WIKIRIP].mkv")
    private val ivy = alt("Naruto.S01E13.Haku's.Secret.Jutsu.EAC3.2.0.1080p.Bluray.x265-iVy.mkv")
    private val seadex = alt("Naruto.013.v4.480p.DVD.Dual-Audio.FLAC.mkv")

    @Test
    fun `plain advance walks in order`() {
        val alts = StreamAlternatives().apply {
            list = listOf(wikiripEpisode, ivy)
            currentIndex = -1
        }
        assertEquals(wikiripEpisode, alts.advance())
        assertEquals(ivy, alts.advance())
        assertNull(alts.advance())
    }

    @Test
    fun `preferring different skips the same-release sibling`() {
        val alts = StreamAlternatives().apply {
            list = listOf(wikiripEpisode, ivy, seadex)
            currentIndex = -1
        }
        // The glitched WIKIRIP pack is playing; "different" must not be the
        // WIKIRIP per-episode sibling.
        assertEquals(ivy, alts.advance(preferDifferentFrom = wikiripPack))
        // The walk continues forward from the pick.
        assertEquals(seadex, alts.advance())
    }

    @Test
    fun `preferring different falls back to the sibling when nothing else remains`() {
        val alts = StreamAlternatives().apply {
            list = listOf(wikiripEpisode)
            currentIndex = -1
        }
        // A similar stream beats a dead end.
        assertEquals(wikiripEpisode, alts.advance(preferDifferentFrom = wikiripPack))
    }

    @Test
    fun `no current stream means the plain walk`() {
        val alts = StreamAlternatives().apply {
            list = listOf(wikiripEpisode, ivy)
            currentIndex = -1
        }
        assertEquals(wikiripEpisode, alts.advance(preferDifferentFrom = null))
    }
}
