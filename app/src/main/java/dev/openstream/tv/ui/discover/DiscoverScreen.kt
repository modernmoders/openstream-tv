package dev.openstream.tv.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.data.DiscoverSortMode
import dev.openstream.tv.data.DiscoverViewPrefs
import dev.openstream.tv.data.ProgressRepository
import androidx.compose.foundation.layout.size
import dev.openstream.tv.ui.components.FilterPill
import dev.openstream.tv.ui.components.GearIcon
import dev.openstream.tv.ui.components.LoadingMessage
import dev.openstream.tv.ui.components.OptionRow
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.SkeletonPosterGrid
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.components.rememberRowEntryMemory
import dev.openstream.tv.ui.components.rowEntry
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.AmbientSection
import dev.openstream.tv.ui.theme.ambientBackground
import dev.openstream.tv.ui.theme.CardSizeTokens

/**
 * Discover: Stremio-style category tree (§5.1). A filter bar of three pickers
 * — Type → Catalog → Genre — mirrors web.stremio.com's Discover dropdowns;
 * the grid below deep-browses the selection with skip pagination. Pickers are
 * real Dialogs so D-pad focus is trapped and Back dismisses (§5.4).
 */
@Composable
fun DiscoverScreen(
    onBack: () -> Unit = {},
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit = {},
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var openPicker by remember { mutableStateOf<Picker?>(null) }

    // Back sits first in the filter bar, so anchor entry focus on the Type
    // picker — OK-on-entry must browse, not bounce back home (BackButton KDoc).
    val typeFocus = remember { FocusRequester() }
    LaunchedEffect(state.types.isNotEmpty()) {
        if (state.types.isNotEmpty()) runCatching { typeFocus.requestFocus() }
    }

    // Grid-entry memory in the shared index-based language (DECISIONS #56):
    // DOWN from the filter bar into a freshly-shown grid lands on the FIRST
    // poster; returning to the SAME grid (e.g. dismissing a picker without
    // changing the selection) restores the card you left. Keyed on the
    // filter so a brand-new list can't inherit the previous filter's memory
    // (owner bug: DOWN landed mid-row instead of the first poster).
    val gridMemory = rememberRowEntryMemory(state.selected?.key, state.selectedGenre)

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
            .ambientBackground(AmbientSection.DISCOVER)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        // Page header: Back + title sit above the filter bar, plain (no
        // surface chrome) so the bar below reads as its own distinct band.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }

        // Filter bar: the category tree, one picker per level. Given its own
        // subtly-elevated surface + soft accent backglow (owner round 10:
        // "give the filter bar itself more presence... no gaudy neon") so it
        // reads as a distinct control strip instead of floating text pills
        // directly on the page background. The glow is a larger, softer-cornered
        // rect painted first so it "bleeds" a few dp past the card's own edge
        // — two backgrounds at IDENTICAL bounds would just paint over each
        // other and hide the glow entirely.
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                // Round 14 #12: each picker is a FilterPill — dimension label +
                // current value + drawn ▾ — so it reads as an openable filter,
                // not static text.
                if (state.types.isNotEmpty()) {
                    FilterPill(
                        dimension = "Type",
                        value = typeLabel(state.selectedType),
                        onClick = { openPicker = Picker.TYPE },
                        modifier = Modifier.focusRequester(typeFocus),
                    )
                }
                if (state.catalogs.isNotEmpty()) {
                    FilterPill(
                        dimension = "Catalog",
                        value = state.selected?.title ?: "Choose",
                        onClick = { openPicker = Picker.CATALOG },
                        modifier = Modifier.widthIn(max = 340.dp),
                    )
                }
                if (state.genres.isNotEmpty()) {
                    FilterPill(
                        dimension = "Genre",
                        value = state.selectedGenre ?: "All",
                        onClick = { openPicker = Picker.GENRE },
                    )
                }
                if (state.types.isNotEmpty()) {
                    // "Hide watched" (watched system): a toggle in the filter
                    // bar, not a picker — selected = ON in the shared pill
                    // language, so its state is readable at a glance.
                    SurfacePill(
                        label = "Hide watched",
                        selected = state.view.hideWatched,
                        onClick = { viewModel.setHideWatched(!state.view.hideWatched) },
                    )
                }
                if (state.types.isNotEmpty()) {
                    // View is display settings, not a tree level — pushed to the
                    // far edge so it reads apart from the pickers (owner 2026-07-05).
                    // Cog AFTER the word, drawn not font (round 14 #10).
                    Spacer(Modifier.weight(1f))
                    SurfacePill(onClick = { openPicker = Picker.VIEW }) {
                        Text(
                            text = "View",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                        )
                        GearIcon(tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Sort/filter once per input change — inside the grid lambda it
        // re-sorted the full list on every recomposition, which is exactly
        // when frames are scarce (pages streaming in while the user moves).
        val shown = remember(
            state.items, state.view.sort, state.view.hideWatched, state.progressByMeta,
        ) {
            DiscoverSort.apply(state.items, state.view.sort).let {
                if (state.view.hideWatched) DiscoverSort.hideWatched(it, state.progressByMeta)
                else it
            }
        }
        when {
            state.types.isEmpty() -> RowMessage(
                "Install an addon with catalogs to discover things",
                horizontalPadding = 0.dp,
            )
            state.error != null -> RowMessage("⚠ ${state.error}", horizontalPadding = 0.dp)
            // Tile silhouettes instead of a centered spinner line: the page
            // paints its final structure immediately and posters fade in over
            // it — the Netflix loading pattern (#9).
            state.items.isEmpty() && state.loading -> SkeletonPosterGrid(columns = state.view.columns)
            state.items.isEmpty() -> RowMessage("Nothing in this catalog", horizontalPadding = 0.dp)
            // The filter emptied a non-empty page: say why the grid is blank
            // instead of showing the misleading "nothing in this catalog".
            shown.isEmpty() -> RowMessage(
                "Everything here is watched — turn off \"Hide watched\" to see it",
                horizontalPadding = 0.dp,
            )
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(CardSizeTokens.posterWidth(state.view.columns)),
                horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                verticalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                // Scroll-axis headroom for the first/last row's focus scale (§5.3).
                contentPadding = PaddingValues(vertical = CardSizeTokens.focusHeadroom),
                modifier = Modifier.rowEntry(gridMemory),
            ) {
                val entryIndex = gridMemory.entryIndex(shown.size)
                itemsIndexed(
                    items = shown,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> "poster" },
                ) { index, item ->
                    PosterCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) gridMemory.lastFocusedIndex = index }
                            .then(
                                if (index == entryIndex) {
                                    Modifier.focusRequester(gridMemory.entryFocus)
                                } else Modifier
                            ),
                        columns = state.view.columns,
                        progress = state.progressByMeta[ProgressRepository.metaKey(item.type, item.id)],
                    )
                }
                if (state.loading) {
                    item(contentType = "loading") {
                        LoadingMessage(horizontalPadding = 0.dp)
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
        Picker.VIEW -> ViewOptionsDialog(
            view = state.view,
            onColumns = viewModel::setColumns,
            onSort = viewModel::setSort,
            onDismiss = { openPicker = null },
        )
        null -> Unit
    }
}

private enum class Picker { TYPE, CATALOG, GENRE, VIEW }

/**
 * The filter bar's own quiet elevation (owner round 10: "give the filter bar
 * itself more presence... no gaudy neon"). A hairline-lit card slightly
 * lighter than the page background, with a soft accent tint UNDER it acting
 * as a backglow — subtle enough to read as depth, not a light show. Static
 * colors, not an animation, so it costs nothing on the 32-bit boxes.
 */
private val FilterBarSurface = Color(0xFF1B1B26)
private val FilterBarGlow = Accent.copy(alpha = 0.10f)

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
 * View options for this screen, edited in place (owner request 2026-07-05:
 * "customizable on the same screen it's used on"). Picks apply live behind
 * the dialog; Back closes it. Sort is a client-side lens over loaded items —
 * addon order stays the protocol truth (§4.1.7).
 */
@Composable
private fun ViewOptionsDialog(
    view: DiscoverViewPrefs,
    onColumns: (Int) -> Unit,
    onSort: (DiscoverSortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    @Composable
    fun sectionLabel(text: String) = Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
    )

    @Composable
    fun option(label: String, selected: Boolean, first: Boolean = false, onPick: () -> Unit) {
        OptionRow(
            label = label,
            selected = selected,
            onClick = onPick,
            modifier = if (first) Modifier.focusRequester(firstFocus) else Modifier,
        )
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
                text = "View",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                sectionLabel("Density")
                option("Comfortable · 6 columns", view.columns == 6, first = true) { onColumns(6) }
                option("Compact · 8 columns", view.columns == 8) { onColumns(8) }

                sectionLabel("Sort loaded items")
                option("Addon order", view.sort == DiscoverSortMode.ADDON_ORDER) {
                    onSort(DiscoverSortMode.ADDON_ORDER)
                }
                option("A–Z", view.sort == DiscoverSortMode.ALPHABETICAL) {
                    onSort(DiscoverSortMode.ALPHABETICAL)
                }
                option("Newest first", view.sort == DiscoverSortMode.NEWEST) {
                    onSort(DiscoverSortMode.NEWEST)
                }
                option("Top rated", view.sort == DiscoverSortMode.TOP_RATED) {
                    onSort(DiscoverSortMode.TOP_RATED)
                }
            }
        }
    }
}

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
                    .verticalScroll(rememberScrollState())
                    // After verticalScroll = inside the clip: scroll-axis
                    // headroom for the first/last option's focus scale (§5.3).
                    .padding(vertical = 8.dp),
            ) {
                options.forEachIndexed { index, option ->
                    OptionRow(
                        label = option.label,
                        selected = option.selected,
                        sublabel = option.sublabel,
                        onClick = {
                            onDismiss()
                            onPick(index)
                        },
                        modifier = if (option.selected) Modifier.focusRequester(selectedFocus) else Modifier,
                    )
                }
            }
        }
    }
}
