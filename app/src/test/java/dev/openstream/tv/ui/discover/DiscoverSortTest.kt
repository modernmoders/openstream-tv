package dev.openstream.tv.ui.discover

import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.data.DiscoverSortMode
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverSortTest {

    private val items = listOf(
        MetaItem(id = "1", name = "zeta", releaseInfo = "2019-2023", imdbRating = "6.1"),
        MetaItem(id = "2", name = "Alpha", releaseInfo = null, imdbRating = "9.0"),
        MetaItem(id = "3", name = "beta", releaseInfo = "2024", imdbRating = null),
        MetaItem(id = "4", name = "Gamma", releaseInfo = "2001", imdbRating = "7.5"),
    )

    private fun ids(mode: DiscoverSortMode) = DiscoverSort.apply(items, mode).map { it.id }

    @Test
    fun `addon order is the identity`() {
        assertEquals(listOf("1", "2", "3", "4"), ids(DiscoverSortMode.ADDON_ORDER))
    }

    @Test
    fun `alphabetical ignores case`() {
        assertEquals(listOf("2", "3", "4", "1"), ids(DiscoverSortMode.ALPHABETICAL))
    }

    @Test
    fun `newest keys on the leading year, unknown years last`() {
        assertEquals(listOf("3", "1", "4", "2"), ids(DiscoverSortMode.NEWEST))
    }

    @Test
    fun `top rated descending, unrated last`() {
        assertEquals(listOf("2", "4", "1", "3"), ids(DiscoverSortMode.TOP_RATED))
    }

    /** Latest progress on item [id] at [fraction] of a 20-minute runtime. */
    private fun progressFor(item: MetaItem, fraction: Double) =
        ProgressRepository.metaKey(item.type, item.id) to WatchProgress(
            ref = MediaRef.addon(item.id),
            metaId = item.id,
            metaType = item.type,
            title = item.name,
            poster = null,
            positionMs = (1_200_000 * fraction).toLong(),
            durationMs = 1_200_000,
            updatedAt = 1,
        )

    @Test
    fun `hide watched drops finished titles, keeps in-progress and untouched`() {
        val progress = mapOf(
            progressFor(items[0], 1.0), // finished → hidden
            progressFor(items[1], 0.4), // mid-way → stays
            // items[2]/[3]: no history → stay
        )
        assertEquals(
            listOf("2", "3", "4"),
            DiscoverSort.hideWatched(items, progress).map { it.id },
        )
    }

    @Test
    fun `hide watched with no history is the identity`() {
        assertEquals(items, DiscoverSort.hideWatched(items, emptyMap()))
    }
}
