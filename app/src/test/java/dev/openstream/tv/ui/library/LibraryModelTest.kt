package dev.openstream.tv.ui.library

import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelTest {

    private fun progress(
        metaId: String,
        metaType: String = "series",
        externalId: String = "$metaId:1:1",
        title: String = metaId,
        positionMs: Long = 0,
        durationMs: Long = 100_000,
        updatedAt: Long = 0,
    ) = WatchProgress(
        ref = MediaRef("addon", externalId),
        metaId = metaId,
        metaType = metaType,
        title = title,
        poster = null,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
    )

    private val watchedPos = 96_000L   // past the 95% line
    private val partialPos = 50_000L

    @Test
    fun `series episodes collapse into one entry with finished count`() {
        val rows = listOf(
            progress("show", externalId = "show:1:1", positionMs = watchedPos, updatedAt = 1),
            progress("show", externalId = "show:1:2", positionMs = watchedPos, updatedAt = 2),
            progress("show", externalId = "show:1:3", positionMs = partialPos, updatedAt = 3),
        )
        val entries = LibraryModel.entries(rows)
        assertEquals(1, entries.size)
        assertEquals(2, entries[0].finishedCount)
        assertEquals(3, entries[0].lastWatchedAt)
        assertTrue(entries[0].isWatched)
    }

    @Test
    fun `latest row names the tile - freshest title and poster win`() {
        val rows = listOf(
            progress("show", externalId = "show:1:1", title = "Old Name", updatedAt = 1),
            progress("show", externalId = "show:1:2", title = "New Name", updatedAt = 2),
        )
        assertEquals("New Name", LibraryModel.entries(rows)[0].title)
    }

    @Test
    fun `entries come out most recent first`() {
        val rows = listOf(
            progress("a", updatedAt = 1),
            progress("b", updatedAt = 3),
            progress("c", updatedAt = 2),
        )
        assertEquals(listOf("b", "c", "a"), LibraryModel.entries(rows).map { it.metaId })
    }

    @Test
    fun `a partial movie is not watched - a finished one is`() {
        val partial = LibraryModel.entries(
            listOf(progress("m1", metaType = "movie", externalId = "m1", positionMs = partialPos))
        )
        val done = LibraryModel.entries(
            listOf(progress("m2", metaType = "movie", externalId = "m2", positionMs = watchedPos))
        )
        assertFalse(partial[0].isWatched)
        assertTrue(done[0].isWatched)
        assertEquals(1, done[0].finishedCount)
    }

    // --- filter bar ---

    private val entries = listOf(
        LibraryEntry("movie", "m1", "Alpha", null, lastWatchedAt = 5, finishedCount = 1),
        LibraryEntry("series", "s1", "beta show", null, lastWatchedAt = 9, finishedCount = 12),
        LibraryEntry("movie", "m2", "Charlie", null, lastWatchedAt = 7, finishedCount = 0),
    )

    @Test
    fun `last watched and all sort by recency`() {
        assertEquals(
            listOf("s1", "m2", "m1"),
            LibraryModel.apply(entries, LibraryFilter.LAST_WATCHED).map { it.metaId },
        )
        assertEquals(
            listOf("s1", "m2", "m1"),
            LibraryModel.apply(entries, LibraryFilter.ALL).map { it.metaId },
        )
    }

    @Test
    fun `alphabetical sorts are case-insensitive both ways`() {
        assertEquals(
            listOf("Alpha", "beta show", "Charlie"),
            LibraryModel.apply(entries, LibraryFilter.A_TO_Z).map { it.title },
        )
        assertEquals(
            listOf("Charlie", "beta show", "Alpha"),
            LibraryModel.apply(entries, LibraryFilter.Z_TO_A).map { it.title },
        )
    }

    @Test
    fun `most watched sorts by finished count then recency`() {
        val withTie = entries + LibraryEntry("movie", "m3", "Delta", null, lastWatchedAt = 8, finishedCount = 1)
        assertEquals(
            // s1 (12) first; the two 1-watch movies tie → newer m3 beats m1; m2 (0) last.
            listOf("s1", "m3", "m1", "m2"),
            LibraryModel.apply(withTie, LibraryFilter.MOST_WATCHED).map { it.metaId },
        )
    }

    @Test
    fun `watched and not watched split on finished plays`() {
        assertEquals(
            listOf("s1", "m1"),
            LibraryModel.apply(entries, LibraryFilter.WATCHED).map { it.metaId },
        )
        assertEquals(
            listOf("m2"),
            LibraryModel.apply(entries, LibraryFilter.NOT_WATCHED).map { it.metaId },
        )
    }

    @Test
    fun `type lens - movies vs everything series-like`() {
        val withChannel = entries + LibraryEntry("channel", "c1", "News", null, lastWatchedAt = 1, finishedCount = 0)
        assertEquals(
            listOf("m1", "m2"),
            LibraryModel.applyType(withChannel, LibraryType.MOVIES).map { it.metaId },
        )
        // channel counts as series-like: the split people mean is movies vs shows
        assertEquals(
            listOf("s1", "c1"),
            LibraryModel.applyType(withChannel, LibraryType.SERIES).map { it.metaId },
        )
        assertEquals(4, LibraryModel.applyType(withChannel, LibraryType.ALL).size)
    }
}
