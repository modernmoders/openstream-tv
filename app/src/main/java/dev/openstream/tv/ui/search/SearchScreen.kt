package dev.openstream.tv.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.components.MicIconImage
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.SkeletonPosterRow
import dev.openstream.tv.ui.components.TvTextField
import dev.openstream.tv.ui.components.rememberRowEntryMemory
import dev.openstream.tv.ui.components.rowEntry
import dev.openstream.tv.ui.search.SearchViewModel.RowState
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.AmbientSection
import dev.openstream.tv.ui.theme.ambientBackground
import dev.openstream.tv.ui.theme.CardSizeTokens
import dev.openstream.tv.ui.theme.MutedText

@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }

    // Voice-first bookkeeping (owner 2026-07-12): every deliberate click into
    // Search bumps the trigger; each unseen bump fires the mic ONCE. Saved so
    // a BACK-return (same counter) never re-fires, and so the pending fire is
    // known BEFORE the field grabs focus — focusing the field pops the
    // on-screen keyboard, which then sat under/after the voice overlay.
    val voiceRequests by viewModel.voiceSearchRequests.collectAsStateWithLifecycle()
    var consumedVoiceRequest by rememberSaveable { mutableStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val micFocus = remember { FocusRequester() }
    // True from the instant the mic is (auto-)fired until the recognizer
    // returns — the mic button fills solid accent for exactly that span, so
    // "the TV is listening" is visible from the couch (owner 2026-07-12:
    // "light up instantly and darken back to normal when it's done").
    var listening by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val voicePending = state.voiceFirst && voiceRequests > consumedVoiceRequest
        if (!voicePending) {
            fieldFocus.requestFocus()
        } else {
            // Voice entry: selection lands on the MIC, not back on the rail
            // (owner 2026-07-12: "the selection stays in the sidebar over the
            // magnifying glass"). Probe a few frames — the pill composes late.
            repeat(10) {
                if (runCatching { micFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                withFrameNanos { }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ambientBackground(AmbientSection.SEARCH)
            .padding(vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
            }
            // Voice search (§10 backlog "mic"): the system speech recognizer
            // does the listening — no RECORD_AUDIO permission on our side.
            // The button only appears when a recognizer exists on the device.
            val context = LocalContext.current
            val voiceIntent = remember {
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
            }
            val voiceAvailable = remember {
                voiceIntent.resolveActivity(context.packageManager) != null
            }
            val voiceLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                // Done listening — answered or backed out — the mic dims back
                // to normal and the on-screen keyboard must not be left
                // standing (owner 2026-07-12).
                listening = false
                keyboard?.hide()
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.let { spoken ->
                        query = spoken
                        viewModel.search(spoken)
                    }
            }
            // One path for every mic fire (click or voice-first auto): light
            // the button BEFORE the recognizer overlay opens; a launch failure
            // (recognizer vanished mid-session) dims it right back.
            fun startListening() {
                keyboard?.hide()
                listening = true
                if (runCatching { voiceLauncher.launch(voiceIntent) }.isFailure) {
                    listening = false
                }
            }

            // Voice-first (owner Round-15 #9 + Round-16): EVERY deliberate
            // click into Search (rail/pill → VoiceSearchTrigger) fires the mic
            // once — even over an old search. BACK-returns keep the counter,
            // so they never re-fire. The keyboard is hidden first so it can't
            // pop under the voice overlay and linger after the result.
            LaunchedEffect(voiceRequests) {
                if (state.voiceFirst && voiceAvailable &&
                    voiceRequests > consumedVoiceRequest
                ) {
                    consumedVoiceRequest = voiceRequests
                    runCatching { micFocus.requestFocus() }
                    startListening()
                } else if (voiceRequests > consumedVoiceRequest) {
                    // Voice off or no recognizer: consume quietly, type instead.
                    consumedVoiceRequest = voiceRequests
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Mic on the LEFT of the field (owner Round-15 #9): speaking
                // is the first-class way in, typing the fallback. `selected`
                // rides the listening state — the shared pill's solid accent
                // fill IS the "I'm listening" light.
                if (voiceAvailable) {
                    SurfacePill(
                        onClick = ::startListening,
                        selected = listening,
                        modifier = Modifier.focusRequester(micFocus),
                    ) {
                        // Crisp vector glyph (no emoji, house rule); dark ink
                        // over the lit accent fill, accent over the quiet one.
                        MicIconImage(
                            tint = if (listening) Color(0xFF0E0E16) else Accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    TvTextField(
                        value = query,
                        onValueChange = { query = it },
                        onSubmit = { viewModel.search(query) },
                        focusRequester = fieldFocus,
                        imeAction = ImeAction.Search,
                    )
                }
            }
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
                SearchRow(row, state.columns, state.progressByMeta, state.seriesWatchByMeta, onItemClick)
            }
        }
    }
}

@Composable
private fun SearchRow(
    row: RowState,
    columns: Int,
    progressByMeta: Map<String, dev.openstream.tv.domain.WatchProgress>,
    seriesWatchByMeta: Map<String, dev.openstream.tv.domain.SeriesWatch>,
    onItemClick: (dev.openstream.tv.addon.MetaItem) -> Unit,
) {
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
            // Tile silhouettes while searching — the row holds its real
            // height, so results land without reflowing the column (#9).
            is RowState.Loading -> SkeletonPosterRow(columns)
            is RowState.Failed -> RowMessage("⚠ ${row.ref.addon.manifest.name}: ${row.message}")
            is RowState.Loaded ->
                if (row.items.isEmpty()) {
                    RowMessage("No results")
                } else {
                    // §10 search focus rule: D-pad entry into a result row
                    // lands on its FIRST card (owner bug 2026-07-05: geometric
                    // focus search dropped you mid-row), while coming back
                    // from details restores the card you left. Shared
                    // RowEntryMemory (DECISIONS #56): index-based, so lazy
                    // node recycling can never shift the row sideways.
                    val memory = rememberRowEntryMemory()
                    val entryIndex = memory.entryIndex(row.items.size)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                        // Vertical headroom: focus scale grows into this gap
                        // instead of overlaying the row title (§5.3).
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 48.dp, vertical = CardSizeTokens.focusHeadroom,
                        ),
                        modifier = Modifier.rowEntry(memory),
                    ) {
                        itemsIndexed(row.items, key = { _, item -> item.id }) { index, item ->
                            PosterCard(
                                item,
                                onClick = { onItemClick(item) },
                                modifier = Modifier
                                    .onFocusChanged { if (it.isFocused) memory.lastFocusedIndex = index }
                                    .then(
                                        if (index == entryIndex) {
                                            Modifier.focusRequester(memory.entryFocus)
                                        } else Modifier
                                    ),
                                columns = columns,
                                progress = progressByMeta[
                                    dev.openstream.tv.data.ProgressRepository.metaKey(item.type, item.id),
                                ],
                                seriesWatch = seriesWatchByMeta[
                                    dev.openstream.tv.data.ProgressRepository.metaKey(item.type, item.id),
                                ],
                            )
                        }
                    }
                }
        }
    }
}
