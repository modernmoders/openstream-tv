package dev.openstream.tv.ui

import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.OkHttpAddonClient
import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.data.SetupConfig
import dev.openstream.tv.data.db.InstalledAddonEntity
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

/** First-launch gate: Welcome only for a configured build with nothing installed. */
class LaunchViewModelTest {

    private lateinit var dao: FakeInstalledAddonDao

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeInstalledAddonDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(setupUrl: String) = LaunchViewModel(
        AddonRepository(OkHttpAddonClient(OkHttpClient()), dao),
        SetupConfig(setupUrl, "TestBrand"),
    )

    private suspend fun LaunchViewModel.decision(): Boolean =
        startOnWelcome.first { it != null }!!

    @Test
    fun `fresh install with a setup site starts on welcome`() = runTest(timeout = 60.seconds) {
        assertEquals(true, viewModel("https://example.test/setup/").decision())
    }

    @Test
    fun `already set up starts home`() = runTest(timeout = 60.seconds) {
        dao.upsert(
            InstalledAddonEntity("https://a.test/manifest.json", Fixtures.load("manifest_full"), 0, true)
        )
        assertEquals(false, viewModel("https://example.test/setup/").decision())
    }

    @Test
    fun `build without a setup site never shows welcome`() = runTest(timeout = 60.seconds) {
        assertEquals(false, viewModel("").decision())
    }
}
