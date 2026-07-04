package dev.openstream.tv.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.CardSizeTokens
import dev.openstream.tv.ui.theme.MutedText

/**
 * Discover: curated-catalog browser. Left rail lists every browsable catalog
 * (each of the owner's curated/merged lists is one entry); the grid deep-
 * browses the selected one with skip pagination (§5.1: 6-col default).
 */
@Composable
fun DiscoverScreen(
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit = {},
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Ask for the next page when the last visible item is near the tail.
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - CardSizeTokens.DEFAULT_COLUMNS * 2
        }
    }
    LaunchedEffect(nearEnd, state.selected?.key) {
        if (nearEnd) viewModel.loadMore()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Left rail: the curated catalog list.
        Column(modifier = Modifier.width(280.dp)) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.catalogs, key = { it.key }) { ref ->
                    val isSelected = ref.key == state.selected?.key
                    Button(onClick = { viewModel.select(ref) }) {
                        Column {
                            Text(
                                text = ref.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${ref.catalog.type} · ${ref.addon.manifest.name}" +
                                    if (isSelected) "  ✓" else "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Grid: deep browse of the selected catalog.
        Column(modifier = Modifier.fillMaxWidth()) {
            state.selected?.let { sel ->
                Text(
                    text = "${sel.title} · ${sel.catalog.type}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            when {
                state.error != null -> RowMessage("⚠ ${state.error}", horizontalPadding = 0.dp)
                state.items.isEmpty() && state.loading -> RowMessage("Loading…", horizontalPadding = 0.dp)
                state.items.isEmpty() -> RowMessage("Nothing in this catalog", horizontalPadding = 0.dp)
                else -> LazyVerticalGrid(
                    state = gridState,
                    // 4 columns in the grid area (the rail takes ~2 columns
                    // of width); still driven by the same size tokens.
                    columns = GridCells.Adaptive(CardSizeTokens.posterWidth()),
                    horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                    verticalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                ) {
                    itemsIndexed(state.items, key = { _, it -> it.id }) { _, item ->
                        PosterCard(item, onClick = { onItemClick(item) })
                    }
                    if (state.loading) {
                        item {
                            Text("Loading…", color = MutedText)
                        }
                    }
                }
            }
        }
    }
}
