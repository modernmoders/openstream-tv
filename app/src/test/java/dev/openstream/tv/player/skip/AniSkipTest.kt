package dev.openstream.tv.player.skip

import dev.openstream.tv.diagnostics.DiagnosticsSink
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniSkipTest {

    // --- activeSegmentAt: the "show the Skip button now?" rule ---

    private val intro = SkipSegment(SkipType.INTRO, 40_000, 130_000)
    private val credits = SkipSegment(SkipType.CREDITS, 1_360_000, 1_440_000)
    private val segs = listOf(intro, credits)

    @Test
    fun `inside the intro window returns the intro`() {
        assertEquals(intro, activeSegmentAt(90_000, segs))
    }

    @Test
    fun `start is inclusive, end is exclusive`() {
        assertEquals(intro, activeSegmentAt(40_000, segs))
        assertNull(activeSegmentAt(130_000, segs)) // exactly at end = past it
    }

    @Test
    fun `between windows there is no button`() {
        assertNull(activeSegmentAt(500_000, segs))
    }

    @Test
    fun `inside the credits window returns credits`() {
        assertEquals(credits, activeSegmentAt(1_400_000, segs))
    }

    @Test
    fun `overlapping windows pick the earliest start`() {
        val a = SkipSegment(SkipType.INTRO, 0, 100)
        val b = SkipSegment(SkipType.CREDITS, 50, 200)
        assertEquals(a, activeSegmentAt(60, listOf(b, a)))
    }

    // --- AniSkip response mapping ---

    @Test
    fun `op maps to intro and ed to credits`() {
        val resp = AniSkipResponse(
            found = true,
            results = listOf(
                AniSkipResult("op", AniSkipInterval(1.5, 91.5)),
                AniSkipResult("ed", AniSkipInterval(1360.0, 1440.0)),
            ),
        )
        val out = resp.toSegments()
        assertEquals(listOf(SkipType.INTRO, SkipType.CREDITS), out.map { it.type })
        assertEquals(1_500L, out[0].startMs)
        assertEquals(91_500L, out[0].endMs)
    }

    @Test
    fun `mixed op and ed are recognised, unknown types dropped`() {
        val resp = AniSkipResponse(
            found = true,
            results = listOf(
                AniSkipResult("mixed-op", AniSkipInterval(0.0, 90.0)),
                AniSkipResult("recap", AniSkipInterval(90.0, 200.0)),
                AniSkipResult("mixed-ed", AniSkipInterval(1300.0, 1400.0)),
            ),
        )
        assertEquals(listOf(SkipType.INTRO, SkipType.CREDITS), resp.toSegments().map { it.type })
    }

    @Test
    fun `not-found and inverted intervals yield nothing`() {
        assertTrue(AniSkipResponse(found = false).toSegments().isEmpty())
        val bad = AniSkipResponse(true, listOf(AniSkipResult("op", AniSkipInterval(90.0, 90.0))))
        assertTrue(bad.toSegments().isEmpty()) // zero-length dropped
    }

    // --- SkipTimesRepository orchestration ---

    private fun repo(
        resolver: AnimeMalIdResolver,
        client: AniSkipClient,
    ) = SkipTimesRepository(resolver, client, DiagnosticsSink.NONE)

    @Test
    fun `unresolved id means no segments and no AniSkip call`() = runTest {
        var called = false
        val r = repo(
            resolver = { _, _, _, _ -> null },
            client = { _, _, _ -> called = true; listOf(intro) },
        )
        assertTrue(r.segmentsFor("tt123", season = 1, episode = 3, absoluteEpisode = null, durationMs = 0).isEmpty())
        assertTrue("must not query AniSkip without a MAL id", !called)
    }

    @Test
    fun `resolved id passes AniSkip windows through`() = runTest {
        val r = repo(
            resolver = { _, _, _, _ -> MalEpisode(20L, 3) },
            client = { mal, ep, _ -> if (mal == 20L && ep == 3) listOf(intro, credits) else emptyList() },
        )
        assertEquals(
            // The repository applies the per-type bias on the way through.
            withSkipBias(listOf(intro, credits)),
            r.segmentsFor("mal:20", season = null, episode = 3, absoluteEpisode = null, durationMs = 0),
        )
    }

    @Test
    fun `the resolver's TRANSLATED episode is what AniSkip gets asked for`() = runTest {
        // Naruto S2E5 → MAL 20 absolute episode 40: the client must see 40, not 5.
        val r = repo(
            resolver = { id, season, episode, absolute ->
                if (id == "tt0409591" && season == 2 && episode == 5) MalEpisode(20L, absolute!!) else null
            },
            client = { mal, ep, _ -> if (mal == 20L && ep == 40) listOf(intro) else emptyList() },
        )
        assertEquals(
            withSkipBias(listOf(intro)),
            r.segmentsFor("tt0409591", season = 2, episode = 5, absoluteEpisode = 40, durationMs = 0),
        )
    }

    @Test
    fun `non-positive episode is ignored`() = runTest {
        val r = repo(
            resolver = { _, _, _, _ -> MalEpisode(20L, 1) },
            client = { _, _, _ -> listOf(intro) },
        )
        assertTrue(r.segmentsFor("mal:20", season = null, episode = 0, absoluteEpisode = null, durationMs = 0).isEmpty())
    }
}

/** Round-15/17 additions: per-type bias + the auto-skip decision. */
class SkipBehaviorTest {

    @Test
    fun `intro ends are trimmed so a skip lands early, never late`() {
        val trimmed = withSkipBias(
            listOf(SkipSegment(SkipType.INTRO, 40_000, 130_000)),
        )
        assertEquals(130_000 - SKIP_END_EARLY_MS, trimmed.single().endMs)
        assertEquals(40_000, trimmed.single().startMs)
    }

    @Test
    fun `an intro too short to trim keeps at least one second`() {
        val tiny = withSkipBias(listOf(SkipSegment(SkipType.INTRO, 10_000, 11_500)))
        assertEquals(11_000, tiny.single().endMs) // floor: start + 1s
        assertEquals(10_000, tiny.single().startMs)
    }

    @Test
    fun `credits start late so the Up next prompt never beats the credits`() {
        val biased = withSkipBias(
            listOf(SkipSegment(SkipType.CREDITS, 1_360_000, 1_440_000)),
        )
        assertEquals(1_360_000 + CREDITS_START_LATE_MS, biased.single().startMs)
        assertEquals(1_440_000, biased.single().endMs) // end untouched — nothing seeks there
    }

    @Test
    fun `credits too short to delay keep at least one second of window`() {
        val tiny = withSkipBias(listOf(SkipSegment(SkipType.CREDITS, 10_000, 11_500)))
        assertEquals(10_500, tiny.single().startMs) // ceiling: end - 1s
        assertEquals(11_500, tiny.single().endMs)
    }

    @Test
    fun `auto-skip decision follows the two toggles by segment type`() {
        assertEquals(
            AutoSkipAction.SEEK_PAST,
            autoSkipActionFor(SkipType.INTRO, autoSkipIntros = true, autoSkipCredits = false),
        )
        assertEquals(
            AutoSkipAction.NONE,
            autoSkipActionFor(SkipType.INTRO, autoSkipIntros = false, autoSkipCredits = true),
        )
        assertEquals(
            AutoSkipAction.COUNTDOWN_TO_NEXT,
            autoSkipActionFor(SkipType.CREDITS, autoSkipIntros = true, autoSkipCredits = true),
        )
        assertEquals(
            AutoSkipAction.NONE,
            autoSkipActionFor(SkipType.CREDITS, autoSkipIntros = true, autoSkipCredits = false),
        )
    }
}
