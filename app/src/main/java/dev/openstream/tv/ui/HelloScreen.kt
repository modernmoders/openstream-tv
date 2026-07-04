package dev.openstream.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

/**
 * Phase 0 boot check: a screen with one focusable element, proving that
 * Compose + tv-material render and D-pad focus works on a TV device.
 *
 * Padding follows the overscan safe margins from MASTER_PLAN §5
 * (48dp horizontal / 27dp vertical).
 */
@Composable
fun HelloScreen() {
    // Dark theme only in v1 (MASTER_PLAN §5.8).
    MaterialTheme(colorScheme = darkColorScheme()) {
        var presses by remember { mutableIntStateOf(0) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101018))
                .padding(horizontal = 48.dp, vertical = 27.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = "OpenStream TV",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                )
                Text(
                    text = "Phase 0 boot check — press OK on the button below",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB0B0C0),
                )
                // tv-material Button scales up when focused, which is the
                // visible proof that D-pad focus is alive.
                Button(onClick = { presses++ }) {
                    Text(text = if (presses == 0) "Hello, couch" else "OK pressed ×$presses")
                }
            }
        }
    }
}
