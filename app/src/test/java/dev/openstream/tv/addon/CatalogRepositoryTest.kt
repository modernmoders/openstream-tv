package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogRepositoryTest {

    private lateinit var server: MockAddonServer
    private lateinit var repository: CatalogRepository

    private val manifest =
        AddonJson.decodeFromString(Manifest.serializer(), Fixtures.load("manifest_full"))

    private fun installed(url: String, enabled: Boolean = true, order: Int = 0) =
        InstalledAddon(url, manifest, enabled, order)

    @Before
    fun setUp() {
        server = MockAddonServer()
        repository = CatalogRepository(
            OkHttpAddonClient(
                OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
            )
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `refs skip disabled addons and non-browsable catalogs`() {
        val refs = repository.catalogRefs(
            listOf(
                installed("https://a.example/manifest.json", order = 0),
                installed("https://b.example/manifest.json", enabled = false, order = 1),
            )
        )
        // manifest_full's catalogs: "top" (browsable), "bygenre"/"sbygenre"
        // (require a genre), "searchonly"/"legacy" (require search).
        // Only "top" is a browsable feed, and only for the enabled addon.
        assertEquals(1, refs.size)
        assertEquals("top", refs.single().catalog.id)
        assertEquals("https://a.example/manifest.json", refs.single().addon.manifestUrl)
    }

    @Test
    fun `discoverRefs add genre-required catalogs but never search-only ones`() {
        val refs = repository.discoverRefs(
            listOf(installed("https://a.example/manifest.json"))
        )
        // Browsable "top" plus the two genre-required catalogs, manifest
        // order; "searchonly"/"legacy" need free-text input and stay out.
        assertEquals(listOf("top", "bygenre", "sbygenre"), refs.map { it.catalog.id })
    }

    @Test
    fun `refs preserve addon order across multiple addons`() {
        val refs = repository.catalogRefs(
            listOf(
                installed("https://b.example/manifest.json", order = 0),
                installed("https://a.example/manifest.json", order = 1),
            )
        )
        assertEquals(
            listOf("https://b.example/manifest.json", "https://a.example/manifest.json"),
            refs.map { it.addon.manifestUrl },
        )
    }

    @Test
    fun `fetch returns usable metas from the catalog endpoint`() = runTest(timeout = 60.seconds) {
        server.route("/catalog/movie/top.json", Fixtures.load("catalog_mixed"))
        server.start()
        val addon = installed(server.url("/manifest.json"))
        val ref = CatalogRepository.CatalogRef(addon, addon.manifest.catalogs.first())

        val items = repository.fetch(ref).getOrThrow()

        assertEquals(5, items.size) // catalog_mixed is all-usable incl. channel/tv
    }

    @Test
    fun `fetch failure surfaces as Result failure for the row chip`() = runTest(timeout = 60.seconds) {
        server.start() // 404 everywhere
        val addon = installed(server.url("/manifest.json"))
        val ref = CatalogRepository.CatalogRef(addon, addon.manifest.catalogs.first())

        assertTrue(repository.fetch(ref).isFailure)
    }
}
