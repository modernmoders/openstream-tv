package dev.openstream.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.data.SubtitleBackdrop
import dev.openstream.tv.data.SubtitleEdge
import dev.openstream.tv.data.SubtitleStyle
import dev.openstream.tv.data.SubtitleTextColor
import dev.openstream.tv.data.SubtitleTextSize
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.theme.AmbientSection
import dev.openstream.tv.ui.theme.MutedText
import dev.openstream.tv.ui.theme.ambientBackground

/**
 * Subtitle look editor (owner request 2026-08-30: "a subtitle size and
 * background/color editor"). Same shape as [PosterSizeScreen] — options on the
 * left, a live picture on the right — because the owner already learned that
 * pattern and this setting is even harder to imagine from words alone.
 *
 * The preview follows FOCUS, not the saved value, so moving over "Extra large"
 * shows extra-large subtitles before committing; OK saves and applies live to
 * the next (and any currently playing) video. Everything is one screen rather
 * than three dialogs: size, colour and backdrop only make sense judged
 * together against the same sample frame.
 *
 * Lives in HOW THINGS LOOK, NOT behind Expert mode — unreadable subtitles are
 * an everyday accessibility problem for the people these boxes are for, not a
 * technical knob (§10 elder-friendly).
 */
@Composable
fun SubtitleStyleScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val saved by viewModel.subtitleStyle.collectAsStateWithLifecycle()

    // What the sample frame draws: the saved style with the focused option
    // swapped in. Null = nothing focused yet, so the preview shows the truth.
    var previewSize by remember { mutableStateOf<SubtitleTextSize?>(null) }
    var previewColor by remember { mutableStateOf<SubtitleTextColor?>(null) }
    var previewBackdrop by remember { mutableStateOf<SubtitleBackdrop?>(null) }

    val preview = SubtitleStyle(
        size = previewSize ?: saved.size,
        color = previewColor ?: saved.color,
        backdrop = previewBackdrop ?: saved.backdrop,
    )

    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ambientBackground(AmbientSection.SETTINGS)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton(onBack)
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }
        Text(
            text = "How subtitles look while something is playing. The picture on " +
                "the right updates as you move around — press OK to keep a choice.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .width(400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                GroupLabel("Size")
                SubtitleTextSize.entries.forEach { size ->
                    SettingsPickerRow(
                        label = size.label,
                        selected = size == saved.size,
                        onClick = { viewModel.setSubtitleTextSize(size) },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) previewSize = size }
                            .then(
                                if (size == saved.size) Modifier.focusRequester(selectedFocus)
                                else Modifier
                            ),
                    )
                }

                Spacer(Modifier.height(6.dp))
                GroupLabel("Colour")
                SubtitleTextColor.entries.forEach { color ->
                    SettingsPickerRow(
                        label = color.label,
                        selected = color == saved.color,
                        onClick = { viewModel.setSubtitleTextColor(color) },
                        modifier = Modifier.onFocusChanged {
                            if (it.isFocused) previewColor = color
                        },
                    )
                }

                Spacer(Modifier.height(6.dp))
                GroupLabel("Behind the words")
                SubtitleBackdrop.entries.forEach { backdrop ->
                    SettingsPickerRow(
                        label = backdrop.label,
                        selected = backdrop == saved.backdrop,
                        onClick = { viewModel.setSubtitleBackdrop(backdrop) },
                        modifier = Modifier.onFocusChanged {
                            if (it.isFocused) previewBackdrop = backdrop
                        },
                    )
                }
            }

            SubtitlePreview(
                style = preview,
                matchesSaved = preview == saved,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MutedText,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

/**
 * A stand-in for a video frame with a subtitle on it. The dark slab stands in
 * for the picture — a real thumbnail would be a different brightness on every
 * show, and the point here is legibility, not decoration.
 *
 * Preview sizing mirrors the player's own rule (a fraction of the surface
 * height) so what you see is what plays: the sample box is a fixed height and
 * the text scales by the same fraction media3 will use on the real video.
 */
@Composable
private fun SubtitlePreview(
    style: SubtitleStyle,
    matchesSaved: Boolean,
    modifier: Modifier = Modifier,
) {
    val frameHeight = 300.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .background(Color(0x59000000), RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(frameHeight)
                .background(Color(0xFF1B1B24), RoundedCornerShape(12.dp))
                .padding(bottom = 18.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val fontSize = (frameHeight.value * style.size.fractionOfHeight).sp
            val textColor = Color(style.color.argb)
            val backdropColor = Color(style.backdrop.backgroundArgb)
            val edgeColor = Color.Black

            Text(
                text = "This is what a subtitle will look like.",
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = fontSize,
                    color = textColor,
                    // Compose has no "outline" text mode, so the two edge
                    // styles are both drawn as a shadow here — the real player
                    // draws a true outline. Close enough to judge legibility,
                    // which is all this picture is for.
                    shadow = when (style.backdrop.edge) {
                        SubtitleEdge.OUTLINE -> androidx.compose.ui.graphics.Shadow(
                            color = edgeColor,
                            blurRadius = 2f,
                        )
                        SubtitleEdge.DROP_SHADOW -> androidx.compose.ui.graphics.Shadow(
                            color = edgeColor,
                            offset = androidx.compose.ui.geometry.Offset(3f, 3f),
                            blurRadius = 6f,
                        )
                        SubtitleEdge.NONE -> null
                    },
                ),
                modifier = Modifier.background(backdropColor, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = if (matchesSaved) {
                "This is what you're using now."
            } else {
                "Press OK on a highlighted option to keep it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
        )
    }
}
