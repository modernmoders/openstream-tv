package dev.openstream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The setup link this box was configured from (remote addon management,
 * owner directive 2026-07-05). Boxes live with out-of-state family: the owner
 * edits the hosted profile JSON and every box follows on its next launch —
 * nobody has to touch the TV. Null until a setup link is installed.
 */
@Serializable
data class ProfileLink(
    val url: String,
    /**
     * Normalized manifest URLs this profile manages. Only these may be
     * auto-removed when they disappear from the profile — addons the user
     * added by hand are never touched. SECURITY: these embed personal config
     * tokens (CLAUDE.md rule); they stay in app-private storage and must
     * never be logged or displayed.
     */
    val managedUrls: Set<String> = emptySet(),
    /** Epoch millis of the last successful sync (or the initial install). */
    val lastSyncMs: Long = 0,
)

/** Interface so the sync engine and ViewModels stay JVM-testable. */
interface ProfileSyncPrefs {
    suspend fun get(): ProfileLink?
    suspend fun save(link: ProfileLink)
    /** Forgets the saved link (owner request: "Reset this TV" in Settings). */
    suspend fun clear()
}

private val Context.profileSyncStore by preferencesDataStore("profile_sync_prefs")

/** One JSON blob, one key — same rationale as [DataStoreHomeRowPrefs]. */
@Singleton
class DataStoreProfileSyncPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProfileSyncPrefs {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(): ProfileLink? =
        context.profileSyncStore.data.first()[BLOB]?.let { raw ->
            runCatching { json.decodeFromString(ProfileLink.serializer(), raw) }.getOrNull()
        }

    override suspend fun save(link: ProfileLink) {
        context.profileSyncStore.edit { store ->
            store[BLOB] = json.encodeToString(ProfileLink.serializer(), link)
        }
    }

    override suspend fun clear() {
        context.profileSyncStore.edit { store -> store.remove(BLOB) }
    }

    private companion object {
        val BLOB = stringPreferencesKey("profile_link")
    }
}
