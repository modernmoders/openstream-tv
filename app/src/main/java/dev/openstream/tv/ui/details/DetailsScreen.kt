package dev.openstream.tv.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.addon.Video
import dev.openstream.tv.data.EpisodeNumbering
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.components.LoadingMessage
import dev.openstream.tv.ui.components.ProgressRing
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.components.SurfaceRow
import dev.openstream.tv.ui.components.UnwatchedRing
import dev.openstream.tv.ui.components.WatchedArtworkDim
import dev.openstream.tv.ui.components.WatchedDisc
import dev.openstream.tv.ui.components.minutesLeftOf
import dev.openstream.tv.ui.theme.Accent
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
    val episodeProgress by viewModel.episodeProgress.collectAsStateWithLifecycle()

    // The Back button is the first focusable on this screen, so anchor entry
    // focus on the primary action — play/seasons/episodes (BackButton KDoc).
    // DetailsContent owns the focus request: for a resume it must scroll the
    // target episode into existence BEFORE focusing it (LazyColumn only
    // composes visible rows), which the outer scope can't sequence.
    val primaryFocus = remember { FocusRequester() }

    // Round 20 #3: backing out of the stream list must land on the episode
    // the user actually clicked — NOT the ViewModel's resume suggestion (the
    // next unwatched episode, usually a few rows earlier). Saveable so it
    // survives the trip through the back stack; null until a click, so a
    // fresh Details open still anchors on the resume episode.
    var openedVideoId by rememberSaveable { mutableStateOf<String?>(null) }

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
                numbering = state.numbering,
                absoluteNumbers = state.absoluteNumbers,
                episodeProgress = episodeProgress,
                entryVideoId = openedVideoId ?: state.resumeVideoId,
                onSelectSeason = viewModel::selectSeason,
                onPlayMovie = {
                    // Movies: video id == meta id (spec)
                    onOpenStreams(
                        viewModel.type, viewModel.id, state.meta!!.name,
                        viewModel.id, state.meta!!.poster,
                    )
                },
                onPlayEpisode = { video ->
                    openedVideoId = video.id
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
    numbering: EpisodeNumbering,
    absoluteNumbers: Map<String, Int>,
    episodeProgress: Map<String, WatchProgress>,
    /** Entry-focus anchor: the episode the user last opened, else the resume
     *  suggestion — see [DetailsScreen]'s openedVideoId (round 20 #3). */
    entryVideoId: String?,
    onSelectSeason: (Int) -> Unit,
    onPlayMovie: () -> Unit,
    onPlayEpisode: (Video) -> Unit,
) {
    val listState = rememberLazyListState()
    // Entry focus. For a resume, scroll the target episode into existence first
    // (a LazyColumn hasn't composed off-screen rows, so its FocusRequester would
    // otherwise throw) then focus it; otherwise focus the default anchor. Runs
    // once — season changes recompose but must not re-yank focus.
    LaunchedEffect(Unit) {
        val epIdx = entryVideoId?.let { id -> episodes.indexOfFirst { it.id == id } } ?: -1
        if (epIdx >= 0) {
            val headerItems = 1 + if (seasons.isNotEmpty()) 1 else 0 // header (+ sticky seasons)
            listState.scrollToItem(headerItems + epIdx)
        }
        runCatching { primaryFocus.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                        modifier = Modifier.weight(1f),
                    )
                    // Show-level roll-up (watched system): a small ring + "31 of
                    // 98 episodes watched". Only once there's history — a fresh
                    // show doesn't need "0 of 98" chrome.
                    val showStats = remember(meta.videos, episodeProgress) {
                        showWatchStats(meta.videos, episodeProgress)
                    }
                    if (showStats.watched > 0 && showStats.total > 0) {
                        ProgressRing(
                            fraction = showStats.fraction,
                            size = 18.dp,
                            showPercent = false,
                            scrim = false,
                        )
                        Text(
                            text = "${showStats.watched} of ${showStats.total} episodes watched",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText,
                        )
                    }
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
            // Movie (or any other single-video item, e.g. an addon that skips
            // episodes): a big primary Play CTA, plus a trailer button ONLY
            // when the meta actually carries one (owner round 10 easy-mode
            // Info screen — most addons send no trailers at all).
            item(key = "play") {
                // Movie watch state (video id == meta id): "Resume" + a bar for
                // a partly-watched film, "Play again" for a finished one.
                val movieWatch = episodeProgress[meta.id]
                val movieWatched = movieWatch != null && ProgressRepository.isWatched(movieWatch)
                val movieResumable = movieWatch != null && !movieWatched && movieWatch.fractionWatched > 0f
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlayMovie,
                        modifier = Modifier.focusRequester(primaryFocus),
                    ) {
                        Text(
                            when {
                                movieResumable -> "▶  Resume"
                                movieWatched -> "▶  Play again"
                                else -> "▶  Play"
                            }
                        )
                    }
                    val trailer = meta.trailers.firstOrNull()
                    if (trailer != null) {
                        val context = LocalContext.current
                        OutlinedButton(onClick = {
                            // External, not embedded: a broken/uninstalled
                            // YouTube app must not dead-end this screen.
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(trailer.youtubeUrl))
                                )
                            }
                        }) { Text("Watch trailer") }
                    }
                }
                if (movieResumable) {
                    ProgressBar(
                        fraction = movieWatch!!.fractionWatched,
                        modifier = Modifier.fillMaxWidth(0.35f),
                    )
                }
                }
            }
            // TODO(owner round 10, OUT OF SCOPE for this pass): a "More like
            // this" row belongs here — same catalog/genre recommendations
            // under the Play/trailer CTA, in the shared PosterCard row
            // language. Needs a data source decision (related-catalog lookup
            // vs. a genre-filtered Discover query) before it's worth building.
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
                        // Without a restorer, coming back UP from an episode row
                        // leaves the chip to Compose's geometric focus search:
                        // the episode row spans the full width, so the search
                        // picks whichever chip sits nearest its centre — which is
                        // never the one you left, and drifts further right on
                        // every trip (owner round 13 #5: season 1 → 3 → 5 → 7).
                        // focusRestorer pins re-entry to the chip focus left from,
                        // falling back to the SELECTED season, the one whose
                        // episodes are actually on screen.
                        val selectedChipFocus = remember { FocusRequester() }
                        val seasonRowState = rememberLazyListState()
                        // Bring the selected chip on screen once, at entry: a
                        // resume can land on season 5, whose chip would otherwise
                        // never be composed — and an unattached fallback
                        // FocusRequester is one the restorer cannot focus. Entry
                        // only, so selecting a season later doesn't jolt the row.
                        val selectedChipIndex = seasons.indexOf(selectedSeason)
                        LaunchedEffect(Unit) {
                            if (selectedChipIndex > 0) seasonRowState.scrollToItem(selectedChipIndex)
                        }
                        // 8dp edge padding: the row clips on its scroll axis, so
                        // the first/last chip's focus scale needs headroom. The
                        // slight indent is invisible at 10 feet (§5.3).
                        // Season roll-ups (watched system): a mini check disc on
                        // a fully watched season, a "3 / 14" count on the
                        // selected one — recomputed live as marks land.
                        val seasonStats = remember(meta.videos, episodeProgress) {
                            seasonWatchStats(meta.videos, episodeProgress)
                        }
                        LazyRow(
                            state = seasonRowState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.focusRestorer(selectedChipFocus),
                        ) {
                            items(seasons, key = { it }) { season ->
                                // With an entry target the episode row owns entry
                                // focus, not the season chip.
                                val isEntryChip =
                                    season == seasons.first() && entryVideoId == null
                                val stats = seasonStats[season]
                                SurfacePill(
                                    onClick = { onSelectSeason(season) },
                                    selected = season == selectedSeason,
                                    modifier = Modifier
                                        .then(
                                            if (season == selectedSeason) {
                                                Modifier.focusRequester(selectedChipFocus)
                                            } else Modifier
                                        )
                                        .then(
                                            if (isEntryChip) {
                                                Modifier.focusRequester(primaryFocus)
                                            } else Modifier
                                        ),
                                ) {
                                    Text(
                                        text = if (season == 0) "Specials" else "Season $season",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                    )
                                    when {
                                        stats?.complete == true ->
                                            WatchedDisc(size = 14.dp)
                                        season == selectedSeason && (stats?.watched ?: 0) > 0 ->
                                            Text(
                                                text = "${stats!!.watched} / ${stats.total}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.8f),
                                                maxLines = 1,
                                            )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(episodes, key = { _, it -> it.id }) { index, video ->
                // Absolute numbering falls back to the per-season number for
                // specials (season 0), which are left out of the absolute count.
                val episodeNumber = when (numbering) {
                    EpisodeNumbering.ABSOLUTE -> absoluteNumbers[video.id] ?: video.episode
                    EpisodeNumbering.SEASONAL -> video.episode
                }
                // This episode's stored progress, if any — the row derives its
                // watched/resume/new state from it (keyed by video id = externalId).
                val watch = episodeProgress[video.id]
                EpisodeRow(
                    video = video,
                    episodeNumber = episodeNumber,
                    watch = watch,
                    // Round-15 #8: metahub (the ecosystem's episode-still CDN)
                    // simply has no images for long anime past ~ep 52 (Naruto
                    // S2+ 404s — same gap the owner saw on Stremio). When a
                    // still fails to load, fall back to the show's backdrop so
                    // the row never renders an empty gray box.
                    fallbackArt = meta.background ?: meta.poster,
                    onClick = { onPlayEpisode(video) },
                    // Entry focus lands here when this is the last-opened or
                    // resume episode, or (no history) on the first episode of
                    // a season-less series.
                    modifier = if (
                        video.id == entryVideoId ||
                        (entryVideoId == null && seasons.isEmpty() && index == 0)
                    ) {
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
 *
 * Watch state (design_handoff_watched_system): a fixed trailing status
 * column reads NEW (dashed circle) / RESUME (progress ring + percent) /
 * WATCHED (check disc); a watched row's content drops to 62% opacity and
 * its thumbnail dims so unwatched episodes pop forward; an in-progress row
 * keeps the resume bar on the thumbnail's bottom edge + "N min left".
 */
@Composable
private fun EpisodeRow(
    video: Video,
    episodeNumber: Int?,
    watch: WatchProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackArt: String? = null,
) {
    val watched = watch != null && ProgressRepository.isWatched(watch)
    val progressFraction = if (watched) 0f else watch?.fractionWatched ?: 0f
    val inProgress = progressFraction > 0f
    // Watched rows recede; the status column keeps full strength so the
    // check + label stay readable across the room.
    val contentAlpha = if (watched) 0.62f else 1f
    SurfaceRow(onClick = onClick, modifier = modifier) {
        if (video.thumbnail != null) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .alpha(contentAlpha),
            ) {
                // A 404'd still swaps to the show's backdrop (one retry, then
                // the flat placeholder) — see the call site's #8 note.
                var stillFailed by remember(video.thumbnail) { mutableStateOf(false) }
                AsyncImage(
                    model = if (stillFailed && fallbackArt != null) fallbackArt else video.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(EpisodeThumbPlaceholder),
                    error = ColorPainter(EpisodeThumbPlaceholder),
                    onError = { if (!stillFailed) stillFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
                if (watched) {
                    Box(Modifier.fillMaxSize().background(WatchedArtworkDim))
                }
                if (inProgress) {
                    ProgressBar(
                        fraction = progressFraction,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).alpha(contentAlpha)) {
            Text(
                // "Episode 1 · System" — spelled out, no "E1" (plain words
                // for people who don't speak in TV shorthand, owner
                // 2026-07-06). Addons without real episode names title them
                // "Episode N" themselves — don't print "Episode 1 · Episode 1".
                text = episodeNumber?.let { ep ->
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
            if (inProgress) {
                // No thumbnail to hang the resume bar on: a thin line here.
                if (video.thumbnail == null) {
                    ProgressBar(
                        fraction = progressFraction,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(0.5f),
                    )
                }
                Text(
                    text = "${minutesLeftOf(watch!!)} min left",
                    style = MaterialTheme.typography.labelSmall,
                    color = ResumeLabel,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // Fixed-width status column: same slot on every row, so the state
        // reads as a scannable rail down the list (handoff 1b).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.width(58.dp),
        ) {
            when {
                watched -> {
                    WatchedDisc(size = 24.dp)
                    StatusLabel("WATCHED", WatchedLabel)
                }
                inProgress -> {
                    ProgressRing(fraction = progressFraction, size = 28.dp, scrim = false)
                    StatusLabel("RESUME", ResumeLabel)
                }
                else -> {
                    UnwatchedRing(size = 22.dp)
                    StatusLabel("NEW", NewLabel)
                }
            }
        }
    }
}

/** Tiny uppercase caption under a status glyph (WATCHED / RESUME / NEW). */
@Composable
private fun StatusLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
    )
}

/** A resume bar: dim track, accent fill up to [fraction] (0..1).
 *  6dp over a near-solid track so it reads across the room (owner 2026-07-09). */
@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Accent),
        )
    }
}

/** Matches SurfaceCard so a loading thumbnail blends into its row. */
private val EpisodeThumbPlaceholder = Color(0xFF23232F)

// Status-label colors from the handoff: each state's caption carries its own
// weight — WATCHED muted (it recedes), RESUME an accent tint (it invites),
// NEW barely-there (untouched content needs no chrome).
private val WatchedLabel = Color(0xFF7C8B9C)
private val ResumeLabel = Color(0xFF8FB4E8)
private val NewLabel = Color(0xFF4E5D6C)
