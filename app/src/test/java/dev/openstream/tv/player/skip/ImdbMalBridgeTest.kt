package dev.openstream.tv.player.skip

import dev.openstream.tv.addon.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImdbMalBridgeTest {

    // --- parseAnimeMap: the bundled asset's compact wire format ---

    @Test
    fun `parses the compact imdb to mal map`() {
        val json = """{"tt2560140":[[16498,1,0],[25777,2,0]],"tt0409591":[[20,-1,0]]}"""
        val map = parseAnimeMap(json)
        assertEquals(2, map.size)
        assertEquals(
            listOf(ImdbMalEntry(16498, 1, 0), ImdbMalEntry(25777, 2, 0)),
            map["tt2560140"],
        )
        assertEquals(listOf(ImdbMalEntry(20, ABSOLUTE_SEASON, 0)), map["tt0409591"])
    }

    @Test
    fun `malformed json parses to an empty map, never throws`() {
        assertTrue(parseAnimeMap("not json").isEmpty())
        assertTrue(parseAnimeMap("""{"tt1":[["x"]]}""").isEmpty())
        assertTrue(parseAnimeMap("").isEmpty())
    }

    @Test
    fun `rows with too few numbers are dropped, valid rows kept`() {
        val json = """{"tt1":[[20],[21,1,0]]}"""
        assertEquals(listOf(ImdbMalEntry(21, 1, 0)), parseAnimeMap(json)["tt1"])
    }

    // --- resolveMalEpisode: (season, episode) → (MAL id, MAL episode) ---

    private val aot = listOf(
        ImdbMalEntry(16498, 1, 0),
        ImdbMalEntry(25777, 2, 0),
        ImdbMalEntry(35760, 3, 0),
        ImdbMalEntry(40028, 4, 0),
    )

    @Test
    fun `each tvdb season maps to its own MAL entry`() {
        assertEquals(MalEpisode(25777, 5), resolveMalEpisode(aot, season = 2, episode = 5, absoluteEpisode = null))
        assertEquals(MalEpisode(16498, 1), resolveMalEpisode(aot, season = 1, episode = 1, absoluteEpisode = null))
    }

    @Test
    fun `split-cour season picks the entry by episode offset`() {
        // AoT Final Season: one tvdb season, three MAL entries at offsets 0/16/28.
        val split = listOf(
            ImdbMalEntry(40028, 4, 0),
            ImdbMalEntry(48583, 4, 16),
            ImdbMalEntry(51535, 4, 28),
        )
        assertEquals(MalEpisode(40028, 16), resolveMalEpisode(split, 4, 16, null))
        assertEquals(MalEpisode(48583, 1), resolveMalEpisode(split, 4, 17, null))
        assertEquals(MalEpisode(51535, 2), resolveMalEpisode(split, 4, 30, null))
    }

    @Test
    fun `absolute-numbered show uses the absolute episode`() {
        // Naruto: tvdb seasons, one MAL entry over the absolute order.
        val naruto = listOf(ImdbMalEntry(20, ABSOLUTE_SEASON, 0))
        assertEquals(MalEpisode(20, 40), resolveMalEpisode(naruto, season = 2, episode = 5, absoluteEpisode = 40))
    }

    @Test
    fun `season 1 falls back to episode-as-absolute when no episode list exists`() {
        // Single-cour anime marked absolute: S1E7 IS absolute episode 7.
        val entries = listOf(ImdbMalEntry(30276, ABSOLUTE_SEASON, 0))
        assertEquals(MalEpisode(30276, 7), resolveMalEpisode(entries, 1, 7, absoluteEpisode = null))
        // …but a later season without an absolute number cannot be guessed.
        assertNull(resolveMalEpisode(entries, 2, 7, absoluteEpisode = null))
    }

    @Test
    fun `absolute entries can be split by offset too`() {
        val entries = listOf(
            ImdbMalEntry(20, ABSOLUTE_SEASON, 0),
            ImdbMalEntry(1735, ABSOLUTE_SEASON, 220),
        )
        assertEquals(MalEpisode(20, 220), resolveMalEpisode(entries, 5, 15, absoluteEpisode = 220))
        assertEquals(MalEpisode(1735, 1), resolveMalEpisode(entries, 6, 1, absoluteEpisode = 221))
    }

    @Test
    fun `specials and unknown seasons resolve to nothing`() {
        assertNull(resolveMalEpisode(aot, season = 0, episode = 1, absoluteEpisode = null))
        assertNull(resolveMalEpisode(aot, season = 9, episode = 1, absoluteEpisode = null))
        assertNull(resolveMalEpisode(emptyList(), season = 1, episode = 1, absoluteEpisode = null))
    }

    // --- absoluteEpisodeNumber: seasonal → absolute from the app's own list ---

    private fun video(id: String, season: Int?, episode: Int?) =
        Video(id = id, season = season, episode = episode)

    private val twoSeasons = listOf(
        video("tt1:0:1", 0, 1), // a special — must not count
        video("tt1:1:1", 1, 1),
        video("tt1:1:2", 1, 2),
        video("tt1:1:3", 1, 3),
        video("tt1:2:1", 2, 1),
        video("tt1:2:2", 2, 2),
    )

    @Test
    fun `absolute number counts prior seasons, skipping specials`() {
        assertEquals(5, absoluteEpisodeNumber(twoSeasons, season = 2, episode = 2))
        assertEquals(1, absoluteEpisodeNumber(twoSeasons, season = 1, episode = 1))
    }

    @Test
    fun `absolute number is null when the episode is not in the list`() {
        assertNull(absoluteEpisodeNumber(twoSeasons, season = 3, episode = 1))
        assertNull(absoluteEpisodeNumber(emptyList(), season = 1, episode = 1))
    }

    @Test
    fun `out-of-order lists are sorted by season then episode`() {
        val shuffled = twoSeasons.shuffled(kotlin.random.Random(7))
        assertEquals(5, absoluteEpisodeNumber(shuffled, season = 2, episode = 2))
    }
}

/**
 * Guards the COMMITTED asset (regenerated by tools/build_anime_map.py): if a
 * bad upstream sync ever corrupts or empties it, this fails at the gate
 * instead of silently shipping a dead skip feature.
 */
class BundledAnimeMapAssetTest {

    private fun assetText(): String {
        val candidates = listOf(
            java.io.File("src/main/assets/anime_imdb_mal.json"),
            java.io.File("app/src/main/assets/anime_imdb_mal.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("asset not found from ${java.io.File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun `the committed asset parses and covers thousands of shows`() {
        val map = parseAnimeMap(assetText())
        assertTrue("expected thousands of shows, got ${map.size}", map.size > 3000)
    }

    @Test
    fun `the owner's known cases resolve from the committed asset`() {
        val map = parseAnimeMap(assetText())
        // Naruto is absolute-numbered: S2E5 with absolute 40 → MAL 20 ep 40.
        assertEquals(
            MalEpisode(20, 40),
            resolveMalEpisode(map.getValue("tt0409591"), 2, 5, absoluteEpisode = 40),
        )
        // Attack on Titan S2 is its own MAL entry, episodes restart at 1.
        assertEquals(
            MalEpisode(25777, 5),
            resolveMalEpisode(map.getValue("tt2560140"), 2, 5, absoluteEpisode = 30),
        )
    }
}
