package dev.openstream.tv.ui.streams

import dev.openstream.tv.addon.AddonJson
import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Manifest
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.player.StreamAlternatives
import dev.openstream.tv.ui.streams.StreamListViewModel.GroupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-play-first-stream timing rule (owner request 2026-07-06): the top
 * stream is only final once every addon that could still claim the top spot
 * has settled — plus the "Try another server" walk order.
 */
class AutoStartSelectionTest {

    private val manifest = AddonJson.decodeFromString(
        Manifest.serializer(), Fixtures.load("manifest_full")
    )

    private fun addon(url: String) = InstalledAddon(url, manifest, enabled = true, sortOrder = 0)

    private val playable = Stream(name = "Good", url = "http://example.invalid/v.mp4")
    private val playable2 = Stream(name = "Good 2", url = "http://example.invalid/v2.mp4")
    private val torrentOnly = Stream(name = "Torrent", infoHash = "abc")
    private val tagged = Stream(name = "🇪🇸 Spanish", url = "http://example.invalid/es.mp4")

    @Test
    fun `initializing means wait`() {
        assertEquals(AutoStartResult.Waiting, bestPlayableWhenSettled(true, emptyList()))
    }

    @Test
    fun `slow first addon keeps its top spot - wait`() {
        val groups = listOf(
            GroupState.Loading(addon("a")),
            GroupState.Loaded(addon("b"), listOf(playable)),
        )
        assertEquals(AutoStartResult.Waiting, bestPlayableWhenSettled(false, groups))
    }

    @Test
    fun `failed first addon passes the top spot down`() {
        val groups = listOf(
            GroupState.Failed(addon("a"), "boom"),
            GroupState.Loaded(addon("b"), listOf(playable)),
        )
        val found = bestPlayableWhenSettled(false, groups) as AutoStartResult.Found
        assertEquals("b", found.addon.manifestUrl)
    }

    @Test
    fun `unplayable-only group is skipped`() {
        val groups = listOf(
            GroupState.Loaded(addon("a"), listOf(torrentOnly)),
            GroupState.Loaded(addon("b"), listOf(torrentOnly, playable)),
        )
        val found = bestPlayableWhenSettled(false, groups) as AutoStartResult.Found
        assertEquals(playable, found.stream)
    }

    @Test
    fun `waits for every source before the auto-pick`() {
        // A still-loading source could carry the better (e.g. hardware-decodable)
        // stream, so the best-first pick isn't final until ALL sources settle
        // (owner 2026-07-09 — supersedes the old first-in-addon-order pick).
        val groups = listOf(
            GroupState.Loaded(addon("a"), listOf(playable)),
            GroupState.Loading(addon("b")),
        )
        assertEquals(AutoStartResult.Waiting, bestPlayableWhenSettled(false, groups))
    }

    @Test
    fun `everything settled with nothing playable means none`() {
        val groups = listOf(
            GroupState.Failed(addon("a"), "boom"),
            GroupState.Loaded(addon("b"), listOf(torrentOnly)),
            GroupState.Loaded(addon("c"), emptyList()),
        )
        assertEquals(AutoStartResult.None, bestPlayableWhenSettled(false, groups))
    }

    @Test
    fun `alternatives flatten in addon order and drop unplayables`() {
        val groups = listOf(
            GroupState.Loaded(addon("a"), listOf(torrentOnly, playable)),
            GroupState.Failed(addon("b"), "boom"),
            GroupState.Loaded(addon("c"), listOf(playable2)),
        )
        val alts = orderedAlternatives(groups)
        assertEquals(
            listOf("a" to playable, "c" to playable2),
            alts.map { it.addonUrl to it.stream },
        )
    }

    @Test
    fun `a language-tagged stream still auto-plays - no audio filtering`() {
        // Owner 2026-07-08: the auto-pick is pure addon order — a stream is
        // never skipped for the language its label advertises. The first
        // playable stream (here a Spanish-tagged one) plays.
        val groups = listOf(
            GroupState.Loaded(addon("a"), listOf(tagged)),
            GroupState.Loaded(addon("b"), listOf(playable)),
        )
        val found = bestPlayableWhenSettled(false, groups) as AutoStartResult.Found
        assertEquals(tagged, found.stream)
    }

    @Test
    fun `alternatives keep pure addon order regardless of language tags`() {
        val groups = listOf(
            GroupState.Loaded(addon("a"), listOf(tagged)),
            GroupState.Loaded(addon("b"), listOf(playable)),
        )
        val alts = orderedAlternatives(groups)
        assertEquals(listOf(tagged, playable), alts.map { it.stream })
    }

    @Test
    fun `alternatives walk advances without wrapping`() {
        val holder = StreamAlternatives()
        holder.list = listOf(
            StreamAlternatives.Alternative("a", playable),
            StreamAlternatives.Alternative("a", playable2),
        )
        holder.currentIndex = 0
        assertTrue(holder.hasNext())
        assertEquals(playable2, holder.advance()?.stream)
        assertFalse(holder.hasNext())
        assertNull(holder.advance()) // no wrap: exhausted stays exhausted
        holder.clear()
        assertFalse(holder.hasNext())
    }

    @Test
    fun `staged stream not in the list walks from the top`() {
        val holder = StreamAlternatives()
        holder.list = listOf(StreamAlternatives.Alternative("a", playable))
        holder.currentIndex = -1 // indexOf miss
        assertTrue(holder.hasNext())
        assertEquals(playable, holder.advance()?.stream)
    }
}
