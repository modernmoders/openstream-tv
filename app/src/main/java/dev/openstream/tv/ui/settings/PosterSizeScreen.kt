package dev.openstream.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.data.MAX_POSTER_COLUMNS
import dev.openstream.tv.data.MIN_POSTER_COLUMNS
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.AmbientSection
import dev.openstream.tv.ui.theme.ambientBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Poster size as its own screen (owner round 20 #7: confusing settings get a
 * screen "like Home screen rows" WITH a picture of what they change). The
 * right half is a live miniature of the home screen: move the selector over
 * an option and the little poster wall re-lays itself out at that density —
 * you SEE the difference before pressing OK. Picking persists immediately
 * (same live-apply pattern as Home's View dialog); BACK returns.
 */
@Composable
fun PosterSizeScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val current by viewModel.posterColumns.collectAsStateWithLifecycle()
    // What the preview draws: follows the FOCUSED option, so browsing the
    // list previews without committing. Starts on the saved value.
    var preview by remember { mutableIntStateOf(0) }
    val previewColumns = if (preview == 0) current else preview

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
                text = "Poster size",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }
        Text(
            text = "How many posters fit in one row — on Home, Discover and Search. " +
                "The picture on the right shows how it will look.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            // Options on the left. Focus previews, OK saves (applies live).
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .width(380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                for (columns in MIN_POSTER_COLUMNS..MAX_POSTER_COLUMNS) {
                    val hint = when (columns) {
                        MIN_POSTER_COLUMNS -> " · biggest posters"
                        MAX_POSTER_COLUMNS -> " · most on screen"
                        else -> ""
                    }
                    SettingsPickerRow(
                        label = "$columns per row$hint",
                        selected = columns == current,
                        onClick = { viewModel.setPosterColumns(columns) },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) preview = columns }
                            .then(
                                if (columns == current) Modifier.focusRequester(selectedFocus)
                                else Modifier
                            ),
                    )
                }
            }

            // The live miniature. Weighted boxes: each row lays out exactly
            // previewColumns tiles, so the tile size falls out of the math —
            // no measurements to keep in sync with the real grid.
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0x59000000), RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "$previewColumns per row",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (previewColumns == current) MutedText else Accent,
                )
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(previewColumns) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(2f / 3f)
                                    .background(Color(0xFF23232F), RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
                Text(
                    text = if (previewColumns == current) {
                        "This is your current size."
                    } else {
                        "Press OK to use this size."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                )
            }
        }
    }
}
