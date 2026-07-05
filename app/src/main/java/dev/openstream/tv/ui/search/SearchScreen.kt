package dev.openstream.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.TvTextField
import dev.openstream.tv.ui.search.SearchViewModel.RowState
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.CardSizeTokens
import dev.openstream.tv.ui.theme.MutedText

@Composable
fun SearchScreen(
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { fieldFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            TvTextField(
                value = query,
                onValueChange = { query = it },
                onSubmit = { viewModel.search(query) },
                focusRequester = fieldFocus,
                imeAction = ImeAction.Search,
            )
        }

        if (state.searched && state.rows.isEmpty()) {
            RowMessage("None of your addons support search")
        }

        LazyColumn(
            // Rows carry ±focusHeadroom internally (§5.3); small spacing
            // keeps the old 20dp visual rhythm.
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = CardSizeTokens.focusHeadroom,
            ),
        ) {
            items(state.rows, key = { it.ref.key }) { row ->
                SearchRow(row, onItemClick)
            }
        }
    }
}

@Composable
private fun SearchRow(row: RowState, onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit) {
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
            is RowState.Loading -> RowMessage("Searching…")
            is RowState.Failed -> RowMessage("⚠ ${row.ref.addon.manifest.name}: ${row.message}")
            is RowState.Loaded ->
                if (row.items.isEmpty()) {
                    RowMessage("No results")
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                        // Vertical headroom: focus scale grows into this gap
                        // instead of overlaying the row title (§5.3).
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 48.dp, vertical = CardSizeTokens.focusHeadroom,
                        ),
                    ) {
                        items(row.items, key = { it.id }) { item ->
                            PosterCard(item, onClick = { onItemClick(item) })
                        }
                    }
                }
        }
    }
}
