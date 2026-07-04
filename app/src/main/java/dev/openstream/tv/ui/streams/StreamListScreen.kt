package dev.openstream.tv.ui.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.streams.StreamListViewModel.GroupState
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Stream list: groups by addon in user order, streams in addon order —
 * NEVER re-sorted (§4.1.7). Non-URL sources (torrents, external links) are
 * visible but not playable in v1 (§4.1.4).
 */
@Composable
fun StreamListScreen(
    onPlay: () -> Unit = {},
    viewModel: StreamListViewModel = hiltViewModel(),
) {
    val onStreamSelected: (Stream) -> Unit = { stream ->
        if (viewModel.stage(stream)) onPlay()
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = viewModel.title.ifBlank { "Streams" },
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (!state.initializing && state.groups.isEmpty()) {
            RowMessage(
                "No installed addon provides streams for this item — " +
                    "add a stream addon (e.g. an AIOStreams instance)",
                horizontalPadding = 0.dp,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.groups.forEach { group ->
                item(key = "header-${group.addon.manifestUrl}") {
                    Text(
                        text = group.addon.manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                when (group) {
                    is GroupState.Loading -> item(key = "loading-${group.addon.manifestUrl}") {
                        RowMessage("Loading…", horizontalPadding = 0.dp)
                    }
                    is GroupState.Failed -> item(key = "failed-${group.addon.manifestUrl}") {
                        RowMessage("⚠ ${group.message}", horizontalPadding = 0.dp)
                    }
                    is GroupState.Loaded ->
                        if (group.streams.isEmpty()) {
                            item(key = "empty-${group.addon.manifestUrl}") {
                                RowMessage("No streams", horizontalPadding = 0.dp)
                            }
                        } else {
                            // Index in key: addons may return near-identical rows
                            group.streams.forEachIndexed { index, stream ->
                                item(key = "s-${group.addon.manifestUrl}-$index") {
                                    StreamRow(stream, onClick = { onStreamSelected(stream) })
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(stream: Stream, onClick: () -> Unit) {
    if (stream.isPlayableInV1) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(0.85f)) {
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = stream.name ?: "Stream",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stream.displayDescription.isNotBlank()) {
                    Text(
                        text = stream.displayDescription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else {
        // §4.1.4: parsed and visible, but not playable in v1 — an honest note
        // instead of a dead button (no focusable dead-ends, §5.4).
        Text(
            text = "◇ ${stream.name ?: "Stream"} — unsupported source in v1 " +
                (stream.infoHash?.let { "(torrent)" } ?: "(external)"),
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}
