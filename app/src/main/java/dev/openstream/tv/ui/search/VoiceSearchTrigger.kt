package dev.openstream.tv.ui.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "The magnifying glass was clicked" (owner 2026-07-12): every DELIBERATE
 * entry into Search — rail item or Home pill — should start the microphone
 * again, even when the screen still holds an old search. A BACK-return to
 * Search must NOT re-fire. Sections are saved/restored, so the screen alone
 * can't tell those two apart; navigation increments this counter on the
 * click, and the screen fires the mic once per unseen increment.
 */
@Singleton
class VoiceSearchTrigger @Inject constructor() {
    private val _requests = MutableStateFlow(0)
    val requests: StateFlow<Int> = _requests

    fun request() {
        _requests.value += 1
    }
}
