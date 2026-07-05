package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Manifest
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamBehaviorHints
import dev.openstream.tv.addon.Video
import dev.openstream.tv.autoplay.AutoplayStateMachine.State
import dev.openstream.tv.autoplay.StreamCascade.CurrentStream
import dev.openstream.tv.domain.MediaRef
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Controller wiring tests: real time via the test scheduler, fake addon world.
 * The machine's RULES are covered in AutoplayStateMachineTest — here we prove
 * the controller feeds it real ticks/responses and executes effects.
 */
class AutoplayControllerTest {

    private class FakeGateway(
        var meta: MetaItem? = null,
        var addons: List<InstalledAddon> = emptyList(),
        /** manifestUrl → (delayMs, streams) */
        var streams: Map<String, Pair<Long, List<Stream>>> = emptyMap(),
    ) : AutoplayGateway {
        override suspend fun resolveMeta(type: String, id: String) = meta
        override suspend fun streamAddons(type: String, videoId: String) = addons
        override suspend fun fetchStreams(addon: InstalledAddon, type: String, videoId: String): List<Stream> {
            val (delayMs, result) = streams[addon.manifestUrl] ?: return emptyList()
            delay(delayMs)
            return result
        }
    }

    private fun addon(url: String, order: Int) =
        InstalledAddon(url, Manifest(id = url, name = url), enabled = true, sortOrder = order)

    private fun stream(name: String, bingeGroup: String? = null) = Stream(
        url = "https://cdn.example/$name.mp4",
        name = name,
        behaviorHints = StreamBehaviorHints(bingeGroup = bingeGroup),
    )

    private fun seriesMeta() = MetaItem(
        id = "tt1", type = "series", name = "Show",
        videos = listOf(
            Video(id = "tt1:1:3", season = 1, episode = 3),
            Video(id = "tt1:1:4", season = 1, episode = 4, name = "The Next One"),
        ),
    )

    private val origin = CurrentStream("https://aio.example/manifest.json", stream("current", "aio|1080p"))

    private fun controllerEnded(
        controller: AutoplayController,
        scope: kotlinx.coroutines.CoroutineScope,
        metaType: String = "series",
    ) = controller.onPlaybackEnded(scope, metaType, "tt1", MediaRef.addon("tt1:1:3"), origin)

    @Test
    fun `movie stays inactive`() = runTest(timeout = 30.seconds) {
        val controller = AutoplayController(FakeGateway(meta = seriesMeta()))
        controllerEnded(controller, backgroundScope, metaType = "movie")
        runCurrent()
        assertNull(controller.state.value)
    }

    @Test
    fun `series end reaches Finished, not a countdown`() = runTest(timeout = 30.seconds) {
        val gateway = FakeGateway(meta = seriesMeta())
        val controller = AutoplayController(gateway)
        controller.onPlaybackEnded(backgroundScope, "series", "tt1", MediaRef.addon("tt1:1:4"), origin)
        runCurrent()
        assertEquals(State.Finished, controller.state.value)
    }

    @Test
    fun `unresolvable meta stays inactive (plain finished panel)`() = runTest(timeout = 30.seconds) {
        val controller = AutoplayController(FakeGateway(meta = null))
        controllerEnded(controller, backgroundScope)
        runCurrent()
        assertNull(controller.state.value)
    }

    @Test
    fun `full happy path - countdown ticks, fan-out, bingeGroup match plays`() = runTest(timeout = 30.seconds) {
        val gateway = FakeGateway(
            meta = seriesMeta(),
            addons = listOf(addon("https://aio.example/manifest.json", 0)),
            streams = mapOf(
                "https://aio.example/manifest.json" to (0L to listOf(stream("next-ep", "aio|1080p"))),
            ),
        )
        val controller = AutoplayController(gateway)
        val commands = mutableListOf<AutoplayController.Command>()
        backgroundScope.launch { controller.commands.collect { commands += it } }

        controllerEnded(controller, backgroundScope)
        runCurrent()
        assertEquals(State.Countdown(seriesMeta().videos[1], 10), controller.state.value)

        advanceTimeBy(9_000); runCurrent()
        assertEquals(9 - 8, (controller.state.value as State.Countdown).secondsLeft)

        advanceTimeBy(1_500); runCurrent() // countdown expires → resolve → respond → play
        assertTrue(controller.state.value is State.Attempting)
        val play = commands.filterIsInstance<AutoplayController.Command.Play>().single()
        assertEquals("next-ep", play.candidate.stream.name)
        assertEquals("tt1:1:4", play.next.id)

        controller.onPlaybackReady() // stream opened — card must disappear
        assertNull(controller.state.value)
    }

    @Test
    fun `OK skips the countdown`() = runTest(timeout = 30.seconds) {
        val gateway = FakeGateway(
            meta = seriesMeta(),
            addons = listOf(addon("https://aio.example/manifest.json", 0)),
            streams = mapOf("https://aio.example/manifest.json" to (0L to listOf(stream("s", "aio|1080p")))),
        )
        val controller = AutoplayController(gateway)
        controllerEnded(controller, backgroundScope)
        runCurrent()
        assertTrue(controller.onConfirm(backgroundScope))
        runCurrent()
        assertTrue(controller.state.value is State.Attempting)
    }

    @Test
    fun `confirm outside the countdown is not consumed`() = runTest(timeout = 30.seconds) {
        val controller = AutoplayController(FakeGateway(meta = seriesMeta()))
        assertEquals(false, controller.onConfirm(backgroundScope)) // inactive
        controller.onPlaybackEnded(backgroundScope, "series", "tt1", MediaRef.addon("tt1:1:4"), origin)
        runCurrent() // → Finished
        assertEquals(false, controller.onConfirm(backgroundScope))
    }

    @Test
    fun `back cancels the countdown to Finished`() = runTest(timeout = 30.seconds) {
        val controller = AutoplayController(FakeGateway(meta = seriesMeta()))
        controllerEnded(controller, backgroundScope)
        runCurrent()
        assertTrue(controller.onBack(backgroundScope))
        assertEquals(State.Finished, controller.state.value)
        assertEquals(false, controller.onBack(backgroundScope)) // Finished → not consumed → screen exits
    }

    @Test
    fun `a 20s-slow addon still autoplays (the §7_2 patience case)`() = runTest(timeout = 30.seconds) {
        val gateway = FakeGateway(
            meta = seriesMeta(),
            addons = listOf(addon("https://aio.example/manifest.json", 0)),
            streams = mapOf(
                "https://aio.example/manifest.json" to (20_000L to listOf(stream("slow", "aio|1080p"))),
            ),
        )
        val controller = AutoplayController(gateway)
        val commands = mutableListOf<AutoplayController.Command>()
        backgroundScope.launch { controller.commands.collect { commands += it } }

        controllerEnded(controller, backgroundScope)
        runCurrent()
        advanceTimeBy(10_500); runCurrent() // countdown done, fan-out launched
        assertTrue(controller.state.value is State.Resolving)

        advanceTimeBy(15_000); runCurrent() // 15s in: still waiting, not cancelled
        assertTrue(controller.state.value is State.Resolving)

        advanceTimeBy(6_000); runCurrent() // addon answers at 20s
        assertTrue(controller.state.value is State.Attempting)
        assertEquals("slow", (commands.single() as AutoplayController.Command.Play).candidate.stream.name)
    }

    @Test
    fun `nothing playable anywhere lands on the manual stream list`() = runTest(timeout = 30.seconds) {
        val gateway = FakeGateway(
            meta = seriesMeta(),
            addons = listOf(addon("https://aio.example/manifest.json", 0)),
            streams = mapOf("https://aio.example/manifest.json" to (0L to emptyList())),
        )
        val controller = AutoplayController(gateway)
        val commands = mutableListOf<AutoplayController.Command>()
        backgroundScope.launch { controller.commands.collect { commands += it } }

        controllerEnded(controller, backgroundScope)
        advanceTimeBy(11_000); runCurrent()
        assertEquals(State.ManualFallback(seriesMeta().videos[1]), controller.state.value)
        assertEquals("tt1:1:4", (commands.single() as AutoplayController.Command.OpenStreamList).next.id)
    }

    @Test
    fun `open failure falls through to the next candidate`() = runTest(timeout = 30.seconds) {
        val gateway = FakeGateway(
            meta = seriesMeta(),
            addons = listOf(addon("https://aio.example/manifest.json", 0)),
            streams = mapOf(
                "https://aio.example/manifest.json" to
                    (0L to listOf(stream("c1", "aio|1080p"), stream("c2", "aio|1080p"))),
            ),
        )
        val controller = AutoplayController(gateway)
        val commands = mutableListOf<AutoplayController.Command>()
        backgroundScope.launch { controller.commands.collect { commands += it } }

        controllerEnded(controller, backgroundScope)
        advanceTimeBy(11_000); runCurrent()
        assertTrue(controller.onPlaybackError(backgroundScope)) // c1 failed to open
        runCurrent()
        assertEquals(
            listOf("c1", "c2"),
            commands.filterIsInstance<AutoplayController.Command.Play>().map { it.candidate.stream.name },
        )
    }

    @Test
    fun `player error when autoplay is not attempting is not consumed`() = runTest(timeout = 30.seconds) {
        val controller = AutoplayController(FakeGateway(meta = seriesMeta()))
        assertEquals(false, controller.onPlaybackError(backgroundScope))
        controllerEnded(controller, backgroundScope)
        runCurrent() // countdown — a late error from the old stream must show the error panel
        assertEquals(false, controller.onPlaybackError(backgroundScope))
    }
}
