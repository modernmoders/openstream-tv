package dev.openstream.tv.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.components.PosterCard
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.TvTextField
import dev.openstream.tv.ui.search.SearchViewModel.RowState
import dev.openstream.tv.ui.theme.AppBackground
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BackButton(onBack)
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
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.let { spoken ->
                        query = spoken
                        viewModel.search(spoken)
                    }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    TvTextField(
                        value = query,
                        onValueChange = { query = it },
                        onSubmit = { viewModel.search(query) },
                        focusRequester = fieldFocus,
                        imeAction = ImeAction.Search,
                    )
                }
                if (voiceAvailable) {
                    OutlinedButton(onClick = {
                        // Recognizer can vanish between resolve and click
                        // (app updates) — a dead mic must not crash Search.
                        runCatching { voiceLauncher.launch(voiceIntent) }
                    }) { Text("🎤") }
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
                SearchRow(row, state.columns, onItemClick)
            }
        }
    }
}

// OptIn: focusRestorer — the §10 focus rule has no stable equivalent yet.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchRow(
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
                    // §10 search focus rule: D-pad entry into a result row
                    // lands on its FIRST card (owner bug 2026-07-05: geometric
                    // focus search dropped you mid-row), while coming back
                    // from details restores the card you left.
                    val firstCardFocus = remember { FocusRequester() }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
                        // Vertical headroom: focus scale grows into this gap
                        // instead of overlaying the row title (§5.3).
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 48.dp, vertical = CardSizeTokens.focusHeadroom,
                        ),
                        modifier = Modifier.focusRestorer { firstCardFocus },
                    ) {
                        itemsIndexed(row.items, key = { _, item -> item.id }) { index, item ->
                            PosterCard(
                                item,
                                onClick = { onItemClick(item) },
                                modifier = if (index == 0) Modifier.focusRequester(firstCardFocus)
                                else Modifier,
                                columns = columns,
                            )
                        }
                    }
                }
        }
    }
}
