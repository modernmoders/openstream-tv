package dev.openstream.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.player.TrackMenu
import dev.openstream.tv.player.TrackOption
import dev.openstream.tv.ui.theme.MutedText

/**
 * Audio & subtitle picker for the internal player (owner request
 * 2026-07-05). Same trapped-focus real-Dialog pattern as Discover's
 * pickers (§5.4); playback keeps running underneath, Back dismisses.
 * Two side-by-side sections so the whole choice space is one glance —
 * built for the family members who won't explore menus.
 */
@Composable
fun TracksDialog(
    menu: TrackMenu,
    onPick: (TrackOption) -> Unit,
    onSubtitlesOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    // Initial focus on the active audio track so OK-OK changes nothing.
    // Guard: requestFocus() on a never-attached requester throws.
    LaunchedEffect(Unit) {
        if (menu.audio.any { it.selected } || menu.subtitlesOff) selectedFocus.requestFocus()
    }

    // Platform default width is too narrow for two sections side by side.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            TrackSection(title = "Audio") {
                if (menu.audio.isEmpty()) {
                    SectionHint("Nothing to choose yet")
                }
                menu.audio.forEach { option ->
                    TrackButton(
                        label = option.label,
                        selected = option.selected,
                        // Selected audio is the dialog's home position.
                        focusRequester = selectedFocus.takeIf { option.selected },
                        onClick = { onDismiss(); onPick(option) },
                    )
                }
            }
            TrackSection(title = "Subtitles") {
                TrackButton(
                    label = "Off",
                    selected = menu.subtitlesOff,
                    // Fallback home when no audio row claimed the focus.
                    focusRequester = selectedFocus
                        .takeIf { menu.audio.none { a -> a.selected } && menu.subtitlesOff },
                    onClick = { onDismiss(); onSubtitlesOff() },
                )
                if (menu.subtitles.isEmpty()) {
                    SectionHint("This stream has no subtitles")
                }
                menu.subtitles.forEach { option ->
                    TrackButton(
                        label = option.label,
                        selected = option.selected,
                        focusRequester = null,
                        onClick = { onDismiss(); onPick(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackSection(title: String, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.width(320.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                // Inside the clip: scroll-axis headroom for focus scale (§5.3).
                .padding(vertical = 8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TrackButton(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
    ) {
        Text(
            text = label + if (selected) "  ✓" else "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MutedText,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
