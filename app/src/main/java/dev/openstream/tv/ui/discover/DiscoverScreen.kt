package dev.openstream.tv.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
 * Discover: Stremio-style category tree (§5.1). A filter bar of three pickers
 * — Type → Catalog → Genre — mirrors web.stremio.com's Discover dropdowns;
 * the grid below deep-browses the selection with skip pagination. Pickers are
 * real Dialogs so D-pad focus is trapped and Back dismisses (§5.4).
 */
@Composable
fun DiscoverScreen(
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit = {},
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var openPicker by remember { mutableStateOf<Picker?>(null) }

    // Ask for the next page when the last visible item is near the tail.
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - CardSizeTokens.DEFAULT_COLUMNS * 2
        }
    }
    LaunchedEffect(nearEnd, state.selected?.key, state.selectedGenre) {
        if (nearEnd) viewModel.loadMore()
    }
    // A new filter selection shows a new list — jump back to the top.
    LaunchedEffect(state.selected?.key, state.selectedGenre) {
        gridState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        // Filter bar: the category tree, one picker per level.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.padding(end = 16.dp),
            )
            if (state.types.isNotEmpty()) {
                Button(onClick = { openPicker = Picker.TYPE }) {
                    Text(typeLabel(state.selectedType))
                }
            }
            if (state.catalogs.isNotEmpty()) {
                Button(onClick = { openPicker = Picker.CATALOG }) {
                    Text(
                        text = state.selected?.title ?: "Catalog",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.genres.isNotEmpty()) {
                Button(onClick = { openPicker = Picker.GENRE }) {
                    Text(state.selectedGenre ?: "Genre")
                }
            }
        }

        when {
            state.types.isEmpty() -> RowMessage(
                "Install an addon with catalogs to discover things",
                horizontalPadding = 0.dp,
            )
            state.error != null -> RowMessage("⚠ ${state.error}", horizontalPadding = 0.dp)
            state.items.isEmpty() && state.loading -> RowMessage("Loading…", horizontalPadding = 0.dp)
            state.items.isEmpty() -> RowMessage("Nothing in this catalog", horizontalPadding = 0.dp)
            else -> LazyVerticalGrid(
                state = gridState,
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

    when (openPicker) {
        Picker.TYPE -> PickerDialog(
            title = "Type",
            options = state.types.map {
                PickerOption(typeLabel(it), selected = it == state.selectedType)
            },
            onPick = { viewModel.selectType(state.types[it]) },
            onDismiss = { openPicker = null },
        )
        Picker.CATALOG -> PickerDialog(
            title = "Catalog",
            options = state.catalogs.map {
                PickerOption(
                    label = it.title,
                    sublabel = it.addon.manifest.name,
                    selected = it.key == state.selected?.key,
                )
            },
            onPick = { viewModel.select(state.catalogs[it]) },
            onDismiss = { openPicker = null },
        )
        Picker.GENRE -> {
            // Genre-required catalogs 404 without one — never offer "None".
            val genres: List<String?> =
                (if (state.genreRequired) emptyList() else listOf(null)) + state.genres
            PickerDialog(
                title = "Genre",
                options = genres.map {
                    PickerOption(it ?: "None", selected = it == state.selectedGenre)
                },
                onPick = { viewModel.selectGenre(genres[it]) },
                onDismiss = { openPicker = null },
            )
        }
        null -> Unit
    }
}

private enum class Picker { TYPE, CATALOG, GENRE }

/** "movie" → "Movie", "tv" → "TV"; manifest types are raw strings (§8). */
private fun typeLabel(type: String?): String = when (type) {
    null -> "Type"
    "tv" -> "TV"
    else -> type.replaceFirstChar { it.uppercaseChar() }
}

private data class PickerOption(
    val label: String,
    val sublabel: String? = null,
    val selected: Boolean = false,
)

/**
 * One level of the category tree as a trapped-focus list dialog. Initial
 * focus lands on the current selection so OK-OK is a no-op change; a plain
 * scrollable Column (not lazy) keeps D-pad focus-scrolling dependable with
 * the 60+ catalogs one AIOMetadata instance can declare.
 */
@Composable
private fun PickerDialog(
    title: String,
    options: List<PickerOption>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    // Guard: requestFocus() on a never-attached requester throws.
    LaunchedEffect(Unit) {
        if (options.any { it.selected }) selectedFocus.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .width(420.dp)
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEachIndexed { index, option ->
                    Button(
                        onClick = {
                            onDismiss()
                            onPick(index)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (option.selected) Modifier.focusRequester(selectedFocus)
                                else Modifier
                            ),
                    ) {
                        Column {
                            Text(
                                text = option.label + if (option.selected) "  ✓" else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            option.sublabel?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
