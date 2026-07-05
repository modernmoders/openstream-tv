package dev.openstream.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.addon.Video
import dev.openstream.tv.autoplay.AutoplayStateMachine
import dev.openstream.tv.ui.theme.MutedText

/**
 * The Up Next flow's card (§7.1 step 2/4): visible waiting, never a dead
 * screen. Shared between the internal player screen and the stream list
 * (which hosts the §7.1.6 best-effort flow after an external player returns).
 * Renders nothing when [state] is null or terminal.
 */
@Composable
fun UpNextOverlay(state: AutoplayStateMachine.State?) {
    when (state) {
        is AutoplayStateMachine.State.Countdown -> UpNextCard(
            headline = "Up next: ${state.next.upNextLabel()}",
            detail = "Playing in ${state.secondsLeft}s   (OK play now · Back cancel)",
        )
        is AutoplayStateMachine.State.Resolving -> UpNextCard(
            headline = "Finding next episode…",
            detail = if (state.totalAddons > 0) {
                "${state.respondedAddons}/${state.totalAddons} addons responded   (Back cancel)"
            } else "(Back cancel)",
        )
        is AutoplayStateMachine.State.Attempting -> UpNextCard(
            headline = "Starting ${state.next.upNextLabel()}",
            detail = if (state.attempt > 0) "Attempt ${state.attempt + 1}" else "",
        )
        else -> Unit
    }
}

fun Video.upNextLabel(): String {
    val se = if (season != null && episode != null) "S${season}E$episode" else null
    return listOfNotNull(se, displayTitle.takeIf { it.isNotBlank() && it != id }).joinToString(" · ")
        .ifBlank { displayTitle }
}

@Composable
private fun UpNextCard(headline: String, detail: String) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .background(Color(0xF0181822))
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MutedText)
            }
        }
    }
}
