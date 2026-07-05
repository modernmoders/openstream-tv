package dev.openstream.tv.data

import dev.openstream.tv.addon.fixtures.FakeWatchProgressDao
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressRepositoryTest {

    private fun repo() = ProgressRepository(FakeWatchProgressDao(), CoroutineScope(Dispatchers.Unconfined))

    private fun progress(
        id: String = "tt1:1:1",
        positionMs: Long = 300_000,
        durationMs: Long = 1_200_000,
        updatedAt: Long = 1_000,
    ) = WatchProgress(
        ref = MediaRef.addon(id),
        metaId = "tt1",
        metaType = "series",
        title = "E1",
        poster = null,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
    )

    // --- resume-eligibility rules (§10 Phase 2: what Continue Watching shows) ---

    @Test
    fun `mid-playback progress is resumable`() {
        assertTrue(ProgressRepository.isResumable(progress(positionMs = 300_000)))
    }

    @Test
    fun `under 15 seconds in is not resumable`() {
        assertFalse(ProgressRepository.isResumable(progress(positionMs = 14_999)))
    }

    @Test
    fun `20 seconds in resumes the dialog but stays out of Continue Watching`() {
        // Owner feedback 2026-07-04: swapping to a new stream mid-episode must
        // carry short progress over — without letting brief clicks clutter home.
        val brief = progress(positionMs = 20_000)
        assertTrue(ProgressRepository.isResumable(brief))
        assertTrue(ProgressRepository.continueWatching(listOf(brief)).isEmpty())
    }

    @Test
    fun `past 95 percent counts as watched`() {
        assertFalse(
            ProgressRepository.isResumable(
                progress(positionMs = 1_150_000, durationMs = 1_200_000)
            )
        )
    }

    @Test
    fun `unknown duration is not resumable`() {
        // duration stays 0/TIME_UNSET if playback never reached prepared
        assertFalse(ProgressRepository.isResumable(progress(durationMs = 0)))
    }

    @Test
    fun `continue watching is filtered and newest-first`() {
        val old = progress(id = "a", updatedAt = 1)
        val newer = progress(id = "b", updatedAt = 2)
        val tooEarly = progress(id = "c", positionMs = 1_000, updatedAt = 3)

        val row = ProgressRepository.continueWatching(listOf(old, tooEarly, newer))

        assertEquals(listOf("b", "a"), row.map { it.ref.externalId })
    }

    // --- persistence roundtrip via the DAO contract ---

    @Test
    fun `save then get then clear roundtrip`() = runTest(timeout = 60.seconds) {
        val repo = repo()
        val p = progress()

        repo.save(p)
        assertEquals(p, repo.get(p.ref))
        assertEquals(300_000L, repo.resumePositionFor(p.ref))

        repo.clear(p.ref)
        assertNull(repo.get(p.ref))
    }

    @Test
    fun `resumePositionFor is null for barely-started items`() = runTest(timeout = 60.seconds) {
        val repo = repo()
        val p = progress(positionMs = 9_000)
        repo.save(p)
        assertNull(repo.resumePositionFor(p.ref))
    }

    @Test
    fun `observeResumePosition re-emits as playback saves land`() = runTest(timeout = 60.seconds) {
        val repo = repo()
        val p = progress()

        assertNull(repo.observeResumePosition(p.ref).first())

        repo.save(p)
        assertEquals(300_000L, repo.observeResumePosition(p.ref).first())

        // Player saved a later position while this screen sat on the back stack
        repo.save(p.copy(positionMs = 900_000))
        assertEquals(900_000L, repo.observeResumePosition(p.ref).first())
    }

    @Test
    fun `observeContinueWatching reflects saves and clears`() = runTest(timeout = 60.seconds) {
        val repo = repo()
        val p = progress()

        repo.save(p)
        assertEquals(listOf(p), repo.observeContinueWatching().first())

        repo.clear(p.ref)
        assertTrue(repo.observeContinueWatching().first().isEmpty())
    }
}
