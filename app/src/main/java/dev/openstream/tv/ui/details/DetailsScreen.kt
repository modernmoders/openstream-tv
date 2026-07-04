package dev.openstream.tv.ui.details

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Details page (MASTER_PLAN §10 Phase 2): backdrop, meta facts, description,
 * and — for series/channels — season selector + episode list. Every playable
 * row navigates to the stream list for its video id.
 */
@Composable
fun DetailsScreen(
    onOpenStreams: (type: String, videoId: String, title: String) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            state.loading -> RowMessage("Loading…")
            state.error != null -> RowMessage("⚠ ${state.error}")
            state.meta != null -> DetailsContent(
                meta = state.meta!!,
                seasons = state.seasons,
                selectedSeason = state.selectedSeason,
                episodes = state.episodesOfSeason,
                onSelectSeason = viewModel::selectSeason,
                onPlayMovie = {
                    // Movies: video id == meta id (spec)
                    onOpenStreams(viewModel.type, viewModel.id, state.meta!!.name)
                },
                onPlayEpisode = { video ->
                    onOpenStreams(viewModel.type, video.id, video.displayTitle)
                },
            )
        }
    }
}

@Composable
private fun DetailsContent(
    meta: MetaItem,
    seasons: List<Int>,
    selectedSeason: Int?,
    episodes: List<Video>,
    onSelectSeason: (Int) -> Unit,
    onPlayMovie: () -> Unit,
    onPlayEpisode: (Video) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Button(onClick = onPlayMovie) { Text("View streams") }
            }
        } else {
            if (seasons.isNotEmpty()) {
                item(key = "seasons") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { it }) { season ->
                            Button(onClick = { onSelectSeason(season) }) {
                                Text(
                                    text = when {
                                        season == 0 -> "Specials"
                                        season == selectedSeason -> "Season $season ✓"
                                        else -> "Season $season"
                                    }
                                )
                            }
                        }
                    }
                }
            }
            items(episodes, key = { it.id }) { video ->
                EpisodeRow(video = video, onClick = { onPlayEpisode(video) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(video: Video, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(0.75f)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                text = video.episode?.let { "E$it  ${video.displayTitle}" }
                    ?: video.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            video.overview?.let {
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
