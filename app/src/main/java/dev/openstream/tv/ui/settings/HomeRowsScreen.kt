package dev.openstream.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.data.HomeRow
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.components.TvTextField
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Home-row manager: reorder (▲/▼), rename, and hide/show every catalog row.
 * Follows the addon manager's control pattern — all Buttons, stable keys
 * (§5.4/§5.7). Hidden rows stay listed here (dimmed) so they can come back.
 */
@Composable
fun HomeRowsScreen(
    onBack: () -> Unit,
    viewModel: HomeRowsViewModel = hiltViewModel(),
) {
    // Null = the addon list hasn't been read yet; keep the body blank for
    // those frames instead of flashing the "no rows" hint (emulator-observed).
    val rows = viewModel.rows.collectAsStateWithLifecycle().value

    /** Key of the row a rename dialog is open for, or null. */
    var renaming by remember { mutableStateOf<HomeRow?>(null) }

    // Predictable entry point: land on the first row's first control.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(!rows.isNullOrEmpty()) {
        if (!rows.isNullOrEmpty()) runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton(onBack)
            Text(
                text = "Home rows",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }
        Text(
            text = "Continue Watching always comes first and isn't listed here.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.padding(top = 16.dp))

        if (rows == null) {
            // Addon list still loading from Room — a beat of blank body.
        } else if (rows.isEmpty()) {
            Text(
                text = "No rows yet — install an addon first.",
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Scroll-axis headroom for first/last row focus scale (§5.3).
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> row.ref.key }) { index, row ->
                    RowEntry(
                        row = row,
                        onRename = { renaming = row },
                        onToggleHidden = { viewModel.setHidden(row.ref.key, !row.hidden) },
                        onMoveUp = { viewModel.move(row.ref.key, -1) },
                        onMoveDown = { viewModel.move(row.ref.key, +1) },
                        firstFocus = if (index == 0) firstFocus else null,
                    )
                }
            }
        }
    }

    renaming?.let { row ->
        RenameDialog(
            row = row,
            onSave = { name ->
                viewModel.rename(row.ref.key, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun RowEntry(
    row: HomeRow,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    firstFocus: FocusRequester?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title + if (row.hidden) "  · hidden" else "",
                style = MaterialTheme.typography.titleMedium,
                color = if (row.hidden) MutedText else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.ref.addon.manifest.name} · ${row.ref.catalog.type}",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onRename,
            modifier = firstFocus?.let { Modifier.focusRequester(it) } ?: Modifier,
        ) { Text("Rename") }
        Button(onClick = onToggleHidden) {
            Text(if (row.hidden) "Show" else "Hide")
        }
        Button(onClick = onMoveUp) { Text("▲") }
        Button(onClick = onMoveDown) { Text("▼") }
    }
}

/**
 * Rename as a trapped-focus Dialog (§5.4: real Dialog so Back dismisses).
 * The field submits via IME; "Use original name" clears the custom title
 * without making the user erase text on a D-pad keyboard (elder-friendly).
 */
@Composable
private fun RenameDialog(
    row: HomeRow,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(row.title) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .width(480.dp)
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text(
                text = "Rename row",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                text = "${row.ref.catalog.name.ifBlank { row.ref.catalog.id }} · ${row.ref.addon.manifest.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TvTextField(
                value = name,
                onValueChange = { name = it },
                onSubmit = { onSave(name) },
                focusRequester = fieldFocus,
            )
            Button(onClick = { onSave(name) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
            Button(onClick = { onSave("") }, modifier = Modifier.fillMaxWidth()) {
                Text("Use original name")
            }
        }
    }
}
