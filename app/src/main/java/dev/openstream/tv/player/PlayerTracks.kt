package dev.openstream.tv.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import dev.openstream.tv.data.SUBTITLES_OFF
import java.util.Locale

/**
 * Audio/subtitle track selection for the internal player (§6.1, owner
 * request 2026-07-05: language + caption pickers Stremio lacks).
 *
 * Split on purpose: [buildTrackMenu]/[trackDisplayName] are pure functions on
 * [RawTrack] so the naming/dedup/off logic is unit-testable without media3
 * classes; the media3 boundary is the two thin mappers at the bottom.
 */

enum class TrackKind { AUDIO, SUBTITLE }

/** One selectable entry in the tracks dialog. */
data class TrackOption(
    val kind: TrackKind,
    /** Index into Tracks.groups — how the selection is applied later. */
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
    /** Raw language tag — what the language preference remembers (DECISIONS #19). */
    val languageTag: String? = null,
)

data class TrackMenu(
    val audio: List<TrackOption> = emptyList(),
    val subtitles: List<TrackOption> = emptyList(),
    /** True when no subtitle track is active — the dialog's "Off" state. */
    val subtitlesOff: Boolean = true,
)

/** Player-agnostic snapshot of one track, extracted from media3 [Tracks]. */
data class RawTrack(
    val kind: TrackKind,
    val groupIndex: Int,
    val trackIndex: Int,
    val languageTag: String?,
    val label: String?,
    /** Audio channel count; pass a negative value when unknown/not audio. */
    val channelCount: Int,
    val supported: Boolean,
    val selected: Boolean,
)

fun buildTrackMenu(raw: List<RawTrack>): TrackMenu {
    fun optionsOf(kind: TrackKind): List<TrackOption> {
        val tracks = raw.filter { it.kind == kind && it.supported }
        val seen = mutableMapOf<String, Int>()
        return tracks.mapIndexed { index, track ->
            val base = trackDisplayName(
                track.languageTag, track.label, track.channelCount, index + 1
            )
            // Two tracks can render to the same name (e.g. twin "English"
            // audio); number the repeats so every row stays distinguishable.
            val count = seen.merge(base, 1, Int::plus)!!
            val name = if (count > 1) "$base ($count)" else base
            TrackOption(
                kind, track.groupIndex, track.trackIndex, name, track.selected,
                track.languageTag,
            )
        }
    }
    val subtitles = optionsOf(TrackKind.SUBTITLE)
    return TrackMenu(
        audio = optionsOf(TrackKind.AUDIO),
        subtitles = subtitles,
        subtitlesOff = subtitles.none { it.selected },
    )
}

/**
 * Human name for a track: translated language when the tag is real
 * ("en" → "English"), the stream's own label when it adds information,
 * channel layout for surround audio, "Track N" when there's nothing at all.
 */
fun trackDisplayName(
    languageTag: String?,
    label: String?,
    channelCount: Int,
    fallbackNumber: Int,
): String {
    val parts = mutableListOf<String>()
    displayLanguage(languageTag)?.let { parts += it }
    label?.takeIf { it.isNotBlank() && parts.none { p -> p.equals(it, ignoreCase = true) } }
        ?.let { parts += it }
    if (parts.isEmpty()) parts += "Track $fallbackNumber"
    channelLayoutName(channelCount)?.let { parts += it }
    return parts.joinToString(" · ")
}

/**
 * The language a pick should persist as a preference (DECISIONS #19), or
 * null when the track carries no usable tag — an unnamed track pick should
 * leave the stored preference untouched rather than clobber it.
 */
fun rememberedLanguage(option: TrackOption): String? =
    option.languageTag?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }

/** "en"/"en-US" → "English"; unknown short codes shown raw; null when absent. */
fun displayLanguage(tag: String?): String? {
    if (tag.isNullOrBlank() || tag.equals("und", ignoreCase = true)) return null
    val locale = Locale.forLanguageTag(tag.replace('_', '-'))
    val name = locale.displayLanguage
    return when {
        // Invalid tag (free-text addon labels like "Portuguese-BR"): show as-is.
        name.isBlank() -> tag
        // Locale had no translation and echoed the code back.
        name.equals(locale.language, ignoreCase = true) && name.length <= 3 ->
            tag.uppercase(Locale.ROOT)
        else -> name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}

private fun channelLayoutName(channelCount: Int): String? = when {
    channelCount == 6 -> "5.1"
    channelCount == 8 -> "7.1"
    channelCount > 2 -> "${channelCount}ch"
    else -> null
}

// ---- media3 boundary (thin, untested by design) ----

fun Tracks.toTrackMenu(): TrackMenu = buildTrackMenu(toRawTracks())

private fun Tracks.toRawTracks(): List<RawTrack> {
    val raw = mutableListOf<RawTrack>()
    groups.forEachIndexed { groupIndex, group ->
        val kind = when (group.type) {
            C.TRACK_TYPE_AUDIO -> TrackKind.AUDIO
            C.TRACK_TYPE_TEXT -> TrackKind.SUBTITLE
            else -> return@forEachIndexed
        }
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            raw += RawTrack(
                kind = kind,
                groupIndex = groupIndex,
                trackIndex = trackIndex,
                languageTag = format.language,
                label = format.label,
                channelCount = format.channelCount,
                supported = group.isTrackSupported(trackIndex),
                selected = group.isTrackSelected(trackIndex),
            )
        }
    }
    return raw
}

/** Applies a picked option; re-enables the track type in case it was off. */
fun Player.applyTrackOption(option: TrackOption) {
    val group = currentTracks.groups.getOrNull(option.groupIndex) ?: return
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(group.type, false)
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        .build()
}

fun Player.disableSubtitles() {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

/**
 * Applies the remembered language preferences (DECISIONS #19) before
 * playback starts. Preferences, not overrides: if the stream has no track in
 * the preferred language, ExoPlayer falls back to its normal choice.
 * [subtitle] also takes [dev.openstream.tv.data.SUBTITLES_OFF].
 */
fun Player.applyPreferredLanguages(audio: String?, subtitle: String?) {
    if (audio == null && subtitle == null) return
    trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
        if (audio != null) setPreferredAudioLanguage(audio)
        when (subtitle) {
            null -> Unit
            SUBTITLES_OFF -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            else -> {
                setPreferredTextLanguage(subtitle)
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            }
        }
    }.build()
}
