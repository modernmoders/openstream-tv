package dev.openstream.tv.data

import dev.openstream.tv.addon.CatalogRepository.CatalogRef
import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Manifest
import dev.openstream.tv.addon.ManifestCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure row-customization rules (Phase 4 row manager): user order first, new
 * rows appended in addon order, stale keys ignored, renames with blank
 * fallback, hidden rows flagged but never dropped here (the manager needs
 * them; Home filters).
 */
class HomeRowPrefsTest {

    private fun ref(id: String) = CatalogRef(
        addon = InstalledAddon(
            manifestUrl = "http://addon.example/manifest.json",
            manifest = Manifest(id = "test", name = "Test", version = "1.0"),
            enabled = true,
            sortOrder = 0,
        ),
        catalog = ManifestCatalog(type = "movie", id = id, name = "Row $id"),
    )

    private val refs = listOf(ref("a"), ref("b"), ref("c"))

    private fun keys(rows: List<HomeRow>) = rows.map { it.ref.catalog.id }

    @Test
    fun `default prefs keep addon order, nothing hidden, own titles`() {
        val rows = HomeRowPrefs().applyTo(refs)
        assertEquals(listOf("a", "b", "c"), keys(rows))
        assertEquals(listOf("Row a", "Row b", "Row c"), rows.map { it.title })
        assertEquals(listOf(false, false, false), rows.map { it.hidden })
    }

    @Test
    fun `user order comes first, unlisted rows follow in addon order`() {
        val prefs = HomeRowPrefs(order = listOf(ref("c").key))
        assertEquals(listOf("c", "a", "b"), keys(prefs.applyTo(refs)))
    }

    @Test
    fun `stale keys in order are ignored`() {
        val prefs = HomeRowPrefs(order = listOf("gone|movie|x", ref("b").key))
        assertEquals(listOf("b", "a", "c"), keys(prefs.applyTo(refs)))
    }

    @Test
    fun `rename replaces the title, blank rename falls back to the catalog name`() {
        val prefs = HomeRowPrefs(
            renames = mapOf(ref("a").key to "Films", ref("b").key to "  "),
        )
        assertEquals(listOf("Films", "Row b", "Row c"), prefs.applyTo(refs).map { it.title })
    }

    @Test
    fun `hidden rows stay in the list but are flagged`() {
        val prefs = HomeRowPrefs(hidden = setOf(ref("b").key))
        val rows = prefs.applyTo(refs)
        assertEquals(listOf("a", "b", "c"), keys(rows))
        assertEquals(listOf(false, true, false), rows.map { it.hidden })
    }
}
