package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.Video
import dev.openstream.tv.autoplay.AutoplayStateMachine.Effect
import dev.openstream.tv.autoplay.AutoplayStateMachine.Event
import dev.openstream.tv.autoplay.AutoplayStateMachine.State
import dev.openstream.tv.autoplay.StreamCascade.AddonStreams
import dev.openstream.tv.autoplay.StreamCascade.Candidate
import dev.openstream.tv.autoplay.StreamCascade.CurrentStream
import dev.openstream.tv.domain.MediaRef
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives [AutoplayStateMachine] with real time and real addon fan-out (§7.1).
 * The machine owns every rule; this class only feeds it events (1s ticks,
 * addon responses, player outcomes) and executes its effects. All calls run
 * on the owning ViewModel's main dispatcher, so event handling is serialized.
 *
 * The controller never touches the player or navigation directly — it emits
 * [Command]s that PlayerViewModel executes (testability + §8.2 layering).
 */
class AutoplayController @Inject constructor(
    private val gateway: AutoplayGateway,
) {

    /** What the player side must do; consumed by PlayerViewModel. */
    sealed interface Command {
        data class Play(val next: Video, val candidate: Candidate) : Command
        data class OpenStreamList(val next: Video) : Command
    }

    /** Null = inactive: the screen shows its plain finished/error panels. */
    val state: StateFlow<State?> get() = _state
    private val _state = MutableStateFlow<State?>(null)

    val commands: SharedFlow<Command> get() = _commands
    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 4)

    private var machine: AutoplayStateMachine? = null
    private var jobs: MutableList<Job> = mutableListOf()
    private var metaType: String = ""

    /**
     * PlayerEvent.Ended hook. Resolves the next episode from the series meta
     * (fetched here — no screen needs to smuggle the videos list around) and
     * starts the countdown. Anything non-series or unresolvable leaves the
     * controller inactive: the plain "Playback finished" panel is the honest
     * fallback, exactly the pre-autoplay behavior.
     */
    fun onPlaybackEnded(
        scope: CoroutineScope,
        metaType: String,
        metaId: String,
        mediaRef: MediaRef?,
        origin: CurrentStream?,
    ) {
        stop()
        if (metaType != "series") return
        // For addon content the progress key IS the episode's video id (§8.4)
        if (mediaRef == null || mediaRef.sourceKind != MediaRef.KIND_ADDON) return
        this.metaType = metaType

        jobs += scope.launch {
            val meta = gateway.resolveMeta(metaType, metaId) ?: return@launch
            val next = NextEpisode.nextAfter(meta.videos, mediaRef.externalId)
            val m = AutoplayStateMachine(origin ?: CurrentStream("", Stream()))
            machine = m
            _state.value = m.start(next)
            jobs += scope.launch { tick(scope) }
        }
    }

    /** OK on the Up Next countdown. Returns true if autoplay consumed the press. */
    fun onConfirm(scope: CoroutineScope): Boolean {
        if (_state.value !is State.Countdown) return false
        return dispatchIfActive(scope, Event.ConfirmPressed)
    }

    /** Back cancels autoplay (§7.1 step 4a). True = consumed, don't exit the screen. */
    fun onBack(scope: CoroutineScope): Boolean {
        if (!_state.value.isCancellable()) return false
        return dispatchIfActive(scope, Event.BackPressed)
    }

    /** PlayerEvent.Ready: the attempted stream opened — autoplay's job is done. */
    fun onPlaybackReady() {
        if (_state.value is State.Attempting) stop()
    }

    /** PlayerEvent.Error while attempting → §7.1 step 5 fallthrough. True = consumed (no error panel). */
    fun onPlaybackError(scope: CoroutineScope): Boolean {
        if (_state.value !is State.Attempting) return false
        dispatchIfActive(scope, Event.StreamOpenFailed)
        return true
    }

    /** Full deactivation: cancel timers/fan-out, hide the card. */
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        machine = null
        _state.value = null
    }

    private suspend fun tick(scope: CoroutineScope) {
        // Ticks are harmless outside Countdown/Resolving (the reducer ignores
        // them), so one loop for the controller's whole active life is enough.
        while (true) {
            delay(1_000)
            dispatchIfActive(scope, Event.Tick)
        }
    }

    private fun dispatchIfActive(scope: CoroutineScope, event: Event): Boolean {
        val m = machine ?: return false
        val s = _state.value ?: return false
        val t = m.reduce(s, event)
        _state.value = t.state
        when (val effect = t.effect) {
            is Effect.StartResolving -> jobs += scope.launch { fanOut(scope, effect.next) }
            is Effect.Play -> _commands.tryEmit(
                Command.Play((t.state as State.Attempting).next, effect.candidate)
            )
            is Effect.OpenStreamList -> _commands.tryEmit(Command.OpenStreamList(effect.next))
            null -> Unit
        }
        return true
    }

    companion object {
        /** States where Back means "cancel autoplay", not "leave the screen". */
        fun State?.isCancellable(): Boolean =
            this is State.Countdown || this is State.Resolving || this is State.Attempting
    }

    /** §4.1.5-shaped fan-out: every addon in parallel, each response is an event. */
    private suspend fun fanOut(scope: CoroutineScope, next: Video) {
        val addons = gateway.streamAddons(metaType, next.id)
        dispatchIfActive(scope, Event.ResolutionStarted(addons.size))
        addons.forEachIndexed { index, addon ->
            jobs += scope.launch {
                val streams = gateway.fetchStreams(addon, metaType, next.id)
                // Late responses after the machine moved on are ignored by the reducer
                dispatchIfActive(scope, Event.AddonResponded(AddonStreams(addon.manifestUrl, index, streams)))
            }
        }
    }
}
