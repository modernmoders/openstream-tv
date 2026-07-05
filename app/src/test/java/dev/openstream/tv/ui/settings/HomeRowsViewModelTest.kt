package dev.openstream.tv.ui.settings

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.CatalogRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.data.FakeHomeRowPrefsStore
import dev.openstream.tv.data.HomeRowPrefs
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Row-manager behavior: the list mirrors Home's customization (hidden rows
 * included), a move pins the FULL order, edits write through the store.
 */
class HomeRowsViewModelTest {

    private lateinit var server: MockAddonServer
    private lateinit var addonRepository: AddonRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var store: FakeHomeRowPrefsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockAddonServer()
        val client = OkHttpAddonClient(
            OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        )
        addonRepository = AddonRepository(client, FakeInstalledAddonDao())
        catalogRepository = CatalogRepository(client)
        store = FakeHomeRowPrefsStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    /** Two one-catalog addons under path prefixes on one mock server. */
    private suspend fun installTwoAddons() {
        for (prefix in listOf("/a", "/b")) {
            server.route("$prefix/manifest.json", Fixtures.load("manifest_full"))
        }
        server.start()
        addonRepository.install(server.url("/a/manifest.json")).getOrThrow()
        addonRepository.install(server.url("/b/manifest.json")).getOrThrow()
    }

    private fun keyFor(prefix: String) = "${server.url("$prefix/manifest.json")}|movie|top"

    private fun viewModel() = HomeRowsViewModel(addonRepository, catalogRepository, store)

    @Test
    fun `lists rows in customized order including hidden ones`() = runTest(timeout = 60.seconds) {
        installTwoAddons()
        store.state.value = HomeRowPrefs(
            order = listOf(keyFor("/b")),
            hidden = setOf(keyFor("/a")),
        )

        val rows = viewModel().rows.first { it?.size == 2 }!!

        assertEquals(listOf(keyFor("/b"), keyFor("/a")), rows.map { it.ref.key })
        assertEquals(listOf(false, true), rows.map { it.hidden })
    }

    @Test
    fun `move down pins the full order in the store`() = runTest(timeout = 60.seconds) {
        installTwoAddons()
        val viewModel = viewModel()
        viewModel.rows.first { it?.size == 2 }

        viewModel.move(keyFor("/a"), +1)

        assertEquals(listOf(keyFor("/b"), keyFor("/a")), store.state.value.order)
        // And the visible list follows the new order.
        assertEquals(
            listOf(keyFor("/b"), keyFor("/a")),
            viewModel.rows.first { it?.first()?.ref?.key == keyFor("/b") }!!.map { it.ref.key },
        )
    }

    @Test
    fun `move past either end is a no-op`() = runTest(timeout = 60.seconds) {
        installTwoAddons()
        val viewModel = viewModel()
        viewModel.rows.first { it?.size == 2 }

        viewModel.move(keyFor("/a"), -1)
        viewModel.move(keyFor("/b"), +1)

        assertEquals(emptyList<String>(), store.state.value.order)
    }

    @Test
    fun `rename trims input and blank input restores the original name`() = runTest(timeout = 60.seconds) {
        installTwoAddons()
        val viewModel = viewModel()
        viewModel.rows.first { it?.size == 2 }

        viewModel.rename(keyFor("/a"), "  Films  ")
        assertEquals("Films", store.state.value.renames[keyFor("/a")])

        viewModel.rename(keyFor("/a"), "   ")
        assertEquals(null, store.state.value.renames[keyFor("/a")])
    }
}
