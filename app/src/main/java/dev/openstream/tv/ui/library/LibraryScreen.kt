package dev.openstream.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.components.rememberRowEntryMemory
import dev.openstream.tv.ui.components.rowEntry
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.AmbientSection
import dev.openstream.tv.ui.theme.CardSizeTokens
import dev.openstream.tv.ui.theme.ambientBackground

/**
 * Library (owner Round-22 #2): everything this TV has watched, as a poster
 * grid with Stremio's Library filter bar — the exact single-select the owner
 * screenshotted (All / Last Watched / A–Z / Z–A / Most Watched / Watched /
 * Not Watched) plus a type lens, all as always-visible pills (no dropdown:
 * one click per choice, elder-friendly §10).
 *
 * Everything here is local watch history; a tile opens Details just like a
 * Home/Discover tile (the metaId/metaType stored with every progress row is
 * exactly a Details address).
 */
@Composable
fun LibraryScreen(
    onItemClick: (MetaItem) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Entry focus lands on the current filter pill — same "OK-on-entry
    // browses" rule as Discover's Type picker.
    val filterFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { filterFocus.requestFocus() } }

    // Shared row-entry memory (DECISIONS #56), keyed on the selection so a
    // fresh list starts at the first poster and an unchanged one restores
    // the card you left.
    val gridMemory = rememberRowEntryMemory(state.filter, state.type)
    LaunchedEffect(state.filter, state.type) { gridState.scrollToItem(0) }

    val shown = remember(state.entries, state.filter, state.type, state.metaNames) { state.shown }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ambientBackground(AmbientSection.LIBRARY)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        // Header row carries the type lens on the far right — the filter bar
        // below stays exactly Stremio's 7 choices (all 10 pills in one band
        // overflowed the screen on the emulator).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            LibraryType.entries.forEach { type ->
                SurfacePill(
                    label = type.label,
                    selected = state.type == type,
                    onClick = { viewModel.setType(type) },
                )
            }
        }

        // Filter bar on the same quiet elevated surface as Discover's.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(FilterBarGlow, RoundedCornerShape(26.dp))
                .padding(4.dp)
                .background(FilterBarSurface, RoundedCornerShape(20.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // Scrolls rather than shrinks: 7 pills at natural width
                    // overflow a 960dp TV — D-pad focus pulls the clipped
                    // tail pill into view (Compose bring-into-view).
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                LibraryFilter.entries.forEachIndexed { index, filter ->
                    SurfacePill(
                        label = filter.label,
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        modifier = if (index == 0) Modifier.focusRequester(filterFocus) else Modifier,
                    )
                }
            }
        }

        when {
            // First Room read is milliseconds — no skeleton needed; rendering
            // nothing beats flashing the empty-state text at every entry.
            state.loading -> Unit
            state.entries.isEmpty() -> RowMessage(
                "Everything you watch shows up here — play something and come back",
                horizontalPadding = 0.dp,
            )
            shown.isEmpty() -> RowMessage(
                emptyFilterMessage(state.filter, state.type),
                horizontalPadding = 0.dp,
            )
            else -> LazyVerticalGrid(
                state = gridState,
                // FixedSize, not Adaptive — same gray-strip lesson as Discover
                // (round 22 #3): cells must be exactly one poster wide.
                columns = GridCells.FixedSize(CardSizeTokens.posterWidth(state.columns)),
                horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                verticalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                contentPadding = PaddingValues(vertical = CardSizeTokens.focusHeadroom),
                modifier = Modifier.rowEntry(gridMemory),
            ) {
                val entryIndex = gridMemory.entryIndex(shown.size)
                itemsIndexed(
                    items = shown,
                    key = { _, it -> it.metaKey },
                    contentType = { _, _ -> "poster" },
                ) { index, entry ->
                    PosterCard(
                        item = MetaItem(
                            id = entry.metaId,
                            type = entry.metaType,
                            name = entry.title,
                            poster = entry.poster,
                        ),
                        onClick = {
                            onItemClick(
                                MetaItem(
                                    id = entry.metaId,
                                    type = entry.metaType,
                                    name = entry.title,
                                    poster = entry.poster,
                                )
                            )
                        },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) gridMemory.lastFocusedIndex = index }
                            .then(
                                if (index == entryIndex) {
                                    Modifier.focusRequester(gridMemory.entryFocus)
                                } else Modifier
                            ),
                        columns = state.columns,
                        progress = state.progressByMeta[entry.metaKey],
                        seriesWatch = state.seriesWatchByMeta[entry.metaKey],
                    )
                }
            }
        }
    }
}

/** Why is the grid blank? Say so — never show a misleading generic empty. */
private fun emptyFilterMessage(filter: LibraryFilter, type: LibraryType): String = when {
    filter == LibraryFilter.WATCHED -> "Nothing finished yet — watched titles land here"
    filter == LibraryFilter.NOT_WATCHED -> "Everything here is finished — nice work"
    type == LibraryType.MOVIES -> "No movies watched yet"
    type == LibraryType.SERIES -> "No series watched yet"
    else -> "Nothing to show for this filter"
}

/** Same quiet elevation values as Discover's filter bar (round 10). */
private val FilterBarSurface = Color(0xFF1B1B26)
private val FilterBarGlow = Accent.copy(alpha = 0.10f)
