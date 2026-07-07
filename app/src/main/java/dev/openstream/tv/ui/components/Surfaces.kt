package dev.openstream.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.Hairline
import dev.openstream.tv.ui.theme.MutedText
import dev.openstream.tv.ui.theme.SurfaceCard
import dev.openstream.tv.ui.theme.SurfaceCardFocused

/**
 * The shared refined interaction surfaces (owner UX pass, DECISIONS #29):
 * one calm resting fill that lifts to an accent tint + accent border on focus,
 * with TV Material's built-in (non-stuttering) scale — never the harsh
 * white-invert of a bare Button. Used for nav/filter pills and list rows so
 * every menu reads in the same language.
 *
 * SELECTED vs FOCUSED (DECISIONS #3x follow-up, owner round 10: "today the
 * selected chip and the focused chip look nearly identical"): [SurfacePill]
 * and [OptionRow] use TV Material's `selected`-aware Surface overload so the
 * two states get independently-styled containers — a solid [Accent] fill for
 * SELECTED (always visible, whether or not focus is currently there) vs the
 * quiet [SurfaceCardFocused] tint + accent RING for FOCUSED. The two compose
 * (selected AND focused) into a slightly deeper accent fill so neither cue
 * is lost when they coincide.
 */

/** Solid selected fill — visually distinct from the quiet focus tint at any distance. */
private val SelectedFill = Accent.copy(alpha = 0.85f)

/** Selected + focused at once: a touch deeper than resting-selected. */
private val SelectedFocusedFill = Accent

/**
 * A rounded nav/filter pill (Home header, Discover filter bar, season chips).
 * [selected] gives it a solid accent fill + a small trailing dot (e.g. the
 * current season) — deliberately a different look from the focus ring, which
 * a hollow accent border keeps for whichever pill has the cursor right now.
 */
@Composable
fun SurfacePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = SelectableSurfaceDefaults.shape(shape = shape),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = SurfaceCard,
            contentColor = Color.White,
            focusedContainerColor = SurfaceCardFocused,
            focusedContentColor = Color.White,
            pressedContainerColor = SurfaceCardFocused,
            pressedContentColor = Color.White,
            selectedContainerColor = SelectedFill,
            selectedContentColor = Color.White,
            focusedSelectedContainerColor = SelectedFocusedFill,
            focusedSelectedContentColor = Color.White,
        ),
        border = SelectableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
            selectedBorder = Border(BorderStroke(1.dp, SelectedFill), shape = shape),
            focusedSelectedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
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

/**
 * A refined picker/dialog option in the shared language. SELECTED gets a
 * solid accent fill (always visible, whether or not this row currently holds
 * focus) + trailing ✓; FOCUSED gets the quiet tint + accent ring — the two
 * cues are independent so a selected-but-unfocused row never gets mistaken
 * for a merely-focused one (owner round 10). Optional [sublabel] (e.g. a
 * catalog's addon).
 */
@Composable
fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = SelectableSurfaceDefaults.shape(shape = shape),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = SurfaceCard,
            contentColor = Color.White,
            focusedContainerColor = SurfaceCardFocused,
            focusedContentColor = Color.White,
            pressedContainerColor = SurfaceCardFocused,
            pressedContentColor = Color.White,
            selectedContainerColor = SelectedFill,
            selectedContentColor = Color.White,
            focusedSelectedContainerColor = SelectedFocusedFill,
            focusedSelectedContentColor = Color.White,
        ),
        border = SelectableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
            selectedBorder = Border(BorderStroke(1.dp, SelectedFill), shape = shape),
            focusedSelectedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) Color.White.copy(alpha = 0.8f) else MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                // White, not Accent: the row's own fill IS accent now, so an
                // accent tick would vanish into it.
                Text("✓", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}
