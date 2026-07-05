package dev.openstream.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory HomeRowPrefsStore for JVM tests — same contract, no DataStore. */
class FakeHomeRowPrefsStore(initial: HomeRowPrefs = HomeRowPrefs()) : HomeRowPrefsStore {
    val state = MutableStateFlow(initial)

    override val prefs: Flow<HomeRowPrefs> = state

    override suspend fun setOrder(keys: List<String>) {
        state.update { it.copy(order = keys) }
    }

    override suspend fun setHidden(key: String, hidden: Boolean) {
        state.update {
            it.copy(hidden = if (hidden) it.hidden + key else it.hidden - key)
        }
    }

    override suspend fun setRename(key: String, name: String?) {
        state.update {
            it.copy(
                renames = if (name == null) it.renames - key
                else it.renames + (key to name)
            )
        }
    }
}
