package dev.openstream.tv.ui.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.diagnostics.DiagnosticsLog
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.theme.AmbientSection
import dev.openstream.tv.ui.theme.ambientBackground
import dev.openstream.tv.ui.theme.MutedText
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Settings → Expert mode → App log (MASTER_PLAN §10): the on-device
 * diagnostics the friendly fallback UI deliberately hides. Read-only plus
 * one Clear — this is a support tool for whoever looks after the box, not
 * a feature screen.
 */
@HiltViewModel
class AppLogViewModel @Inject constructor(
    private val log: DiagnosticsLog,
) : ViewModel() {

    /** Null = still reading; empty = nothing logged. Newest first. */
    private val _lines = MutableStateFlow<List<String>?>(null)
    val lines: StateFlow<List<String>?> = _lines

    init {
        viewModelScope.launch { _lines.value = log.read() }
    }

    fun clear() {
        viewModelScope.launch {
            log.clear()
            _lines.value = emptyList()
        }
    }
}

@Composable
fun AppLogScreen(
    onBack: () -> Unit,
    viewModel: AppLogViewModel = hiltViewModel(),
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ambientBackground(AmbientSection.SETTINGS)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BackButton(onBack)
            Text(
                text = "App log",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (!lines.isNullOrEmpty()) {
                OutlinedButton(onClick = viewModel::clear) { Text("Clear log") }
            }
        }

        Spacer(Modifier.padding(top = 20.dp))

        when {
            lines == null -> Unit // file read takes ~ms; avoid a flash

            lines!!.isEmpty() -> Text(
                text = "Nothing logged — no problems recorded on this TV.",
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(lines!!) { line -> LogLine(line) }
            }
        }
    }
}

/**
 * A log line is focusable so the D-pad can walk (and thereby scroll) the
 * list; the focused line brightens instead of drawing a border — it's a
 * reading aid, not a button.
 */
@Composable
private fun LogLine(line: String) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = if (focused) Color.White else MutedText,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    )
}
