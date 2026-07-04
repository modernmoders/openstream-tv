package dev.openstream.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.home.HomeViewModel.RowState
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.CardSizeTokens
import dev.openstream.tv.ui.theme.MutedText

/**
 * Home: catalog rows from all enabled addons, rendered incrementally as each
 * addon answers. Continue Watching will become the always-first row in
 * Phase 2 (§5.6) — progress tracking doesn't exist yet.
 */
@Composable
fun HomeScreen(
    onDiscover: () -> Unit,
    onSearch: () -> Unit,
    onManageAddons: () -> Unit,
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            // Vertical overscan only: rows manage their own horizontal
            // padding so posters can scroll edge-to-edge (§5.3).
            .padding(vertical = 27.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "OpenStream TV",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDiscover) { Text("Discover") }
                Button(onClick = onSearch) { Text("Search") }
                Button(onClick = onManageAddons) { Text("Addons") }
            }
        }

        when {
            state.initializing -> Unit // Room read takes ~ms; avoid a flash

            !state.hasAddons -> EmptyHome()

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                items(state.rows, key = { it.ref.key }) { row ->
                    CatalogRow(row, onItemClick)
                }
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No catalogs yet — install an addon to get started",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText,
        )
    }
}

@Composable
private fun CatalogRow(row: RowState, onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${row.ref.title} · ${row.ref.catalog.type}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = row.ref.addon.manifest.name,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
        }

        when (row) {
            is RowState.Loading -> RowMessage("Loading…")
            // §4.1.8: a failed addon is a visible chip, never a silent gap
            is RowState.Failed -> RowMessage("⚠ ${row.ref.addon.manifest.name} failed: ${row.message}")
            is RowState.Loaded ->
                if (row.items.isEmpty()) {
                    RowMessage("Nothing in this catalog")
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp),
                    ) {
                        items(row.items, key = { it.id }) { item ->
                            PosterCard(item, onClick = { onItemClick(item) })
                        }
                    }
                }
        }
    }
}
