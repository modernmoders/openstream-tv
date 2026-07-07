package dev.openstream.tv.ui.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.addon.Video
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.components.LoadingMessage
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.components.SurfaceRow
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Details page (MASTER_PLAN §10 Phase 2): backdrop, meta facts, description,
 * and — for series/channels — season selector + episode list. Every playable
 * row navigates to the stream list for its video id.
 */
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onOpenStreams: (type: String, videoId: String, title: String, metaId: String, poster: String?) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The Back button is the first focusable on this screen, so anchor entry
    // focus on the primary action — play/seasons/episodes (BackButton KDoc).
    // runCatching: with a slow addon the anchor target may not be composed
    // yet; falling back to default focus beats crashing.
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(state.meta != null) {
        if (state.meta != null) runCatching { primaryFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        // Backdrop behind everything, dimmed toward the text side.
        state.meta?.background?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.35f),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to AppBackground,
                            0.6f to AppBackground.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        )
                    )
            )
        }

        when {
            state.loading -> LoadingMessage()
            state.error != null -> RowMessage("⚠ ${state.error}")
            state.meta != null -> DetailsContent(
                onBack = onBack,
                primaryFocus = primaryFocus,
                meta = state.meta!!,
                seasons = state.seasons,
                selectedSeason = state.selectedSeason,
                episodes = state.episodesOfSeason,
                onSelectSeason = viewModel::selectSeason,
                onPlayMovie = {
                    // Movies: video id == meta id (spec)
                    onOpenStreams(
                        viewModel.type, viewModel.id, state.meta!!.name,
                        viewModel.id, state.meta!!.poster,
                    )
                },
                onPlayEpisode = { video ->
                    onOpenStreams(
                        viewModel.type, video.id, video.displayTitle,
                        viewModel.id, state.meta!!.poster,
                    )
                },
            )
        }
    }
}

// OptIn: stickyHeader (Compose Foundation) pins the current season indicator
// at the top of the viewport while episodes scroll beneath it (owner request).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsContent(
    onBack: () -> Unit,
    primaryFocus: FocusRequester,
    meta: MetaItem,
    seasons: List<Int>,
    selectedSeason: Int?,
    episodes: List<Video>,
    onSelectSeason: (Int) -> Unit,
    onPlayMovie: () -> Unit,
    onPlayEpisode: (Video) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // contentPadding (not outer padding): the clip boundary stays at the
        // screen edge, so a focused item's 1.1× scale grows into the overscan
        // gutter instead of being cut off (§5.3). Extra bottom room so the last
        // episode can scroll clear of the fade below.
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 27.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackButton(onBack)
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val facts = listOfNotNull(
                        meta.releaseInfo?.takeIf { it.isNotBlank() },
                        meta.runtime?.takeIf { it.isNotBlank() },
                        meta.imdbRating?.takeIf { it.isNotBlank() }?.let { "IMDb $it" },
                        meta.genres.take(3).joinToString(", ").ifBlank { null },
                    )
                    Text(
                        text = facts.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MutedText,
                    )
                }
                meta.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                }
                if (meta.cast.isNotEmpty()) {
                    Text(
                        text = "Cast: " + meta.cast.take(5).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                    )
                }
            }
        }

        if (meta.videos.isEmpty()) {
            // Movie (or any single-video item): one action.
            item(key = "play") {
                Button(
                    onClick = onPlayMovie,
                    modifier = Modifier.focusRequester(primaryFocus),
                ) { Text("View streams") }
            }
        } else {
            if (seasons.isNotEmpty()) {
                // Sticky, not a plain item (owner: "the currently selected
                // season must stay visible at the top of the screen while
                // scrolling the episode list"): pins at the top of the
                // viewport once scrolled there and stays put while episodes
                // continue past underneath. Opaque background + its own
                // bottom padding so episodes scrolling behind it never show
                // through the strip.
                stickyHeader(key = "seasons") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppBackground)
                            .padding(bottom = 10.dp),
                    ) {
                        // 8dp edge padding: the row clips on its scroll axis, so
                        // the first/last chip's focus scale needs headroom. The
                        // slight indent is invisible at 10 feet (§5.3).
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            items(seasons, key = { it }) { season ->
                                SurfacePill(
                                    label = if (season == 0) "Specials" else "Season $season",
                                    onClick = { onSelectSeason(season) },
                                    selected = season == selectedSeason,
                                    modifier = if (season == seasons.first()) {
                                        Modifier.focusRequester(primaryFocus)
                                    } else Modifier,
                                )
                            }
                        }
                    }
                }
            }
            itemsIndexed(episodes, key = { _, it -> it.id }) { index, video ->
                EpisodeRow(
                    video = video,
                    onClick = { onPlayEpisode(video) },
                    // Season-less series (e.g. channels): anchor the first episode.
                    modifier = if (seasons.isEmpty() && index == 0) {
                        Modifier.focusRequester(primaryFocus)
                    } else Modifier,
                )
            }
        }
    }
        // Nothing hard-cuts: a partly-scrolled row melts into the background
        // instead of a sharp half-episode at the bottom edge (owner 2026-07-06).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to AppBackground)
                )
        )
    }
}

/**
 * An episode row in the shared SurfaceRow language, upgraded with the
 * addon-supplied thumbnail + a 2-3 line synopsis (owner request) when the
 * addon provides them — degrades to the old compact text-only row when it
 * doesn't (many addons send neither). Coil paints a flat placeholder color
 * FIRST (same trick as PosterCard) so the row's layout never pops/reflows
 * as the image arrives — no jank on the 32-bit boxes.
 */
@Composable
private fun EpisodeRow(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SurfaceRow(onClick = onClick, modifier = modifier) {
        if (video.thumbnail != null) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(EpisodeThumbPlaceholder),
                error = ColorPainter(EpisodeThumbPlaceholder),
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                // "Episode 1 · System" — spelled out, no "E1" (plain words for
                // people who don't speak in TV shorthand, owner 2026-07-06).
                // Addons without real episode names title them "Episode N"
                // themselves — don't print "Episode 1 · Episode 1".
                text = video.episode?.let { ep ->
                    val label = "Episode $ep"
                    if (video.displayTitle.equals(label, ignoreCase = true)) label
                    else "$label  ·  ${video.displayTitle}"
                } ?: video.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            video.overview?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    // 2-3 lines (owner request) when there's a thumbnail to
                    // balance against; the old compact 1-line clamp stays for
                    // the thumbnail-less/description-less fallback row.
                    maxLines = if (video.thumbnail != null) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Matches SurfaceCard so a loading thumbnail blends into its row. */
private val EpisodeThumbPlaceholder = Color(0xFF23232F)
