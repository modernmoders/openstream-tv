package dev.openstream.tv.ui.connect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/** Warm success green for the finished state — the one accent on this screen. */
private val Accent = Color(0xFF3DDC97)

/**
 * The one-step setup screen (owner directive 2026-07-06, simplified round 2):
 * type your name, and the TV sets itself up — no guide, no accept screen, no
 * URLs. Shown on first launch and from Settings → "Connect this TV".
 *
 * Only the name step lifts toward the top (so the on-screen keyboard never
 * covers it); every other step is centered. Steps cross-fade with a gentle
 * lift, and the focus highlight is never clipped (`SizeTransform(clip = false)`).
 */
@Composable
fun ConnectScreen(
    /** Leave for the home screen — used by Done, skip, and Back. */
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

    // Only the name entry needs to dodge the keyboard; the rest sit centered.
    val liftToTop = state is UiState.AskName

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
        contentAlignment = if (liftToTop) Alignment.TopCenter else Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it.stepKey() },
            transitionSpec = {
                val enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 12 }
                (enter togetherWith fadeOut(tween(150))).using(SizeTransform(clip = false))
            },
            label = "connect-step",
            modifier = Modifier.padding(top = if (liftToTop) 56.dp else 0.dp),
        ) { s ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .padding(horizontal = 48.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (s) {
                    is UiState.AskName -> AskNameStep(
                        brand = viewModel.brand,
                        name = name,
                        error = s.error,
                        onNameChange = { name = it },
                        onSubmit = { viewModel.submitName(name) },
                        fieldFocus = primaryFocus,
                    )
                    is UiState.Busy -> LoadingMessage(
                        text = s.message,
                        horizontalPadding = 0.dp,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    is UiState.WhichOne -> WhichOneStep(
                        choices = s.choices,
                        onChoose = viewModel::choose,
                        onBack = viewModel::startOver,
                        firstFocus = primaryFocus,
                    )
                    is UiState.Done -> DoneStep(state = s, onExit = onExit)
                }
            }
        }
    }
}

/** Stable per-step key so cross-fades fire on step changes, not keystrokes. */
private fun UiState.stepKey(): Int = when (this) {
    is UiState.AskName -> 0
    is UiState.Busy -> 1
    is UiState.WhichOne -> 2
    is UiState.Done -> 3
}

@Composable
private fun AskNameStep(
    brand: String,
    name: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    fieldFocus: FocusRequester,
) {
    Text(
        text = "Welcome to $brand",
        style = MaterialTheme.typography.titleMedium,
        color = MutedText,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "What's your name?",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    TvTextField(
        value = name,
        onValueChange = onNameChange,
        onSubmit = onSubmit,
        focusRequester = fieldFocus,
    )
    AnimatedVisibility(visible = error != null) {
        Text(
            text = error.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFFFB86B), // warm, not alarming — it's just a retry
            textAlign = TextAlign.Center,
        )
    }
    // Submit only — no bypass. This is the FIRST thing a fresh box shows, and
    // a "Skip for now" button here used to leave the box installed with zero
    // addons (owner round 10: replace with a real Done/submit affordance, and
    // give DOWN-from-the-field somewhere focusable to land — same action as
    // the keyboard's Go/Done, just visible and discoverable without it).
    Button(onClick = onSubmit) { Text("Continue") }
}

@Composable
private fun WhichOneStep(
    choices: List<String>,
    onChoose: (String) -> Unit,
    onBack: () -> Unit,
    firstFocus: FocusRequester,
) {
    Text(
        text = "Which one are you?",
        style = MaterialTheme.typography.headlineMedium,
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
    OutlinedButton(onClick = onBack) { Text("Type it again") }
}

@Composable
private fun DoneStep(
    state: UiState.Done,
    onExit: () -> Unit,
) {
    // A message that fades: show the win for a beat, then head Home on its own.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1700)
        onExit()
    }
    val firstName = state.displayName.trim().substringBefore(' ').ifBlank { "there" }
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF0A2A1E))
    }
    Text(
        text = "You're all set, $firstName!",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Your shows are ready.",
        style = MaterialTheme.typography.bodyLarge,
        color = MutedText,
        textAlign = TextAlign.Center,
    )
}
