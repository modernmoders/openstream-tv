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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.components.ContinueWatchingCard
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.LoadingMessage
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.home.HomeViewModel.RowState
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.CardSizeTokens
import dev.openstream.tv.ui.theme.Hairline
import dev.openstream.tv.ui.theme.MutedText
import dev.openstream.tv.ui.theme.SurfaceCard

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
    LaunchedEffect(Unit) { runCatching { headerFocus.requestFocus() } }

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
                // The family-facing brand ("SavoyStreams" on the owner's
                // boxes); addon management now lives in Settings behind
                // Expert mode — the home header stays friendly-only
                // (owner directive 2026-07-06).
                text = BuildConfig.SETUP_BRAND,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SurfacePill("Discover", onDiscover, Modifier.focusRequester(headerFocus))
                SurfacePill("Search", onSearch)
                SurfacePill("Settings", onSettings)
            }
        }

        when {
            state.initializing -> Unit // Room read takes ~ms; avoid a flash

            !state.hasAddons -> EmptyHome()

            else -> {
                // Featured = the first item of the first catalog that answered
                // with content. A quiet spotlight, not a rotating carousel
                // (refined, not dramatic — owner 2026-07-06, DECISIONS #30).
                val featured = state.rows
                    .filterIsInstance<RowState.Loaded>()
                    .firstOrNull { it.items.isNotEmpty() }
                    ?.items?.firstOrNull()

                // A LazyColumn anchors its scroll to the first item, so when the
                // hero is inserted at the top (rows load after the first frame)
                // it lands just above the viewport. Snap to the top once, the
                // moment the hero first appears — after that the user's own
                // scrolling is left alone.
                val listState = rememberLazyListState()
                LaunchedEffect(featured != null) {
                    if (featured != null) listState.scrollToItem(0)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Rows carry ±focusHeadroom internally, so the small spacing
                    // here keeps the visual rhythm of the old 20dp gap (§5.3).
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                ) {
                    if (featured != null) {
                        item(key = "featured") {
                            FeaturedHero(featured, onItemClick)
                        }
                    }
                    if (state.continueWatching.isNotEmpty()) {
                        item(key = "continue-watching") {
                            ContinueWatchingRow(state.continueWatching, onItemClick)
                        }
                    }
                    items(state.rows, key = { it.ref.key }) { row ->
                        CatalogRow(row, state.columns, onItemClick)
                    }
                }
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
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = { onItemClick(item) },
        modifier = Modifier
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
@Composable
private fun ContinueWatchingRow(
    entries: List<WatchProgress>,
    onItemClick: (MetaItem) -> Unit,
) {
    Column {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
            // Vertical headroom: a focused card scales into this gap instead
            // of overlaying the row title / getting clipped (§5.3).
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 48.dp, vertical = CardSizeTokens.focusHeadroom,
            ),
        ) {
            items(entries, key = { "${it.ref.sourceKind}:${it.ref.externalId}" }) { p ->
                ContinueWatchingCard(
                    progress = p,
                    onClick = {
                        // Minimal MetaItem: details re-resolves the full meta by id.
                        onItemClick(
                            MetaItem(id = p.metaId, type = p.metaType, name = p.title, poster = p.poster)
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(
    row: RowState,
    columns: Int,
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit,
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
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 48.dp, vertical = CardSizeTokens.focusHeadroom,
                        ),
                    ) {
                        items(row.items, key = { it.id }) { item ->
                            PosterCard(item, onClick = { onItemClick(item) }, columns = columns)
                        }
                    }
                }
        }
    }
}
