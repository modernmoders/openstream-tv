package dev.openstream.tv.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.MutedText

/**
 * The "an update is ready" prompt shown over whatever screen is up when the
 * launch check finds a newer build (owner 2026-07-11: boxes update themselves
 * once they leave the house). One OK on "Update now" downloads and hands off
 * to Android's own install confirmation; "Later" just closes it — the next
 * app launch will offer again. Wording is for the household, not for us.
 */
@Composable
fun UpdatePrompt(
    ui: UpdateViewModel.UpdateUi,
    brand: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showFor = ui is UpdateViewModel.UpdateUi.Available ||
        ui is UpdateViewModel.UpdateUi.Downloading ||
        ui is UpdateViewModel.UpdateUi.InstallFailed
    if (!showFor) return

    val updateFocus = remember { FocusRequester() }
    LaunchedEffect(ui) {
        if (ui is UpdateViewModel.UpdateUi.Available || ui is UpdateViewModel.UpdateUi.InstallFailed) {
            runCatching { updateFocus.requestFocus() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .width(460.dp)
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            when (ui) {
                is UpdateViewModel.UpdateUi.Available -> {
                    Text(
                        text = "$brand has an update",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "Version ${ui.versionName} is ready. Updating takes " +
                            "under a minute — Android will ask you to confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.focusRequester(updateFocus),
                        ) { Text("Update now") }
                        OutlinedButton(onClick = onDismiss) { Text("Later") }
                    }
                }
                UpdateViewModel.UpdateUi.Downloading -> {
                    Text(
                        text = "Getting the update…",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "Android will ask you to confirm in a moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )
                }
                UpdateViewModel.UpdateUi.InstallFailed -> {
                    Text(
                        text = "The update didn't finish",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "Nothing broke — you're still on the current version. " +
                            "You can try again now or later from Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.focusRequester(updateFocus),
                        ) { Text("Try again") }
                        OutlinedButton(onClick = onDismiss) { Text("Close") }
                    }
                }
                else -> Unit
            }
        }
    }
}
