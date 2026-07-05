package dev.openstream.tv.ui.discover

import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.data.DiscoverSortMode

/**
 * Client-side ordering of the items Discover has loaded so far. Sorting is
 * page-local by design: addons own server-side order (§4.1.7), so anything
 * else is an experimental lens over what's on screen — items without the
 * sorted-by field go last, original order preserved within ties.
 */
object DiscoverSort {

    fun apply(items: List<MetaItem>, mode: DiscoverSortMode): List<MetaItem> = when (mode) {
        DiscoverSortMode.ADDON_ORDER -> items
        DiscoverSortMode.ALPHABETICAL -> items.sortedBy { it.name.lowercase() }
        DiscoverSortMode.NEWEST ->
            items.sortedByDescending { yearOf(it) ?: Int.MIN_VALUE }
        DiscoverSortMode.TOP_RATED ->
            items.sortedByDescending { it.imdbRating?.toFloatOrNull() ?: Float.NEGATIVE_INFINITY }
    }

    /** "2019", "2019-2023", "2019-" all key on the leading year. */
    private fun yearOf(item: MetaItem): Int? =
        item.releaseInfo?.take(4)?.toIntOrNull()
}
