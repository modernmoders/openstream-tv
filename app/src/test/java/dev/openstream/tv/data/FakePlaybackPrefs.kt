package dev.openstream.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory PlaybackPrefs for JVM tests — same contract, no DataStore. */
class FakePlaybackPrefs : PlaybackPrefs {
    val languagesState = MutableStateFlow(LanguagePrefs())
    val playerState = MutableStateFlow(PLAYER_INTERNAL)
    val autoPlayState = MutableStateFlow(false)
    val softwareDecoderState = MutableStateFlow(false)
    val skipIntrosState = MutableStateFlow(true)

    override val languages: Flow<LanguagePrefs> = languagesState
    override val preferredPlayer: Flow<String> = playerState
    override val autoPlayFirstStream: Flow<Boolean> = autoPlayState
    override val preferSoftwareDecoder: Flow<Boolean> = softwareDecoderState
    override val skipIntrosEnabled: Flow<Boolean> = skipIntrosState

    override suspend fun setAudioLanguage(languageTag: String) {
        languagesState.update { it.copy(audio = languageTag) }
    }

    override suspend fun setSubtitleLanguage(languageTag: String) {
        languagesState.update { it.copy(subtitle = languageTag) }
    }

    override suspend fun setPreferredPlayer(value: String) {
        playerState.value = value
    }

    override suspend fun setAutoPlayFirstStream(enabled: Boolean) {
        autoPlayState.value = enabled
    }

    override suspend fun setPreferSoftwareDecoder(enabled: Boolean) {
        softwareDecoderState.value = enabled
    }

    override suspend fun setSkipIntrosEnabled(enabled: Boolean) {
        skipIntrosState.value = enabled
    }
}
