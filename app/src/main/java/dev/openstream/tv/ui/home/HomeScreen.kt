package dev.openstream.tv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.openstream.tv.BuildConfig
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.WatchProgress
import androidx.compose.foundation.layout.size
import dev.openstream.tv.ui.components.ContinueWatchingCard
import dev.openstream.tv.ui.components.GearIcon
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.LoadingMessage
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.home.HomeViewModel.RowState
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.AmbientSection
import dev.openstream.tv.ui.theme.ambientBackground
import dev.openstream.tv.ui.theme.CardSizeTokens
import dev.openstream.tv.ui.theme.Hairline
import dev.openstream.tv.ui.theme.MutedText
import dev.openstream.tv.ui.theme.SurfaceCard

/** LazyColumn item keys for the two rows that aren't addon catalogs. */
internal const val HOME_FEATURED_KEY = "featured"
internal const val HOME_CONTINUE_WATCHING_KEY = "continue-watching"

/** Frames to wait for a restored row to compose before giving up on its focus. */
private const val RESTORE_FOCUS_FRAMES = 12

/**
 * Which LazyColumn item holds [targetRowKey], or -1 when it isn't on screen.
 *
 * The column is header, then an optional hero, then the pinned recommendation
 * rows (round 14 #14 — the first [pinnedRowCount] entries of the row list),
 * then an optional Continue Watching row, then the remaining catalog rows —
 * so a catalog's index depends on which of the optional rows exist and which
 * side of Continue Watching it sits on. Pure so the arithmetic is unit-testable.
 */
internal fun homeRestoreIndex(
    rowKeys: List<String>,
    hasFeatured: Boolean,
    hasContinueWatching: Boolean,
    targetRowKey: String,
    pinnedRowCount: Int = 0,
): Int {
    val featuredIndex = 1 // item 0 is always the header
    if (targetRowKey == HOME_FEATURED_KEY) return if (hasFeatured) featuredIndex else -1
    val firstPinnedIndex = if (hasFeatured) featuredIndex + 1 else featuredIndex
    val continueWatchingIndex = firstPinnedIndex + pinnedRowCount
    if (targetRowKey == HOME_CONTINUE_WATCHING_KEY) {
        return if (hasContinueWatching) continueWatchingIndex else -1
    }
    val offset = rowKeys.indexOf(targetRowKey)
    if (offset < 0) return -1
    if (offset < pinnedRowCount) return firstPinnedIndex + offset
    val firstUnpinnedIndex =
        if (hasContinueWatching) continueWatchingIndex + 1 else continueWatchingIndex
    return firstUnpinnedIndex + (offset - pinnedRowCount)
}

/**
 * Home: catalog rows from all enabled addons, rendered incrementally as each
 * addon answers. Continue Watching will become the always-first row in
 * Phase 2 (§5.6) — progress tracking doesn't exist yet.
 */
@Composable
fun HomeScreen(
    onDiscover: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Predictable entry point (§10): land on the header so Home opens at the
    // very top — the featured hero stays in view instead of the list auto-
    // scrolling to a focusable poster deeper down.
    val headerFocus = remember { FocusRequester() }
    val showingRows = !state.initializing && state.hasAddons
    // Only the branches that have nothing else to focus. The rows branch owns
    // its own entry focus below, because focusing the header there would drag
    // a restored scroll position back to the top (header == list item 0).
    LaunchedEffect(showingRows) { if (!showingRows) runCatching { headerFocus.requestFocus() } }

    // Back from Details must land on the tile you opened, not the top of Home
    // (owner round 13 #4). Navigating away disposes this screen, so the focused
    // node — and every focusRestorer's memory with it — is gone by the time we
    // return. Remember WHICH tile was opened instead; rememberSaveable outlives
    // the back stack. It is never cleared: the last tile you opened stays the
    // anchor for every later return to Home.
    var openedRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val openItem: (String, MetaItem) -> Unit = { rowKey, item ->
        openedRowKey = rowKey
        openedItemId = item.id
        onItemClick(item)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ambientBackground(AmbientSection.HOME)
            // Vertical overscan only: rows manage their own horizontal
            // padding so posters can scroll edge-to-edge (§5.3).
            .padding(vertical = 27.dp),
    ) {
        when {
            state.initializing -> HomeHeader(onDiscover, onSearch, onSettings, headerFocus)

            !state.hasAddons -> {
                HomeHeader(onDiscover, onSearch, onSettings, headerFocus)
                EmptyHome()
            }

            else -> {
                // Featured = the first item of the first catalog that answered
                // with content. A quiet spotlight, not a rotating carousel
                // (refined, not dramatic — owner 2026-07-06, DECISIONS #30).
                val featured = state.rows
                    .filterIsInstance<RowState.Loaded>()
                    .firstOrNull { it.items.isNotEmpty() }
                    ?.items?.firstOrNull()

                // A LazyColumn anchors its scroll to the first item, so when the
                // hero is inserted just under the header (rows load after the
                // first frame) it can land above the viewport. Snap to the top
                // once, the moment the hero first appears — after that the
                // user's own scrolling is left alone. The saveable latch matters
                // on the way BACK from Details: the hero is already there, so an
                // unguarded effect would re-snap Home to the top (round 13 #4).
                val listState = rememberLazyListState()
                var didSnapToHero by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(featured != null) {
                    if (featured != null && !didSnapToHero) {
                        listState.scrollToItem(0)
                        didSnapToHero = true
                    }
                }

                // Entry focus: the tile we left, else the header. Both the column
                // and the target row scroll lazily, so the row's FocusRequester
                // only attaches a frame or two after the column reaches it —
                // probe until it takes, then fall back to the header.
                val restoreFocus = remember { FocusRequester() }
                val rowKeys = state.rows.map { it.ref.key }
                LaunchedEffect(Unit) {
                    val index = openedRowKey?.let {
                        homeRestoreIndex(
                            rowKeys = rowKeys,
                            hasFeatured = featured != null,
                            hasContinueWatching = state.continueWatching.isNotEmpty(),
                            targetRowKey = it,
                            pinnedRowCount = state.pinnedRowCount,
                        )
                    } ?: -1
                    if (index >= 0) {
                        listState.scrollToItem(index)
                        repeat(RESTORE_FOCUS_FRAMES) {
                            withFrameNanos {}
                            if (runCatching { restoreFocus.requestFocus() }.isSuccess) {
                                return@LaunchedEffect
                            }
                        }
                    }
                    runCatching { headerFocus.requestFocus() }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Rows carry ±focusHeadroom internally, so the small spacing
                    // here keeps the visual rhythm of the old 20dp gap (§5.3).
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                ) {
                    // The header is a LIST ITEM, not pinned above the list: when
                    // it was outside, holding UP could land focus on the pinned
                    // pills while the list's bring-into-view scroll was still
                    // mid-flight — the cancelled animation left Home stuck
                    // half-scrolled (owner's hold-UP glitch, 2026-07-06). As
                    // item 0, reaching the header forces the list to finish
                    // scrolling to the very top.
                    item(key = "header") {
                        HomeHeader(onDiscover, onSearch, onSettings, headerFocus)
                    }
                    if (featured != null) {
                        item(key = HOME_FEATURED_KEY) {
                            FeaturedHero(
                                item = featured,
                                onItemClick = { openItem(HOME_FEATURED_KEY, it) },
                                modifier = if (openedRowKey == HOME_FEATURED_KEY) {
                                    Modifier.focusRequester(restoreFocus)
                                } else Modifier,
                            )
                        }
                    }
                    // Round 14 #14: the pinned recommendation rows render ABOVE
                    // Continue Watching, everything else below — the ViewModel
                    // sorted them to the front, this just splits the list.
                    val pinnedRows = state.rows.take(state.pinnedRowCount)
                    val unpinnedRows = state.rows.drop(state.pinnedRowCount)
                    items(pinnedRows, key = { it.ref.key }) { row ->
                        CatalogRow(
                            row = row,
                            columns = state.columns,
                            progressByMeta = state.progressByMeta,
                            onItemClick = { openItem(row.ref.key, it) },
                            restoreItemId = openedItemId.takeIf { openedRowKey == row.ref.key },
                            restoreFocus = restoreFocus,
                        )
                    }
                    if (state.continueWatching.isNotEmpty()) {
                        item(key = HOME_CONTINUE_WATCHING_KEY) {
                            ContinueWatchingRow(
                                entries = state.continueWatching,
                                onItemClick = { openItem(HOME_CONTINUE_WATCHING_KEY, it) },
                                restoreItemId = openedItemId
                                    .takeIf { openedRowKey == HOME_CONTINUE_WATCHING_KEY },
                                restoreFocus = restoreFocus,
                            )
                        }
                    }
                    items(unpinnedRows, key = { it.ref.key }) { row ->
                        CatalogRow(
                            row = row,
                            columns = state.columns,
                            progressByMeta = state.progressByMeta,
                            onItemClick = { openItem(row.ref.key, it) },
                            restoreItemId = openedItemId.takeIf { openedRowKey == row.ref.key },
                            restoreFocus = restoreFocus,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    onDiscover: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    headerFocus: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // The family-facing brand ("SavoyStreams" on the owner's
            // boxes); addon management now lives in Settings behind
            // Expert mode — the home header stays friendly-only
            // (owner directive 2026-07-06).
            text = BuildConfig.SETUP_BRAND,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurfacePill("Discover", onDiscover, Modifier.focusRequester(headerFocus))
            SurfacePill("Search", onSearch)
            // Round 14 #11: Settings reads apart from the browse pills — extra
            // gap + the shared gear glyph instead of a third look-alike text
            // pill. headerFocus stays on Discover (the #33 hold-UP anchor).
            Spacer(Modifier.width(26.dp))
            SurfacePill(onClick = onSettings) {
                GearIcon(tint = MutedText, modifier = Modifier.size(18.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MutedText,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A quiet featured spotlight at the top of Home (refined marquee, DECISIONS
 * #30): the item's backdrop (when the addon provides one) under a left-to-
 * right + bottom scrim, a poster, a big title, and a one-line meta. Looks
 * right even when catalog items carry no backdrop — it falls back to the
 * surface tint. Focusable Surface: accent border on focus, no jarring scale.
 */
@Composable
private fun FeaturedHero(
    item: MetaItem,
    onItemClick: (MetaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = { onItemClick(item) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .height(300.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceCard,
            focusedContainerColor = SurfaceCard,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.background != null) {
                AsyncImage(
                    model = item.background,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Scrim: keep the left (text) side legible, fade the art in.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to SurfaceCard,
                                0.55f to SurfaceCard.copy(alpha = 0.65f),
                                1f to Color.Transparent,
                            )
                        )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(244.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        item.type.replaceFirstChar { it.uppercase() }.ifBlank { null },
                        item.releaseInfo,
                        item.imdbRating?.let { "★ $it" },
                    ).joinToString("  ·  ")
                    if (meta.isNotBlank()) {
                        Text(text = meta, style = MaterialTheme.typography.titleSmall, color = MutedText)
                    }
                    if (!item.description.isNullOrBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Press OK to watch",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Nothing here yet — open Settings and pick \"Connect this TV\" to get set up",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText,
        )
    }
}

/**
 * Always-first row when non-empty (§5.6). A click navigates to the item's
 * DETAILS page — from there the resume dialog (stream list) takes over.
 */
// focusRestorer — the §10 row-entry rule, same as Search: DOWN into a fresh row
// lands on the FIRST card, but returning to a row you were just in restores the
// card you left.
@Composable
private fun ContinueWatchingRow(
    entries: List<WatchProgress>,
    onItemClick: (MetaItem) -> Unit,
    restoreItemId: String?,
    restoreFocus: FocusRequester,
) {
    Column {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        val firstCardFocus = remember { FocusRequester() }
        val rowState = rememberLazyListState()
        val restoreIndex = restoreItemId?.let { id -> entries.indexOfFirst { it.metaId == id } } ?: -1
        // Scroll the restored card into existence: a LazyRow hasn't composed
        // off-screen cards, so its FocusRequester wouldn't be attached yet.
        LaunchedEffect(restoreIndex) { if (restoreIndex > 0) rowState.scrollToItem(restoreIndex) }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
            // Vertical headroom: a focused card scales into this gap instead
            // of overlaying the row title / getting clipped (§5.3).
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 48.dp, vertical = CardSizeTokens.focusHeadroom,
            ),
            modifier = Modifier.focusRestorer(firstCardFocus),
        ) {
            itemsIndexed(entries, key = { _, it -> "${it.ref.sourceKind}:${it.ref.externalId}" }) { index, p ->
                ContinueWatchingCard(
                    progress = p,
                    onClick = {
                        // Minimal MetaItem: details re-resolves the full meta by id.
                        onItemClick(
                            MetaItem(id = p.metaId, type = p.metaType, name = p.title, poster = p.poster)
                        )
                    },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier)
                        .then(if (index == restoreIndex) Modifier.focusRequester(restoreFocus) else Modifier),
                )
            }
        }
    }
}

// focusRestorer — see ContinueWatchingRow above.
@Composable
private fun CatalogRow(
    row: RowState,
    columns: Int,
    progressByMeta: Map<String, dev.openstream.tv.domain.WatchProgress>,
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit,
    restoreItemId: String?,
    restoreFocus: FocusRequester,
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${row.title} · ${row.ref.catalog.type}",
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
            is RowState.Loading -> LoadingMessage()
            // §4.1.8: a failed addon is a visible chip, never a silent gap
            is RowState.Failed -> RowMessage("⚠ ${row.ref.addon.manifest.name} failed: ${row.message}")
            is RowState.Loaded ->
                if (row.items.isEmpty()) {
                    RowMessage("Nothing in this catalog")
                } else {
                    val firstCardFocus = remember { FocusRequester() }
                    val rowState = rememberLazyListState()
                    val restoreIndex =
                        restoreItemId?.let { id -> row.items.indexOfFirst { it.id == id } } ?: -1
                    // Scroll the restored card into existence: a LazyRow hasn't
                    // composed off-screen cards, so its FocusRequester wouldn't
                    // be attached yet.
                    LaunchedEffect(restoreIndex) {
                        if (restoreIndex > 0) rowState.scrollToItem(restoreIndex)
                    }
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 48.dp, vertical = CardSizeTokens.focusHeadroom,
                        ),
                        modifier = Modifier.focusRestorer(firstCardFocus),
                    ) {
                        itemsIndexed(row.items, key = { _, it -> it.id }) { index, item ->
                            PosterCard(
                                item,
                                onClick = { onItemClick(item) },
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier)
                                    .then(if (index == restoreIndex) Modifier.focusRequester(restoreFocus) else Modifier),
                                columns = columns,
                                progress = progressByMeta[ProgressRepository.metaKey(item.type, item.id)],
                            )
                        }
                    }
                }
        }
    }
}
