package dev.openstream.tv.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home's back-from-Details focus restore (owner round 13 #4) hinges on mapping
 * a catalog row's key to its LazyColumn item index. The hero and Continue
 * Watching rows are conditional, so the offset shifts under it — the case that
 * would silently scroll to the wrong row.
 */
class HomeRestoreIndexTest {

    private val rows = listOf("addonA:top", "addonA:new", "addonB:popular")

    @Test
    fun `catalog row index accounts for hero and continue watching`() {
        assertEquals(
            3,
            homeRestoreIndex(rows, hasFeatured = true, hasContinueWatching = true, targetRowKey = "addonA:top"),
        )
        assertEquals(
            5,
            homeRestoreIndex(rows, hasFeatured = true, hasContinueWatching = true, targetRowKey = "addonB:popular"),
        )
    }

    @Test
    fun `catalog row index shifts up when the optional rows are absent`() {
        assertEquals(
            1,
            homeRestoreIndex(rows, hasFeatured = false, hasContinueWatching = false, targetRowKey = "addonA:top"),
        )
        assertEquals(
            2,
            homeRestoreIndex(rows, hasFeatured = true, hasContinueWatching = false, targetRowKey = "addonA:top"),
        )
        assertEquals(
            2,
            homeRestoreIndex(rows, hasFeatured = false, hasContinueWatching = true, targetRowKey = "addonA:top"),
        )
    }

    @Test
    fun `hero and continue watching resolve to their own items`() {
        assertEquals(
            1,
            homeRestoreIndex(rows, hasFeatured = true, hasContinueWatching = true, targetRowKey = HOME_FEATURED_KEY),
        )
        assertEquals(
            2,
            homeRestoreIndex(
                rows,
                hasFeatured = true,
                hasContinueWatching = true,
                targetRowKey = HOME_CONTINUE_WATCHING_KEY,
            ),
        )
        // No hero: Continue Watching takes the slot right under the header.
        assertEquals(
            1,
            homeRestoreIndex(
                rows,
                hasFeatured = false,
                hasContinueWatching = true,
                targetRowKey = HOME_CONTINUE_WATCHING_KEY,
            ),
        )
    }

    @Test
    fun `every catalog row sits below continue watching`() {
        // Round 20 #8: Continue Watching is above ALL catalog rows — even
        // rows[0] (the recommendations row the ViewModel sorts to the front)
        // lands after it.
        assertEquals(
            2,
            homeRestoreIndex(rows, hasFeatured = true, hasContinueWatching = true, targetRowKey = HOME_CONTINUE_WATCHING_KEY),
        )
        assertEquals(
            3,
            homeRestoreIndex(rows, hasFeatured = true, hasContinueWatching = true, targetRowKey = "addonA:top"),
        )
        assertEquals(
            4,
            homeRestoreIndex(rows, hasFeatured = true, hasContinueWatching = true, targetRowKey = "addonA:new"),
        )
    }

    @Test
    fun `a row that is no longer on screen restores nothing`() {
        // The addon was disabled, the row hidden, or the hero/CW row emptied
        // out while we were away — fall back to the header rather than focus a
        // row the user never opened.
        assertEquals(
            -1,
            homeRestoreIndex(rows, hasFeatured = true, hasContinueWatching = true, targetRowKey = "addonC:gone"),
        )
        assertEquals(
            -1,
            homeRestoreIndex(rows, hasFeatured = false, hasContinueWatching = true, targetRowKey = HOME_FEATURED_KEY),
        )
        assertEquals(
            -1,
            homeRestoreIndex(
                rows,
                hasFeatured = true,
                hasContinueWatching = false,
                targetRowKey = HOME_CONTINUE_WATCHING_KEY,
            ),
        )
        assertEquals(
            -1,
            homeRestoreIndex(emptyList(), hasFeatured = false, hasContinueWatching = false, targetRowKey = "addonA:top"),
        )
    }
}
