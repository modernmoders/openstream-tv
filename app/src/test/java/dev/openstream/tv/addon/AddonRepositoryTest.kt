package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.data.db.InstalledAddonDao
import dev.openstream.tv.data.db.InstalledAddonEntity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Repository logic tests with an in-memory fake DAO (the Room DAO itself is
 * exercised on-device in later phases). Network side uses MockAddonServer.
 */
class AddonRepositoryTest {

    private lateinit var server: MockAddonServer
    private lateinit var dao: FakeInstalledAddonDao
    private lateinit var repository: AddonRepository

    @Before
    fun setUp() {
        server = MockAddonServer()
        dao = FakeInstalledAddonDao()
        repository = AddonRepository(
            OkHttpAddonClient(
                OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
            ),
            dao,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `install fetches manifest and persists enabled at end of list`() = runTest {
        server.route("/manifest.json", Fixtures.load("manifest_full"))
        server.start()

        val installed = repository.install(server.url("/manifest.json")).getOrThrow()

        assertEquals("org.openstream.fixture", installed.manifest.id)
        assertTrue(installed.enabled)
        assertEquals(0, installed.sortOrder)
        assertEquals(1, repository.observeInstalled().first().size)
    }

    @Test
    fun `two instances with the same manifest id coexist`() = runTest {
        // MASTER_PLAN §4.2: multiple AIOStreams configs share a manifest id
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.route("/b/manifest.json", Fixtures.load("manifest_full"))
        server.start()

        repository.install(server.url("/a/manifest.json")).getOrThrow()
        repository.install(server.url("/b/manifest.json")).getOrThrow()

        val installed = repository.observeInstalled().first()
        assertEquals(2, installed.size)
        assertEquals(installed[0].manifest.id, installed[1].manifest.id)
        assertEquals(listOf(0, 1), installed.map { it.sortOrder })
    }

    @Test
    fun `reinstall refreshes manifest but keeps order and enabled flag`() = runTest {
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.route("/b/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        val urlA = server.url("/a/manifest.json")
        repository.install(urlA).getOrThrow()
        repository.install(server.url("/b/manifest.json")).getOrThrow()
        repository.setEnabled(urlA, false)

        val again = repository.install(urlA).getOrThrow()

        assertEquals(0, again.sortOrder)     // kept position, not moved to end
        assertFalse(again.enabled)           // kept user's disabled state
        assertEquals(2, repository.observeInstalled().first().size)
    }

    @Test
    fun `install failure stores nothing`() = runTest {
        server.start() // 404 for everything

        val result = repository.install(server.url("/manifest.json"))

        assertTrue(result.isFailure)
        assertTrue(repository.observeInstalled().first().isEmpty())
    }

    @Test
    fun `invalid url fails fast without network`() = runTest {
        val result = repository.install("definitely not a url")
        val error = result.exceptionOrNull() as AddonRequestException
        assertEquals(AddonRequestException.Reason.INVALID_URL, error.reason)
    }

    @Test
    fun `uninstall and reorder update the observed list`() = runTest {
        server.route("/a/manifest.json", Fixtures.load("manifest_full"))
        server.route("/b/manifest.json", Fixtures.load("manifest_full"))
        server.route("/c/manifest.json", Fixtures.load("manifest_full"))
        server.start()
        val a = server.url("/a/manifest.json")
        val b = server.url("/b/manifest.json")
        val c = server.url("/c/manifest.json")
        listOf(a, b, c).forEach { repository.install(it).getOrThrow() }

        repository.uninstall(b)
        repository.reorder(listOf(c, a))

        val installed = repository.observeInstalled().first()
        assertEquals(listOf(c, a), installed.map { it.manifestUrl })
    }
}

/** In-memory stand-in for the Room DAO. */
private class FakeInstalledAddonDao : InstalledAddonDao {
    private val state = MutableStateFlow<Map<String, InstalledAddonEntity>>(emptyMap())

    private fun sorted() = state.value.values.sortedBy { it.sortOrder }

    override fun observeAll(): Flow<List<InstalledAddonEntity>> =
        state.map { it.values.sortedBy { e -> e.sortOrder } }

    override suspend fun getAll(): List<InstalledAddonEntity> = sorted()

    override suspend fun get(manifestUrl: String): InstalledAddonEntity? =
        state.value[manifestUrl]

    override suspend fun upsert(entity: InstalledAddonEntity) {
        state.value = state.value + (entity.manifestUrl to entity)
    }

    override suspend fun delete(manifestUrl: String) {
        state.value = state.value - manifestUrl
    }

    override suspend fun setEnabled(manifestUrl: String, enabled: Boolean) {
        state.value[manifestUrl]?.let { upsert(it.copy(enabled = enabled)) }
    }

    override suspend fun setSortOrder(manifestUrl: String, sortOrder: Int) {
        state.value[manifestUrl]?.let { upsert(it.copy(sortOrder = sortOrder)) }
    }
}
