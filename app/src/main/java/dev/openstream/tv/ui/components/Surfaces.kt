package dev.openstream.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
 * The shared refined interaction surfaces (owner UX pass, DECISIONS #29):
 * one calm resting fill that lifts to an accent tint + accent border on focus,
 * with TV Material's built-in (non-stuttering) scale — never the harsh
 * white-invert of a bare Button. Used for nav/filter pills and list rows so
 * every menu reads in the same language.
 */

/** A rounded nav/filter pill (Home header, Discover filter bar). */
@Composable
fun SurfacePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceCard,
            focusedContainerColor = SurfaceCardFocused,
            pressedContainerColor = SurfaceCardFocused,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
        )
    }
}

/**
 * A full-width refined list row. Callers supply the inner content (a title
 * column, trailing controls, …) inside a centered [Row]. Supports a long-press
 * (e.g. the stream list's "Play with…" override).
 */
@Composable
fun SurfaceRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.012f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceCard,
            focusedContainerColor = SurfaceCardFocused,
            pressedContainerColor = SurfaceCardFocused,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            content = content,
        )
    }
}
