package dev.openstream.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.Hairline
import dev.openstream.tv.ui.theme.SurfaceCard
import dev.openstream.tv.ui.theme.SurfaceCardFocused

/**
 * THE one popup face (owner backlog 2026-07-26: "one consistent UI style
 * across next-episode popup / Next Episode button / all dialogs"). Every
 * dialog, player panel and Up Next card wears [PanelFill]+[PanelShape] and
 * acts through [PanelButton] pills, so popups read as one family — the same
 * language as the owner's Round-17 player mockup and the DECISIONS-#29
 * surfaces, instead of each screen inventing its own dark box and the harsh
 * white-invert of a bare TV-Material Button.
 *
 * Extending: new popup → wrap content in [DialogPanel]; new action →
 * [PanelButton], `emphasized = true` on the ONE recommended action (the one
 * that should also hold initial focus).
 */
val PanelFill = Color(0xF0181822)
val PanelShape = RoundedCornerShape(16.dp)

/** Standard dialog body: the shared face + 28dp padding + 14dp row rhythm. */
@Composable
fun DialogPanel(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
            .background(PanelFill, PanelShape)
            .padding(28.dp),
        content = content,
    )
}

/**
 * A focusable action pill for dialogs/panels, in the app's surface language:
 * calm card fill with an accent ring on focus — never a white invert.
 * [emphasized] marks the recommended action with a solid accent fill (the
 * same cue as the Up Next card's "Play now" and a selected OptionRow).
 */
@Composable
fun PanelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = if (emphasized) {
            ClickableSurfaceDefaults.colors(
                containerColor = Accent.copy(alpha = 0.85f),
                contentColor = Color(0xFF0E0E16),
                focusedContainerColor = Accent,
                focusedContentColor = Color(0xFF0E0E16),
                pressedContainerColor = Accent,
                pressedContentColor = Color(0xFF0E0E16),
            )
        } else {
            ClickableSurfaceDefaults.colors(
                containerColor = SurfaceCard,
                contentColor = Color.White,
                focusedContainerColor = SurfaceCardFocused,
                focusedContentColor = Color.White,
                pressedContainerColor = SurfaceCardFocused,
                pressedContentColor = Color.White,
            )
        },
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(1.dp, if (emphasized) Accent.copy(alpha = 0.85f) else Hairline),
                shape = shape,
            ),
            // Emphasized keeps its accent fill on focus, so the ring flips
            // to white — the same compose rule as focused+selected pills.
            focusedBorder = Border(
                BorderStroke(2.dp, if (emphasized) Color.White else Accent),
                shape = shape,
            ),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
