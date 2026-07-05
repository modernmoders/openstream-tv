package dev.openstream.tv.player

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Track menu logic (owner request 2026-07-05): naming, duplicate handling,
 * unsupported filtering, and the subtitles-off state — all pure, no media3.
 */
class PlayerTracksTest {

    private lateinit var defaultLocale: Locale

    @Before
    fun pinLocale() {
        // displayLanguage output depends on the JVM's default locale.
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun raw(
        kind: TrackKind,
        groupIndex: Int,
        lang: String? = null,
        label: String? = null,
        channels: Int = -1,
        supported: Boolean = true,
        selected: Boolean = false,
    ) = RawTrack(kind, groupIndex, 0, lang, label, channels, supported, selected)

    // ---- trackDisplayName / displayLanguage ----

    @Test
    fun `language tags become translated names`() {
        assertEquals("English", trackDisplayName("en", null, -1, 1))
        assertEquals("French", trackDisplayName("fr", null, -1, 1))
        assertEquals("Spanish", trackDisplayName("es-419", null, -1, 1))
    }

    @Test
    fun `label is appended when it adds information`() {
        assertEquals("English · Commentary", trackDisplayName("en", "Commentary", -1, 1))
        // ...but not when it just repeats the language name
        assertEquals("English", trackDisplayName("en", "english", -1, 1))
    }

    @Test
    fun `surround layouts are annotated`() {
        assertEquals("English · 5.1", trackDisplayName("en", null, 6, 1))
        assertEquals("English · 7.1", trackDisplayName("en", null, 8, 1))
        // stereo/mono stay clean
        assertEquals("English", trackDisplayName("en", null, 2, 1))
    }

    @Test
    fun `nameless track falls back to its number`() {
        assertEquals("Track 3", trackDisplayName(null, null, -1, 3))
        assertEquals("Track 1", trackDisplayName("und", "", -1, 1))
    }

    @Test
    fun `free-text addon language labels pass through`() {
        // Addon subtitle "lang" fields are free text (SubtitleTrack.lang doc)
        assertEquals("English", displayLanguage("english"))
        assertNull(displayLanguage(null))
        assertNull(displayLanguage("und"))
    }

    // ---- buildTrackMenu ----

    @Test
    fun `menu splits kinds and filters unsupported tracks`() {
        val menu = buildTrackMenu(
            listOf(
                raw(TrackKind.AUDIO, 0, lang = "en", selected = true),
                raw(TrackKind.AUDIO, 1, lang = "fr", supported = false),
                raw(TrackKind.SUBTITLE, 2, lang = "en"),
            )
        )
        assertEquals(listOf("English"), menu.audio.map { it.label })
        assertEquals(listOf("English"), menu.subtitles.map { it.label })
        assertTrue(menu.audio.single().selected)
    }

    @Test
    fun `duplicate names get numbered so rows stay distinguishable`() {
        val menu = buildTrackMenu(
            listOf(
                raw(TrackKind.AUDIO, 0, lang = "en"),
                raw(TrackKind.AUDIO, 1, lang = "en"),
                raw(TrackKind.AUDIO, 2, lang = "en"),
            )
        )
        assertEquals(listOf("English", "English (2)", "English (3)"), menu.audio.map { it.label })
    }

    @Test
    fun `subtitlesOff reflects whether any text track is active`() {
        val off = buildTrackMenu(listOf(raw(TrackKind.SUBTITLE, 0, lang = "en")))
        assertTrue(off.subtitlesOff)

        val on = buildTrackMenu(listOf(raw(TrackKind.SUBTITLE, 0, lang = "en", selected = true)))
        assertFalse(on.subtitlesOff)
    }

    @Test
    fun `empty stream yields an empty menu with subtitles off`() {
        val menu = buildTrackMenu(emptyList())
        assertTrue(menu.audio.isEmpty())
        assertTrue(menu.subtitles.isEmpty())
        assertTrue(menu.subtitlesOff)
    }

    @Test
    fun `options keep the group and track indices selection needs`() {
        val menu = buildTrackMenu(
            listOf(RawTrack(TrackKind.AUDIO, 4, 2, "en", null, -1, true, false))
        )
        assertEquals(4, menu.audio.single().groupIndex)
        assertEquals(2, menu.audio.single().trackIndex)
    }
}
