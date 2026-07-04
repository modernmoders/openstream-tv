package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamBehaviorHints
import dev.openstream.tv.addon.Video
import dev.openstream.tv.autoplay.AutoplayStateMachine.Effect
import dev.openstream.tv.autoplay.AutoplayStateMachine.Event
import dev.openstream.tv.autoplay.AutoplayStateMachine.State
import dev.openstream.tv.autoplay.StreamCascade.AddonStreams
import dev.openstream.tv.autoplay.StreamCascade.CurrentStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayStateMachineTest {

    private val next = Video(id = "tt1:1:4", season = 1, episode = 4)

    private fun stream(name: String, bingeGroup: String? = null) = Stream(
        url = "https://cdn.example/$name.mp4",
        name = name,
        behaviorHints = StreamBehaviorHints(bingeGroup = bingeGroup),
    )

    private fun machine(bingeGroup: String? = "aio|1080p") = AutoplayStateMachine(
        current = CurrentStream("https://aio.example", stream("current", bingeGroup)),
    )

    private fun group(vararg streams: Stream, addonIndex: Int = 0, url: String = "https://aio.example") =
        AddonStreams(url, addonIndex, streams.toList())

    /** Drive the machine into Resolving with a known addon count. */
    private fun resolving(m: AutoplayStateMachine, total: Int): State {
        val started = m.reduce(State.Countdown(next, 1), Event.Tick)
        return m.reduce(started.state, Event.ResolutionStarted(total)).state
    }

    // --- countdown ---

    @Test
    fun `no next episode goes straight to Finished`() {
        assertEquals(State.Finished, machine().start(null))
    }

    @Test
    fun `start opens a full-length countdown`() {
        assertEquals(State.Countdown(next, 10), machine().start(next))
    }

    @Test
    fun `ticks count down without side effects`() {
        val m = machine()
        val t = m.reduce(State.Countdown(next, 10), Event.Tick)
        assertEquals(State.Countdown(next, 9), t.state)
        assertNull(t.effect)
    }

    @Test
    fun `countdown expiry starts resolution`() {
        val t = machine().reduce(State.Countdown(next, 1), Event.Tick)
        assertTrue(t.state is State.Resolving)
        assertEquals(Effect.StartResolving(next), t.effect)
    }

    @Test
    fun `OK skips the countdown`() {
        val t = machine().reduce(State.Countdown(next, 10), Event.ConfirmPressed)
        assertTrue(t.state is State.Resolving)
        assertEquals(Effect.StartResolving(next), t.effect)
    }

    @Test
    fun `Back during countdown cancels to Finished`() {
        val t = machine().reduce(State.Countdown(next, 5), Event.BackPressed)
        assertEquals(State.Finished, t.state)
        assertNull(t.effect)
    }

    // --- resolving: early exit paths ---

    @Test
    fun `zero stream addons goes to manual list immediately`() {
        val m = machine()
        val started = m.reduce(State.Countdown(next, 1), Event.Tick)
        val t = m.reduce(started.state, Event.ResolutionStarted(0))
        assertEquals(State.ManualFallback(next), t.state)
        assertEquals(Effect.OpenStreamList(next), t.effect)
    }

    @Test
    fun `bingeGroup match plays immediately even with addons still pending`() {
        val m = machine(bingeGroup = "aio|1080p")
        val s = resolving(m, total = 3)
        val t = m.reduce(s, Event.AddonResponded(group(stream("match", bingeGroup = "aio|1080p"))))
        assertTrue(t.state is State.Attempting)
        assertEquals("match", (t.effect as Effect.Play).candidate.stream.name)
    }

    @Test
    fun `non-matching response keeps waiting while addons are pending`() {
        val m = machine(bingeGroup = "aio|1080p")
        val s = resolving(m, total = 2)
        val t = m.reduce(s, Event.AddonResponded(group(stream("other"))))
        assertTrue(t.state is State.Resolving)
        assertEquals(1, (t.state as State.Resolving).respondedAddons)
        assertNull(t.effect)
    }

    @Test
    fun `all addons settled without bingeGroup plays the ranked winner`() {
        val m = machine(bingeGroup = null)
        var s = resolving(m, total = 2)
        s = m.reduce(s, Event.AddonResponded(group(stream("first"), addonIndex = 1, url = "https://b.example"))).state
        val t = m.reduce(s, Event.AddonResponded(group(stream("aio-own"), addonIndex = 0, url = "https://aio.example")))
        assertTrue(t.state is State.Attempting)
        // same addon as current playback wins tier 2
        assertEquals("aio-own", (t.effect as Effect.Play).candidate.stream.name)
    }

    @Test
    fun `all addons settled with zero playable goes to manual list`() {
        val m = machine()
        val s = resolving(m, total = 1)
        val t = m.reduce(s, Event.AddonResponded(group())) // addon answered with nothing
        assertEquals(State.ManualFallback(next), t.state)
        assertEquals(Effect.OpenStreamList(next), t.effect)
    }

    // --- resolving: patience rule (§7.1 step 4) ---

    @Test
    fun `resolution is never cancelled before 60 seconds`() {
        val m = machine()
        var s = resolving(m, total = 2)
        s = m.reduce(s, Event.AddonResponded(group(stream("slowish")))).state // 1/2, no binge match
        repeat(AutoplayStateMachine.PATIENCE_SECONDS - 1) {
            val t = m.reduce(s, Event.Tick)
            assertTrue("tick ${it + 1} must stay Resolving", t.state is State.Resolving)
            assertNull(t.effect)
            s = t.state
        }
    }

    @Test
    fun `at 60 seconds with some playable streams it plays instead of dying`() {
        val m = machine()
        var s = resolving(m, total = 2)
        s = m.reduce(s, Event.AddonResponded(group(stream("only-answer")))).state
        repeat(AutoplayStateMachine.PATIENCE_SECONDS - 1) { s = m.reduce(s, Event.Tick).state }
        val t = m.reduce(s, Event.Tick) // second 60
        assertTrue(t.state is State.Attempting)
        assertEquals("only-answer", (t.effect as Effect.Play).candidate.stream.name)
    }

    @Test
    fun `at 60 seconds with zero playable streams it lands on the manual list`() {
        val m = machine()
        var s = resolving(m, total = 2)
        repeat(AutoplayStateMachine.PATIENCE_SECONDS) { s = m.reduce(s, Event.Tick).state }
        assertEquals(State.ManualFallback(next), s)
    }

    @Test
    fun `Back during resolving cancels to Finished`() {
        val m = machine()
        val t = m.reduce(resolving(m, total = 2), Event.BackPressed)
        assertEquals(State.Finished, t.state)
    }

    // --- attempting: open-failure fallthrough (§7.1 step 5) ---

    private fun attempting(m: AutoplayStateMachine, count: Int): State {
        val s = resolving(m, total = 1)
        val streams = (1..count).map { stream("c$it") }.toTypedArray()
        return m.reduce(s, Event.AddonResponded(group(*streams))).state
    }

    @Test
    fun `open failure falls through to the next candidate`() {
        val m = machine(bingeGroup = null)
        val t = m.reduce(attempting(m, count = 3), Event.StreamOpenFailed)
        assertEquals(1, (t.state as State.Attempting).attempt)
        assertEquals("c2", (t.effect as Effect.Play).candidate.stream.name)
    }

    @Test
    fun `third failure opens the manual list`() {
        val m = machine(bingeGroup = null)
        var s = attempting(m, count = 5)
        s = m.reduce(s, Event.StreamOpenFailed).state // -> c2
        s = m.reduce(s, Event.StreamOpenFailed).state // -> c3
        val t = m.reduce(s, Event.StreamOpenFailed)   // 3 attempts burned
        assertEquals(State.ManualFallback(next), t.state)
        assertEquals(Effect.OpenStreamList(next), t.effect)
    }

    @Test
    fun `running out of candidates before the attempt cap opens the manual list`() {
        val m = machine(bingeGroup = null)
        val s = attempting(m, count = 1)
        val t = m.reduce(s, Event.StreamOpenFailed)
        assertEquals(State.ManualFallback(next), t.state)
    }

    @Test
    fun `Back during attempting cancels to Finished`() {
        val m = machine(bingeGroup = null)
        val t = m.reduce(attempting(m, count = 2), Event.BackPressed)
        assertEquals(State.Finished, t.state)
    }

    // --- terminal states stay put ---

    @Test
    fun `terminal states ignore further events`() {
        val m = machine()
        assertEquals(State.Finished, m.reduce(State.Finished, Event.Tick).state)
        val manual = State.ManualFallback(next)
        assertEquals(manual, m.reduce(manual, Event.StreamOpenFailed).state)
    }
}
