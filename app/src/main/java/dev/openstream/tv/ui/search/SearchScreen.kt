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
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.openstream.tv.ui.components.MicIconImage
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

    LaunchedEffect(Unit) {
        val voicePending = state.voiceFirst && voiceRequests > consumedVoiceRequest
        if (!voicePending) fieldFocus.requestFocus()
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
                // Whether the mic answered or was backed out of, the on-screen
                // keyboard must not be left standing (owner 2026-07-12).
                keyboard?.hide()
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.let { spoken ->
                        query = spoken
                        viewModel.search(spoken)
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
                    keyboard?.hide()
                    runCatching { voiceLauncher.launch(voiceIntent) }
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
                // is the first-class way in, typing the fallback.
                if (voiceAvailable) {
                    OutlinedButton(onClick = {
                        // Recognizer can vanish between resolve and click
                        // (app updates) — a dead mic must not crash Search.
                        runCatching { voiceLauncher.launch(voiceIntent) }
                    }) {
                        // Crisp vector glyph, tinted to the theme accent —
                        // replaces the "🎤" emoji (owner: "swap that
                        // microphone for a better, clearer icon").
                        MicIconImage(tint = Accent, modifier = Modifier.size(20.dp))
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
                SearchRow(row, state.columns, state.progressByMeta, onItemClick)
            }
        }
    }
}

@Composable
private fun SearchRow(
    row: RowState,
    columns: Int,
    progressByMeta: Map<String, dev.openstream.tv.domain.WatchProgress>,
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
                            )
                        }
                    }
                }
        }
    }
}
