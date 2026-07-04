package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.Video
import dev.openstream.tv.autoplay.StreamCascade.AddonStreams
import dev.openstream.tv.autoplay.StreamCascade.Candidate
import dev.openstream.tv.autoplay.StreamCascade.CurrentStream

/**
 * Autoplay state machine (§7.1) as a pure reducer: (state, event) → (state,
 * effect). No clocks, no coroutines — the wiring layer feeds one [Event.Tick]
 * per second and executes [Effect]s; every rule here is unit-testable (§9.2).
 *
 * Patience rule (§7.1 step 4, non-negotiable): once resolving starts, the only
 * exits are user Back, a playable candidate, or [PATIENCE_SECONDS] elapsed with
 * zero playable streams — and that last case lands on the manual stream list,
 * never a dead screen.
 */
class AutoplayStateMachine(
    private val current: CurrentStream,
    private val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
) {

    sealed interface State {
        /** Terminal: nothing to autoplay — show the plain "Playback finished" panel. */
        data object Finished : State

        /** "Up next: …" card with live countdown; OK skips straight to resolving. */
        data class Countdown(val next: Video, val secondsLeft: Int) : State

        /** Fan-out in flight: "Finding next episode… (2/3 responded)". */
        data class Resolving(
            val next: Video,
            val totalAddons: Int,
            val groups: List<AddonStreams> = emptyList(),
            val elapsedSeconds: Int = 0,
        ) : State {
            val respondedAddons: Int get() = groups.size
        }

        /** Playing candidates[attempt]; open failure falls through (§7.1 step 5). */
        data class Attempting(val next: Video, val candidates: List<Candidate>, val attempt: Int) : State

        /** Terminal: open the next episode's stream list — never a dead screen. */
        data class ManualFallback(val next: Video) : State
    }

    sealed interface Event {
        /** One second passed (countdown and patience timers). */
        data object Tick : Event

        /** OK on the Up Next card: skip the rest of the countdown. */
        data object ConfirmPressed : Event

        /** The only user cancel (§7.1 step 4a). */
        data object BackPressed : Event

        /** Resolution fan-out launched; how many addons were asked. */
        data class ResolutionStarted(val totalAddons: Int) : Event

        /** One addon answered ([streams] empty for failures — chip is UI's job). */
        data class AddonResponded(val group: AddonStreams) : Event

        /** The candidate we tried to play failed to open. */
        data object StreamOpenFailed : Event
    }

    sealed interface Effect {
        /** Kick off the parallel stream fan-out for [next]. */
        data class StartResolving(val next: Video) : Effect

        /** Hand this candidate to the player. */
        data class Play(val candidate: Candidate) : Effect

        /** Navigate to the manual stream list for [next]. */
        data class OpenStreamList(val next: Video) : Effect
    }

    data class Transition(val state: State, val effect: Effect? = null)

    /** Entry point on PlayerEvent.Ended / "Next episode" press. */
    fun start(next: Video?): State =
        if (next == null) State.Finished else State.Countdown(next, countdownSeconds)

    fun reduce(state: State, event: Event): Transition = when (state) {
        is State.Countdown -> reduceCountdown(state, event)
        is State.Resolving -> reduceResolving(state, event)
        is State.Attempting -> reduceAttempting(state, event)
        State.Finished, is State.ManualFallback -> Transition(state) // terminal
    }

    private fun reduceCountdown(state: State.Countdown, event: Event): Transition = when (event) {
        Event.Tick -> {
            val left = state.secondsLeft - 1
            if (left <= 0) beginResolving(state.next) else Transition(state.copy(secondsLeft = left))
        }
        Event.ConfirmPressed -> beginResolving(state.next)
        Event.BackPressed -> Transition(State.Finished)
        else -> Transition(state)
    }

    private fun beginResolving(next: Video) = Transition(
        // totalAddons unknown until the fan-out reports in; 0 avoids "0/0 responded" math traps
        State.Resolving(next, totalAddons = 0),
        Effect.StartResolving(next),
    )

    private fun reduceResolving(state: State.Resolving, event: Event): Transition = when (event) {
        is Event.ResolutionStarted ->
            if (event.totalAddons == 0) manual(state.next) // nothing declares streams for this episode
            else Transition(state.copy(totalAddons = event.totalAddons))

        is Event.AddonResponded -> {
            val updated = state.copy(groups = state.groups + event.group)
            val ranked = StreamCascade.rank(current, updated.groups)
            val bingeMatch = current.stream.behaviorHints.bingeGroup != null &&
                ranked.firstOrNull()?.stream?.behaviorHints?.bingeGroup == current.stream.behaviorHints.bingeGroup
            when {
                // Tier 1 hit: the protocol's purpose-built match — no better candidate can arrive
                bingeMatch -> attempt(updated.next, ranked)
                // Everyone answered: rank what we have or fall back — no reason to wait
                updated.totalAddons > 0 && updated.respondedAddons >= updated.totalAddons ->
                    if (ranked.isEmpty()) manual(updated.next) else attempt(updated.next, ranked)
                else -> Transition(updated)
            }
        }

        Event.Tick -> {
            val updated = state.copy(elapsedSeconds = state.elapsedSeconds + 1)
            if (updated.elapsedSeconds < PATIENCE_SECONDS) Transition(updated)
            else {
                // Patience ceiling: play whatever the stragglers left us, else manual list
                val ranked = StreamCascade.rank(current, updated.groups)
                if (ranked.isEmpty()) manual(updated.next) else attempt(updated.next, ranked)
            }
        }

        Event.BackPressed -> Transition(State.Finished)
        else -> Transition(state)
    }

    private fun reduceAttempting(state: State.Attempting, event: Event): Transition = when (event) {
        Event.StreamOpenFailed -> {
            val nextAttempt = state.attempt + 1
            if (nextAttempt >= MAX_ATTEMPTS || nextAttempt > state.candidates.lastIndex) manual(state.next)
            else Transition(
                state.copy(attempt = nextAttempt),
                Effect.Play(state.candidates[nextAttempt]),
            )
        }
        Event.BackPressed -> Transition(State.Finished)
        else -> Transition(state)
    }

    private fun attempt(next: Video, ranked: List<Candidate>) = Transition(
        State.Attempting(next, ranked, attempt = 0),
        Effect.Play(ranked.first()),
    )

    private fun manual(next: Video) = Transition(
        State.ManualFallback(next),
        Effect.OpenStreamList(next),
    )

    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 10

        /** §7.1 step 4: never cancel resolution with a timer shorter than this. */
        const val PATIENCE_SECONDS = 60

        /** §7.1 step 5: open-failure fallthrough budget before the manual list. */
        const val MAX_ATTEMPTS = 3
    }
}
