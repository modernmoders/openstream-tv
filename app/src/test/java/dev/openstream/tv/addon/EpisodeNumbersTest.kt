package dev.openstream.tv.addon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeNumbersTest {

    private fun video(season: Int, episode: Int) =
        Video(id = "s${season}e$episode", season = season, episode = episode)

    // A Naruto-shaped fixture: 7 specials (season 0) then seasons 1..3 with
    // 35, 48, 48 episodes. Absolute numbering ignores specials and counts
    // straight through the real seasons.
    private val naruto: List<Video> = buildList {
        (1..7).forEach { add(video(0, it)) }
        (1..35).forEach { add(video(1, it)) }
        (1..48).forEach { add(video(2, it)) }
        (1..48).forEach { add(video(3, it)) }
    }

    @Test
    fun `absolute counts straight through seasons, specials excluded`() {
        val abs = absoluteEpisodeNumbers(naruto)
        // Season 3 Episode 32 = 35 + 48 + 32 = 115 ("Your Opponent Is Me!").
        assertEquals(115, abs["s3e32"])
        assertEquals(1, abs["s1e1"])
        assertEquals(35, abs["s1e35"])
        assertEquals(36, abs["s2e1"])
        assertEquals(83, abs["s2e48"])
        assertEquals(84, abs["s3e1"])
    }

    @Test
    fun `specials (season 0) get no absolute number`() {
        val abs = absoluteEpisodeNumbers(naruto)
        assertNull(abs["s0e1"])
        assertNull(abs["s0e7"])
    }

    @Test
    fun `out-of-order input is sorted before counting`() {
        val shuffled = listOf(video(2, 1), video(1, 2), video(1, 1), video(2, 2))
        val abs = absoluteEpisodeNumbers(shuffled)
        assertEquals(1, abs["s1e1"])
        assertEquals(2, abs["s1e2"])
        assertEquals(3, abs["s2e1"])
        assertEquals(4, abs["s2e2"])
    }

    @Test
    fun `videos with a blank id are ignored`() {
        val abs = absoluteEpisodeNumbers(
            listOf(Video(id = "", season = 1, episode = 1), video(1, 2)),
        )
        assertNull(abs[""])
        assertEquals(1, abs["s1e2"])
    }
}
