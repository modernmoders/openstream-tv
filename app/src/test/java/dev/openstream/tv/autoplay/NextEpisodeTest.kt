package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {

    private fun ep(season: Int?, episode: Int?, id: String = "tt1:$season:$episode") =
        Video(id = id, season = season, episode = episode)

    @Test
    fun `next within a season`() {
        val videos = listOf(ep(1, 1), ep(1, 2), ep(1, 3))
        assertEquals("tt1:1:3", NextEpisode.nextAfter(videos, "tt1:1:2")?.id)
    }

    @Test
    fun `crosses season boundary`() {
        val videos = listOf(ep(1, 1), ep(1, 2), ep(2, 1))
        assertEquals("tt1:2:1", NextEpisode.nextAfter(videos, "tt1:1:2")?.id)
    }

    @Test
    fun `series end returns null`() {
        val videos = listOf(ep(1, 1), ep(1, 2))
        assertNull(NextEpisode.nextAfter(videos, "tt1:1:2"))
    }

    @Test
    fun `unknown current id returns null`() {
        val videos = listOf(ep(1, 1), ep(1, 2))
        assertNull(NextEpisode.nextAfter(videos, "tt1:9:9"))
    }

    @Test
    fun `previous within a season`() {
        val videos = listOf(ep(1, 1), ep(1, 2), ep(1, 3))
        assertEquals("tt1:1:1", NextEpisode.previousBefore(videos, "tt1:1:2")?.id)
    }

    @Test
    fun `previous crosses season boundary`() {
        val videos = listOf(ep(1, 1), ep(1, 2), ep(2, 1))
        assertEquals("tt1:1:2", NextEpisode.previousBefore(videos, "tt1:2:1")?.id)
    }

    @Test
    fun `series start returns null for previous`() {
        val videos = listOf(ep(1, 1), ep(1, 2))
        assertNull(NextEpisode.previousBefore(videos, "tt1:1:1"))
    }

    @Test
    fun `previous with unknown current id returns null`() {
        val videos = listOf(ep(1, 1), ep(1, 2))
        assertNull(NextEpisode.previousBefore(videos, "tt1:9:9"))
    }

    @Test
    fun `order is season-episode even when the array is shuffled`() {
        val videos = listOf(ep(2, 1), ep(1, 2), ep(1, 1))
        assertEquals(
            listOf("tt1:1:1", "tt1:1:2", "tt1:2:1"),
            NextEpisode.orderVideos(videos).map { it.id },
        )
    }

    @Test
    fun `specials sort after regular seasons, not between them`() {
        val videos = listOf(ep(0, 1), ep(1, 1), ep(2, 1))
        assertEquals(
            listOf("tt1:1:1", "tt1:2:1", "tt1:0:1"),
            NextEpisode.orderVideos(videos).map { it.id },
        )
        // finishing S1 goes to S2, not to the special
        assertEquals("tt1:2:1", NextEpisode.nextAfter(videos, "tt1:1:1")?.id)
        // the special is still reachable after the last regular episode
        assertEquals("tt1:0:1", NextEpisode.nextAfter(videos, "tt1:2:1")?.id)
    }

    @Test
    fun `unnumbered videos keep array order at the very end`() {
        val videos = listOf(ep(null, null, id = "up1"), ep(null, null, id = "up2"), ep(1, 1))
        assertEquals(
            listOf("tt1:1:1", "up1", "up2"),
            NextEpisode.orderVideos(videos).map { it.id },
        )
    }
}
