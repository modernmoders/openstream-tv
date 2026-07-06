package dev.openstream.tv.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.openstream.tv.ui.components.LoadingMessage
import dev.openstream.tv.ui.components.TvTextField
import dev.openstream.tv.ui.connect.ConnectViewModel.UiState
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * The Welcome Guide + one-step setup screen (owner directive 2026-07-06).
 * Shown on first launch (nothing installed yet) and from Settings →
 * "Connect this TV". Everything on it is written for the least technical
 * person in the family: no URLs, no "addons", no "manifests" — a name in,
 * a working TV out.
 */
@Composable
fun ConnectScreen(
    /** Leave for the home screen — used by Done, "maybe later", and Back. */
    onExit: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    val primaryFocus = remember { FocusRequester() }

    // Land the D-pad on the natural next thing for every step (§5.4).
    LaunchedEffect(state) {
        runCatching { primaryFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (val s = state) {
                is UiState.AskName -> AskNameStep(
                    brand = viewModel.brand,
                    name = name,
                    error = s.error,
                    onNameChange = { name = it },
                    onSubmit = { viewModel.submitName(name) },
                    onLater = onExit,
                    fieldFocus = primaryFocus,
                )
                is UiState.Busy -> LoadingMessage(
                    text = s.message,
                    horizontalPadding = 0.dp,
                    style = MaterialTheme.typography.titleLarge,
                )
                is UiState.WhichOne -> WhichOneStep(
                    choices = s.choices,
                    onChoose = viewModel::choose,
                    onBack = viewModel::startOver,
                    firstFocus = primaryFocus,
                )
                is UiState.Ready -> ReadyStep(
                    state = s,
                    onConfirm = viewModel::confirm,
                    onNotMe = viewModel::startOver,
                    confirmFocus = primaryFocus,
                )
                is UiState.Done -> DoneStep(
                    state = s,
                    onStart = onExit,
                    startFocus = primaryFocus,
                )
            }
        }
    }
}

@Composable
private fun AskNameStep(
    brand: String,
    name: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onLater: () -> Unit,
    fieldFocus: FocusRequester,
) {
    Text(
        text = "Welcome to $brand!",
        style = MaterialTheme.typography.headlineLarge,
        color = Color.White,
    )
    Text(
        text = "Let's get this TV set up. It takes about a minute, and you only have to do it once.",
        style = MaterialTheme.typography.bodyLarge,
        color = MutedText,
        textAlign = TextAlign.Center,
    )

    // The whole guide, honestly: three steps, two of which are ours.
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        GuideStep(1, "Type your name")
        GuideStep(2, "We set everything up")
        GuideStep(3, "Start watching")
    }

    Text(
        text = "What's your name? First name and last initial is perfect — like \"jody m\".",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    TvTextField(
        value = name,
        onValueChange = onNameChange,
        onSubmit = onSubmit,
        focusRequester = fieldFocus,
    )
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFFFB86B), // warm, not alarming — it's just a retry
            textAlign = TextAlign.Center,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onSubmit) { Text("That's me — continue") }
        OutlinedButton(onClick = onLater) { Text("Maybe later") }
    }
}

/** One circled number + caption — the guide's whole visual language. */
@Composable
private fun GuideStep(number: Int, caption: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(150.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFF2A2A3E), CircleShape),
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WhichOneStep(
    choices: List<String>,
    onChoose: (String) -> Unit,
    onBack: () -> Unit,
    firstFocus: FocusRequester,
) {
    Text(
        text = "A few people go by that name — which one are you?",
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    choices.forEachIndexed { index, choice ->
        Button(
            onClick = { onChoose(choice) },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
        ) { Text(choice) }
    }
    OutlinedButton(onClick = onBack) { Text("← Let me retype it") }
}

@Composable
private fun ReadyStep(
    state: UiState.Ready,
    onConfirm: () -> Unit,
    onNotMe: () -> Unit,
    confirmFocus: FocusRequester,
) {
    val quiet = state.plan.entries.count { !it.ok }
    Text(
        text = "Hi ${state.displayName}!",
        style = MaterialTheme.typography.headlineLarge,
        color = Color.White,
    )
    Text(
        text = "Everything's ready — here's what you're getting:",
        style = MaterialTheme.typography.bodyLarge,
        color = MutedText,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Friendly names only — no versions, no URLs (they carry tokens, and
        // this is the one screen guaranteed to be seen by everyone).
        state.plan.entries.forEach { entry ->
            Text(
                text = if (entry.ok) "✓  ${entry.displayName}" else "•  ${entry.displayName}",
                color = if (entry.ok) MutedText else MutedText.copy(alpha = 0.6f),
            )
        }
    }
    if (quiet > 0) {
        Text(
            text = if (quiet == 1) {
                "One of them is napping right now — it'll join on its own later."
            } else {
                "$quiet of them are napping right now — they'll join on their own later."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onConfirm,
            modifier = Modifier.focusRequester(confirmFocus),
        ) { Text("Finish setup") }
        OutlinedButton(onClick = onNotMe) { Text("That's not me") }
    }
}

@Composable
private fun DoneStep(
    state: UiState.Done,
    onStart: () -> Unit,
    startFocus: FocusRequester,
) {
    Text(
        text = "You're all set, ${state.displayName}!",
        style = MaterialTheme.typography.headlineLarge,
        color = Color.White,
    )
    Text(
        text = "This TV now keeps itself up to date on its own — you shouldn't ever need this screen again.",
        style = MaterialTheme.typography.bodyLarge,
        color = MutedText,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onStart,
        modifier = Modifier.focusRequester(startFocus),
    ) { Text("Start watching") }
}
