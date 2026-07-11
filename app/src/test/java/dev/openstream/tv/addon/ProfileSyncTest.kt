package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.FakeInstalledAddonDao
import dev.openstream.tv.addon.fixtures.FakeProfileSyncPrefs
import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.addon.fixtures.MockAddonServer
import dev.openstream.tv.data.ProfileLink
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Remote addon management (owner directive 2026-07-05): the hosted profile is
 * the source of truth for the addons it manages; manual installs are never
 * touched; every failure leaves the box exactly as it was.
 */
class ProfileSyncTest {

    // ---- planSync: the pure diff -------------------------------------------

    @Test
    fun `fresh profile installs everything in order`() {
        val plan = planSync(listOf("a", "b", "c"), emptySet(), emptySet())
        assertEquals(listOf("a", "b", "c"), plan.install)
        assertTrue(plan.remove.isEmpty())
        assertEquals(setOf("a", "b", "c"), plan.managed)
    }

    @Test
    fun `up-to-date box is a no-op`() {
        val plan = planSync(listOf("a", "b"), setOf("a", "b"), setOf("a", "b"))
        assertTrue(plan.install.isEmpty())
        assertTrue(plan.remove.isEmpty())
    }

    @Test
    fun `profile edit adds the new and removes the dropped`() {
        // Owner's real migration shape: 15 old addons -> a few new ones.
        val plan = planSync(
            profileUrls = listOf("new1", "new2", "kept"),
            installedUrls = setOf("old1", "old2", "kept"),
            previouslyManaged = setOf("old1", "old2", "kept"),
        )
        assertEquals(listOf("new1", "new2"), plan.install)
        assertEquals(listOf("old1", "old2"), plan.remove.sorted())
        assertEquals(setOf("new1", "new2", "kept"), plan.managed)
    }

    @Test
    fun `manually added addons are never removed`() {
        val plan = planSync(
            profileUrls = listOf("a"),
            installedUrls = setOf("a", "hand-installed"),
            previouslyManaged = setOf("a"),
        )
        assertTrue(plan.install.isEmpty())
        assertTrue(plan.remove.isEmpty()) // "hand-installed" is not ours to manage
    }

    @Test
    fun `user-removed profile addon is reinstalled - profile wins`() {
        val plan = planSync(
            profileUrls = listOf("a", "b"),
            installedUrls = setOf("a"), // user uninstalled "b" by hand
            previouslyManaged = setOf("a", "b"),
        )
        assertEquals(listOf("b"), plan.install)
    }

    @Test
    fun `already-gone managed addon is not removed twice`() {
        val plan = planSync(
            profileUrls = listOf("a"),
            installedUrls = setOf("a"), // "gone" was dropped from profile AND box
            previouslyManaged = setOf("a", "gone"),
        )
        assertTrue(plan.remove.isEmpty())
    }

    @Test
    fun `installed profile addons are refreshed — a rebuilt config keeps its manifest URL`() {
        // Owner 2026-07-11: the box still showed "Trakt Watchlist/History" rows
        // that the live AIOMetadata config had long dropped — same URL, new
        // manifest, and sync never re-fetched what was already installed.
        val plan = planSync(
            profileUrls = listOf("new", "kept"),
            installedUrls = setOf("kept", "hand-installed"),
            previouslyManaged = setOf("kept"),
        )
        assertEquals(listOf("new"), plan.install)
        assertEquals(listOf("kept"), plan.refresh)
    }

    // ---- syncIfDue: the orchestrator ---------------------------------------

    private var server: MockAddonServer? = null

    @After
    fun tearDown() {
        server?.shutdown()
    }

    private fun harness(): Triple<ProfileSync, FakeProfileSyncPrefs, FakeInstalledAddonDao> {
        val http = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        val dao = FakeInstalledAddonDao()
        val prefs = FakeProfileSyncPrefs()
        val sync = ProfileSync(prefs, SetupProfileClient(http), AddonRepository(OkHttpAddonClient(http), dao))
        return Triple(sync, prefs, dao)
    }

    @Test
    fun `no stored link means no sync`() = runTest(timeout = 60.seconds) {
        val (sync, prefs, dao) = harness()
        sync.syncIfDue(nowMs = 1_000_000)
        assertNull(prefs.link)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `recent sync is throttled`() = runTest(timeout = 60.seconds) {
        val (sync, prefs, dao) = harness()
        prefs.link = ProfileLink("https://unreachable.invalid/p.json", setOf("x"), lastSyncMs = 1_000_000)
        // Within the interval: no network call is even attempted (the URL
        // would fail loudly if it were), state untouched.
        sync.syncIfDue(nowMs = 1_000_000 + ProfileSync.MIN_INTERVAL_MS - 1)
        assertEquals(1_000_000L, prefs.link!!.lastSyncMs)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `due sync applies the profile diff and advances lastSync`() = runTest(timeout = 60.seconds) {
        val (sync, prefs, dao) = harness()
        val srv = MockAddonServer().also { server = it }
        srv.route("/new/manifest.json", Fixtures.load("manifest_full"))
        srv.start()
        val newUrl = srv.url("/new/manifest.json")
        val oldUrl = "https://example.invalid/old/manifest.json"
        val manualUrl = "https://example.invalid/manual/manifest.json"
        srv.route(
            "/profile.json",
            """{"openstream":1,"name":"Family","addons":[{"name":"New","url":"$newUrl"}]}"""
        )
        // Box state: one profile-managed addon (now dropped) + one manual one.
        seedInstalled(dao, oldUrl, sortOrder = 0)
        seedInstalled(dao, manualUrl, sortOrder = 1)
        val now = 10_000_000L
        prefs.link = ProfileLink(srv.url("/profile.json"), managedUrls = setOf(oldUrl), lastSyncMs = 0)

        sync.syncIfDue(nowMs = now)

        val installed = dao.getAll().map { it.manifestUrl }
        assertTrue(newUrl in installed) // added from profile
        assertTrue(oldUrl !in installed) // dropped from profile -> removed
        assertTrue(manualUrl in installed) // manual install untouched
        assertEquals(setOf(newUrl), prefs.link!!.managedUrls)
        assertEquals(now, prefs.link!!.lastSyncMs)
    }

    @Test
    fun `due sync re-fetches the manifest of an already-installed addon`() = runTest(timeout = 60.seconds) {
        val (sync, prefs, dao) = harness()
        val srv = MockAddonServer().also { server = it }
        // The server now serves a DIFFERENT manifest at the same URL — the
        // owner's "rebuilt the addon config, URL unchanged" case.
        srv.route(
            "/kept/manifest.json",
            """{"id":"fix.kept","version":"2.0.0","name":"Rebuilt Addon",
                "resources":["catalog"],"types":["movie"],
                "catalogs":[{"type":"movie","id":"auto.trending","name":"Trending"}]}""",
        )
        srv.start()
        val keptUrl = srv.url("/kept/manifest.json")
        srv.route(
            "/profile.json",
            """{"openstream":1,"name":"Family","addons":[{"name":"Kept","url":"$keptUrl"}]}"""
        )
        seedInstalled(dao, keptUrl, sortOrder = 3) // stored manifest = manifest_full (old)
        dao.setEnabled(keptUrl, false) // user's disable choice must survive the refresh
        prefs.link = ProfileLink(srv.url("/profile.json"), managedUrls = setOf(keptUrl), lastSyncMs = 0)

        sync.syncIfDue(nowMs = 10_000_000L)

        val entity = dao.getAll().single()
        assertTrue("stored manifest should be the rebuilt one", "Rebuilt Addon" in entity.manifestJson)
        assertEquals(3, entity.sortOrder)     // refresh keeps the user's order
        assertEquals(false, entity.enabled)   // ...and their enabled choice
    }

    @Test
    fun `unreachable profile changes nothing and retries next launch`() = runTest(timeout = 60.seconds) {
        val (sync, prefs, dao) = harness()
        val srv = MockAddonServer().also { server = it }
        srv.start()
        val deadProfile = srv.url("/profile.json")
        srv.shutdown()
        val managed = "https://example.invalid/kept/manifest.json"
        seedInstalled(dao, managed, sortOrder = 0)
        prefs.link = ProfileLink(deadProfile, setOf(managed), lastSyncMs = 0)

        sync.syncIfDue(nowMs = 10_000_000)

        assertEquals(listOf(managed), dao.getAll().map { it.manifestUrl })
        // lastSync did NOT advance: "fix the file, restart the app" works.
        assertEquals(0L, prefs.link!!.lastSyncMs)
    }

    /** Installs a row directly so tests control the starting box state. */
    private suspend fun seedInstalled(dao: FakeInstalledAddonDao, url: String, sortOrder: Int) {
        dao.upsert(
            dev.openstream.tv.data.db.InstalledAddonEntity(
                manifestUrl = url,
                manifestJson = Fixtures.load("manifest_full"),
                sortOrder = sortOrder,
                enabled = true,
            )
        )
    }
}
