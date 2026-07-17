package dev.openstream.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.MutedText

/** One top-level section of the app. [icon] selects a drawn glyph, not a font
 *  character — the boxes' fonts render unicode symbols/emoji inconsistently
 *  (the search mic was already swapped from an emoji to a vector). */
data class NavDestination(val route: String, val label: String, val icon: RailIconKind)

enum class RailIconKind { HOME, DISCOVER, SEARCH, SETTINGS }

private val COLLAPSED_WIDTH = 78.dp
private val EXPANDED_WIDTH = 210.dp

/**
 * Persistent left navigation rail for the top-level sections (owner 2026-07-10:
 * "if you're on Home and go to Discover, it would be cool to still have the
 * Home/Discover/Search/Settings buttons on-screen").
 *
 * It replaces the per-screen Back button on those sections: the sections are
 * siblings, not a stack, so LEFT from the content always lands here and the
 * current one stays highlighted. Collapsed to icons until the rail takes focus,
 * then it expands to show labels — the Google-TV pattern, so poster rows keep
 * their width while you're browsing.
 */
@Composable
fun NavRail(
    currentRoute: String?,
    destinations: List<NavDestination>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Attached to the CURRENT section's item, so BACK-from-content can land
     * the selector on "where you are" (owner 2026-07-11: deep in a grid,
     * BACK should open the rail instead of a long LEFT-crawl).
     */
    sectionFocus: FocusRequester? = null,
    /** Reports whether ANY rail item holds focus (drives the BACK handlers). */
    onFocusWithinChanged: ((Boolean) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (focused) EXPANDED_WIDTH else COLLAPSED_WIDTH,
        animationSpec = tween(180),
        label = "rail-width",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            // hasFocus (not isFocused): expand while ANY rail item holds focus.
            .onFocusChanged {
                focused = it.hasFocus
                onFocusWithinChanged?.invoke(it.hasFocus)
            }
            .focusGroup()
            .background(Color(0xCC0E0E16))
            .padding(vertical = 28.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        destinations.forEach { destination ->
            val selected = currentRoute == destination.route
            RailItem(
                destination = destination,
                selected = selected,
                expanded = focused,
                onClick = { onSelect(destination.route) },
                modifier = if (selected && sectionFocus != null) {
                    Modifier.focusRequester(sectionFocus)
                } else Modifier,
            )
        }
    }
}

@Composable
private fun RailItem(
    destination: NavDestination,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Round 14 (owner 2026-07-11): the item you're HOVERING must outshine the
    // current-section highlight — focused = solid accent pill with dark glyph,
    // selected-but-unfocused = the quiet tinted pill it always was.
    var itemFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            // The SELECTED section stays visibly lit even when focus is in the
            // content — that's what tells you where you are.
            containerColor = if (selected) Accent.copy(alpha = 0.22f) else Color.Transparent,
            focusedContainerColor = Accent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { itemFocused = it.isFocused },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val tint = when {
                itemFocused -> RailInk
                selected -> Accent
                else -> Color.White
            }
            RailIcon(destination.icon, tint)
            if (expanded) {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (itemFocused) RailInk else if (selected) Accent else MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Glyph/label ink on the focused accent pill — the rail's own background. */
private val RailInk = Color(0xFF0E0E16)

/**
 * The rail's magnifying-glass, public so text that tells the user to "select
 * Search from the left panel" can draw the identical glyph inline instead of
 * an emoji that looks nothing like the actual button.
 */
@Composable
fun SearchGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        drawCircle(
            tint,
            radius = w * 0.30f,
            center = Offset(w * 0.43f, w * 0.43f),
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round),
        )
        drawLine(
            tint,
            Offset(w * 0.66f, w * 0.66f),
            Offset(w * 0.88f, w * 0.88f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round,
        )
    }
}

/** Simple drawn glyphs — no font/emoji dependency (see [NavDestination.icon]). */
@Composable
private fun RailIcon(kind: RailIconKind, tint: Color) {
    // Settings shares the one gear glyph used on the Discover View pill and
    // the Home header, so "settings" looks the same everywhere (round 14 #10).
    if (kind == RailIconKind.SETTINGS) {
        GearIcon(tint, Modifier.size(24.dp))
        return
    }
    // Search shares its glyph with the Search screen's voice caption, which
    // points the user at this exact rail button (round 22 #7).
    if (kind == RailIconKind.SEARCH) {
        SearchGlyph(tint, Modifier.size(24.dp))
        return
    }
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        when (kind) {
            RailIconKind.HOME -> {
                // roof + body
                val roof = Path().apply {
                    moveTo(w * 0.10f, w * 0.48f)
                    lineTo(w * 0.50f, w * 0.12f)
                    lineTo(w * 0.90f, w * 0.48f)
                }
                drawPath(roof, tint, style = stroke)
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.24f, w * 0.48f),
                    size = Size(w * 0.52f, w * 0.40f),
                    style = stroke,
                )
            }
            RailIconKind.DISCOVER -> {
                // compass diamond inside a ring
                drawCircle(tint, radius = w * 0.42f, style = stroke)
                val needle = Path().apply {
                    moveTo(w * 0.50f, w * 0.24f)
                    lineTo(w * 0.66f, w * 0.50f)
                    lineTo(w * 0.50f, w * 0.76f)
                    lineTo(w * 0.34f, w * 0.50f)
                    close()
                }
                drawPath(needle, tint, style = stroke)
            }
            // Handled above by the shared GearIcon/SearchGlyph; the early
            // returns mean these arms never draw, they just keep the when
            // exhaustive.
            RailIconKind.SEARCH, RailIconKind.SETTINGS -> Unit
        }
    }
}
