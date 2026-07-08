package dev.openstream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Sentinel for "the user turned subtitles off — keep them off next time". */
const val SUBTITLES_OFF = "off"

/** §6.2 "Always use" player setting values; any other string is an
 *  [dev.openstream.tv.player.ExternalPlayer] enum name (e.g. "VLC"). */
const val PLAYER_INTERNAL = "internal"
const val PLAYER_ASK = "ask"

/**
 * Preferred playback languages (DECISIONS #19): every audio/subtitle pick in
 * the player is remembered and re-applied on the next playback, so a Spanish
 * household picks their language ONCE (§10 elder-friendly). Null = no
 * preference recorded yet; subtitle also takes [SUBTITLES_OFF].
 */
data class LanguagePrefs(
    val audio: String? = null,
    val subtitle: String? = null,
)

interface PlaybackPrefs {
    val languages: Flow<LanguagePrefs>

    /** [PLAYER_INTERNAL] (default), [PLAYER_ASK], or an ExternalPlayer name. */
    val preferredPlayer: Flow<String>

    /**
     * Owner request 2026-07-06 (§10 elder-friendly): picking a movie/episode
     * starts the first stream automatically — no stream list to understand —
     * and a broken stream quietly advances to the next one. Default off.
     */
    val autoPlayFirstStream: Flow<Boolean>

    /**
     * Prefer software video decoders over the TV's hardware decoder (owner
     * 2026-07-08, MASTER_PLAN §10 R11 N1). The 32-bit onn boxes' hardware
     * decoders macroblock some encodes (anime) that software decodes clean —
     * MX-Player parity. **Default ON** (owner 2026-07-08: the toggle fixed the
     * glitching, so make it the out-of-the-box behavior). The trade-off is 4K
     * HEVC/AV1 can stutter in software — the pro box can turn this OFF in
     * Settings if that bites. Read once when the engine is built.
     */
    val preferSoftwareDecoder: Flow<Boolean>

    /**
     * Show a one-press "Skip Intro"/"Skip Credits" button during an anime
     * episode's opening/ending (owner 2026-07-08). Windows come from the
     * community AniSkip database, so this only ever appears on timed anime —
     * nothing to detect, nothing to misfire. Default ON.
     */
    val skipIntrosEnabled: Flow<Boolean>
    suspend fun setAudioLanguage(languageTag: String)

    /** A real language tag, or [SUBTITLES_OFF]. */
    suspend fun setSubtitleLanguage(languageTag: String)
    suspend fun setPreferredPlayer(value: String)
    suspend fun setAutoPlayFirstStream(enabled: Boolean)
    suspend fun setPreferSoftwareDecoder(enabled: Boolean)
    suspend fun setSkipIntrosEnabled(enabled: Boolean)
}

private val Context.playbackPrefsStore by preferencesDataStore("playback_prefs")

@Singleton
class DataStorePlaybackPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackPrefs {

    override val languages: Flow<LanguagePrefs> =
        context.playbackPrefsStore.data.map { prefs ->
            LanguagePrefs(audio = prefs[AUDIO_LANG], subtitle = prefs[SUBTITLE_LANG])
        }

    override suspend fun setAudioLanguage(languageTag: String) {
        context.playbackPrefsStore.edit { it[AUDIO_LANG] = languageTag }
    }

    override suspend fun setSubtitleLanguage(languageTag: String) {
        context.playbackPrefsStore.edit { it[SUBTITLE_LANG] = languageTag }
    }

    override val preferredPlayer: Flow<String> =
        context.playbackPrefsStore.data.map { it[PLAYER] ?: PLAYER_INTERNAL }

    override suspend fun setPreferredPlayer(value: String) {
        context.playbackPrefsStore.edit { it[PLAYER] = value }
    }

    override val autoPlayFirstStream: Flow<Boolean> =
        // Default ON (owner directive 2026-07-06): picking a movie/episode just
        // plays; a broken stream quietly tries the next server (§7.1.7).
        context.playbackPrefsStore.data.map { it[AUTO_PLAY_FIRST] ?: true }

    override suspend fun setAutoPlayFirstStream(enabled: Boolean) {
        context.playbackPrefsStore.edit { it[AUTO_PLAY_FIRST] = enabled }
    }

    override val preferSoftwareDecoder: Flow<Boolean> =
        // Default ON (owner 2026-07-08): the software path fixed the anime
        // macroblocking, so it's the safe default across the boxes. A box that
        // stutters on 4K (the pro box) can turn it OFF in Settings.
        context.playbackPrefsStore.data.map { it[PREFER_SW_DECODER] ?: true }

    override suspend fun setPreferSoftwareDecoder(enabled: Boolean) {
        context.playbackPrefsStore.edit { it[PREFER_SW_DECODER] = enabled }
    }

    override val skipIntrosEnabled: Flow<Boolean> =
        // Default ON: the button only shows on timed anime, so it's inert
        // everywhere else — nothing to opt into.
        context.playbackPrefsStore.data.map { it[SKIP_INTROS] ?: true }

    override suspend fun setSkipIntrosEnabled(enabled: Boolean) {
        context.playbackPrefsStore.edit { it[SKIP_INTROS] = enabled }
    }

    private companion object {
        val AUDIO_LANG = stringPreferencesKey("audio_language")
        val SUBTITLE_LANG = stringPreferencesKey("subtitle_language")
        val PLAYER = stringPreferencesKey("preferred_player")
        val AUTO_PLAY_FIRST = booleanPreferencesKey("auto_play_first_stream")
        val PREFER_SW_DECODER = booleanPreferencesKey("prefer_software_decoder")
        val SKIP_INTROS = booleanPreferencesKey("skip_intros_enabled")
    }
}
