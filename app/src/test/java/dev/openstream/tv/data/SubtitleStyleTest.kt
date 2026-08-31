package dev.openstream.tv.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The subtitle look (owner 2026-08-30). These lock the two things that would
 * silently break a box: the DEFAULT must keep looking exactly like it did
 * before this feature existed, and every option must actually be distinct
 * (a colour that equals another colour is a dead menu entry).
 */
class SubtitleStyleTest {

    @Test
    fun `default matches media3's own look so an untouched box is unchanged`() {
        val default = SubtitleStyle()
        assertEquals(SubtitleTextSize.NORMAL, default.size)
        assertEquals(SubtitleTextColor.WHITE, default.color)
        assertEquals(SubtitleBackdrop.OUTLINE, default.backdrop)
        assertTrue(default.isDefault)
        // media3's SubtitleView.DEFAULT_TEXT_SIZE_FRACTION — if this ever
        // drifts, boxes that never opened the screen would change size.
        assertEquals(0.0533f, SubtitleTextSize.NORMAL.fractionOfHeight, 0.0001f)
        // White, fully opaque, no box behind it.
        assertEquals(0xFFFFFFFF.toInt(), SubtitleTextColor.WHITE.argb)
        assertEquals(SUBTITLE_TRANSPARENT, SubtitleBackdrop.OUTLINE.backgroundArgb)
    }

    @Test
    fun `isDefault is false once anything is changed`() {
        assertFalse(SubtitleStyle(size = SubtitleTextSize.HUGE).isDefault)
        assertFalse(SubtitleStyle(color = SubtitleTextColor.YELLOW).isDefault)
        assertFalse(SubtitleStyle(backdrop = SubtitleBackdrop.SOLID_BOX).isDefault)
    }

    @Test
    fun `sizes are ordered smallest to largest and all distinct`() {
        val fractions = SubtitleTextSize.entries.map { it.fractionOfHeight }
        assertEquals(fractions.sorted(), fractions)
        assertEquals(fractions.size, fractions.toSet().size)
        // Every size must be legible-but-sane: never zero, never half the screen.
        assertTrue(fractions.all { it > 0f && it < 0.2f })
    }

    @Test
    fun `every colour and backdrop is distinct and labelled`() {
        val colors = SubtitleTextColor.entries
        assertEquals(colors.size, colors.map { it.argb }.toSet().size)
        assertTrue(colors.all { it.label.isNotBlank() })
        // Colours must be fully opaque — a translucent subtitle is unreadable.
        assertTrue(colors.all { (it.argb ushr 24) == 0xFF })

        val backdrops = SubtitleBackdrop.entries
        assertEquals(backdrops.size, backdrops.map { it.label }.toSet().size)
        // A box must actually paint something; an edge style must not.
        assertNotEquals(SUBTITLE_TRANSPARENT, SubtitleBackdrop.DIM_BOX.backgroundArgb)
        assertNotEquals(SUBTITLE_TRANSPARENT, SubtitleBackdrop.SOLID_BOX.backgroundArgb)
        assertEquals(SUBTITLE_TRANSPARENT, SubtitleBackdrop.NONE.backgroundArgb)
        assertEquals(SubtitleEdge.NONE, SubtitleBackdrop.NONE.edge)
        assertEquals(SubtitleEdge.OUTLINE, SubtitleBackdrop.OUTLINE.edge)
        assertEquals(SubtitleEdge.DROP_SHADOW, SubtitleBackdrop.SHADOW.edge)
    }

    @Test
    fun `prefs round-trip each part independently`() = runTest {
        val prefs = FakeViewPrefs()
        assertEquals(SubtitleStyle(), prefs.subtitleStyle.first())

        prefs.setSubtitleTextSize(SubtitleTextSize.LARGE)
        prefs.setSubtitleTextColor(SubtitleTextColor.YELLOW)
        prefs.setSubtitleBackdrop(SubtitleBackdrop.SOLID_BOX)

        assertEquals(
            SubtitleStyle(SubtitleTextSize.LARGE, SubtitleTextColor.YELLOW, SubtitleBackdrop.SOLID_BOX),
            prefs.subtitleStyle.first(),
        )
    }

    @Test
    fun `reset to defaults puts subtitles back`() = runTest {
        val prefs = FakeViewPrefs()
        prefs.setSubtitleTextSize(SubtitleTextSize.HUGE)
        prefs.resetToDefaults()
        assertEquals(SubtitleStyle(), prefs.subtitleStyle.first())
    }
}
