package dev.openstream.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Row/grid entry focus memory (§10 rule: D-pad entry into a fresh container
 * lands on the FIRST card; returning restores the card you left) — tracked
 * by INDEX, never by node.
 *
 * Why not `focusRestorer`: it saves the focused child NODE, but lazy layouts
 * recycle nodes across items as their parent scrolls, so the saved node can
 * silently be showing a DIFFERENT card by the time focus comes back —
 * re-entry landed mid-row and the bring-into-view drag shifted the whole row
 * sideways (owner round 14 #7, emulator-proven on Home; every browse surface
 * now shares this fix so focus behaves identically app-wide).
 *
 * Usage: `val memory = rememberRowEntryMemory()`, put
 * `Modifier.rowEntry(memory)` on the LazyRow/LazyVerticalGrid, and on each
 * card attach `memory.entryFocus` when `index == memory.entryIndex(count)`
 * plus `.onFocusChanged { if (it.isFocused) memory.lastFocusedIndex = index }`.
 */
class RowEntryMemory internal constructor(initialIndex: Int) {
    var lastFocusedIndex by mutableStateOf(initialIndex)
    val entryFocus = FocusRequester()

    /** The index the entry requester should sit on, clamped to the list. */
    fun entryIndex(itemCount: Int): Int =
        lastFocusedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
}

/** Only the index survives; a fresh FocusRequester re-attaches on restore. */
private val RowEntryMemorySaver = Saver<RowEntryMemory, Int>(
    save = { it.lastFocusedIndex },
    restore = { RowEntryMemory(it) },
)

/**
 * Saveable on purpose: the memory must outlive the container scrolling out
 * of composition — that's exactly when it's needed. Pass [inputs] to reset
 * the memory when the container's CONTENT identity changes (e.g. a new
 * Discover filter shows a brand-new list).
 */
@Composable
fun rememberRowEntryMemory(vararg inputs: Any?): RowEntryMemory =
    rememberSaveable(*inputs, saver = RowEntryMemorySaver) { RowEntryMemory(0) }

/** Redirects focus entry into the container to the remembered card. */
fun Modifier.rowEntry(memory: RowEntryMemory): Modifier = focusProperties {
    onEnter = {
        // Not composed (items changed under the memory) → swallow and let
        // the default geometric entry stand.
        runCatching { memory.entryFocus.requestFocus() }
    }
}
