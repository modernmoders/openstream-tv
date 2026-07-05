package dev.openstream.tv.player

import dev.openstream.tv.data.PLAYER_ASK
import dev.openstream.tv.domain.PlayableSource

/**
 * External player launch specs + result interpretation (MASTER_PLAN §6.2),
 * kept as PURE data and functions: the VLC and MX intent contracts differ and
 * are not cross-compatible, so every field lives in an exhaustively
 * table-testable spec here, and only ExternalPlayerLauncher touches Android.
 */
enum class ExternalPlayer(val label: String, val packageNames: List<String>) {
    /** https://wiki.videolan.org/Android_Player_Intents/ */
    VLC("VLC", listOf("org.videolan.vlc")),

    /** Pro first: someone who paid for it expects it to be the one used. */
    MX_PLAYER("MX Player", listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")),

    /** Plain ACTION_VIEW chooser for anything else the user has installed. */
    GENERIC("Other apps…", emptyList()),
}

/** Typed intent extras — Android-free so specs can be asserted in JVM tests. */
sealed interface ExtraValue {
    data class Str(val value: String) : ExtraValue
    data class IntVal(val value: Int) : ExtraValue
    data class LongVal(val value: Long) : ExtraValue
    data class BoolVal(val value: Boolean) : ExtraValue
    data class StrArray(val values: List<String>) : ExtraValue

    /** Becomes a parcelable Uri[] (MX's `subs` contract). */
    data class UriArray(val urls: List<String>) : ExtraValue
}

/** Everything the Android layer needs to build the ACTION_VIEW intent. */
data class ExternalLaunch(
    val player: ExternalPlayer,
    /** Concrete package to target; null = system chooser (GENERIC). */
    val packageName: String?,
    val url: String,
    val mimeType: String,
    val extras: Map<String, ExtraValue>,
)

fun buildExternalLaunch(
    player: ExternalPlayer,
    packageName: String?,
    source: PlayableSource,
): ExternalLaunch {
    val extras: Map<String, ExtraValue> = when (player) {
        ExternalPlayer.VLC -> buildMap {
            put("title", ExtraValue.Str(source.title))
            if (source.startPositionMs > 0) {
                put("position", ExtraValue.LongVal(source.startPositionMs))
            } else {
                // "Start over" must beat VLC's own remembered resume point
                put("from_start", ExtraValue.BoolVal(true))
            }
            source.subtitles.firstOrNull()?.let {
                put("subtitles_location", ExtraValue.Str(it.url))
            }
            if (source.headers.isNotEmpty()) {
                put("http-headers", ExtraValue.StrArray(flattenHeaders(source.headers)))
            }
        }

        ExternalPlayer.MX_PLAYER -> buildMap {
            put("title", ExtraValue.Str(source.title))
            // Without this MX never reports the exit position back to us
            put("return_result", ExtraValue.BoolVal(true))
            if (source.startPositionMs > 0) {
                // MX's contract is int ms (fine: overflows past ~24 days)
                put("position", ExtraValue.IntVal(source.startPositionMs.toInt()))
            }
            if (source.headers.isNotEmpty()) {
                put("headers", ExtraValue.StrArray(flattenHeaders(source.headers)))
            }
            if (source.subtitles.isNotEmpty()) {
                put("subs", ExtraValue.UriArray(source.subtitles.map { it.url }))
                put("subs.name", ExtraValue.StrArray(source.subtitles.map { it.lang }))
            }
        }

        // Generic handlers share no extras contract — send none (§6.2)
        ExternalPlayer.GENERIC -> emptyMap()
    }
    return ExternalLaunch(
        player = player,
        packageName = packageName,
        url = source.url,
        mimeType = source.mimeTypeHint ?: "video/*",
        extras = extras,
    )
}

/** Both players take headers as one flat [k1, v1, k2, v2, …] string array. */
private fun flattenHeaders(headers: Map<String, String>): List<String> =
    headers.flatMap { (k, v) -> listOf(k, v) }

/** What the external player's activity result means for us. */
sealed interface ExternalOutcome {
    /** Mid-stream exit: persist as watch progress. */
    data class Progress(val positionMs: Long, val durationMs: Long) : ExternalOutcome

    /** Watched to (near) the end: clear progress, offer Up Next (§7.1.6). */
    data object Finished : ExternalOutcome

    /** No usable position came back: leave stored progress untouched. */
    data object Unknown : ExternalOutcome
}

/** Activity.RESULT_OK, restated so this file stays Android-free. */
const val EXTERNAL_RESULT_OK = -1

/**
 * Map a raw activity result to an [ExternalOutcome]. [extras] values arrive
 * untyped because MX reports int ms where VLC reports long ms — read
 * leniently, trust nothing (a generic player returns no extras at all).
 */
fun interpretExternalResult(
    player: ExternalPlayer,
    resultCode: Int,
    extras: Map<String, Any?>,
): ExternalOutcome {
    if (resultCode != EXTERNAL_RESULT_OK) return ExternalOutcome.Unknown

    // MX says outright that the file played to the end — believe it even
    // when the position extras are absent.
    if (player == ExternalPlayer.MX_PLAYER && extras["end_by"] == "playback_completion") {
        return ExternalOutcome.Finished
    }

    val (posKey, durKey) = when (player) {
        ExternalPlayer.VLC -> "extra_position" to "extra_duration"
        ExternalPlayer.MX_PLAYER -> "position" to "duration"
        ExternalPlayer.GENERIC -> return ExternalOutcome.Unknown
    }
    val position = (extras[posKey] as? Number)?.toLong() ?: return ExternalOutcome.Unknown
    val duration = (extras[durKey] as? Number)?.toLong() ?: return ExternalOutcome.Unknown
    // Position 0 is also what players report when they never really started
    if (position <= 0 || duration <= 0) return ExternalOutcome.Unknown

    return if (isNearComplete(position, duration)) {
        ExternalOutcome.Finished
    } else {
        ExternalOutcome.Progress(position, duration)
    }
}

/** §7.1.6 near-complete rule: ≥95% watched, or inside the last 30 seconds. */
fun isNearComplete(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 &&
        (positionMs >= durationMs * NEAR_COMPLETE_FRACTION || durationMs - positionMs <= NEAR_COMPLETE_TAIL_MS)

private const val NEAR_COMPLETE_FRACTION = 0.95
private const val NEAR_COMPLETE_TAIL_MS = 30_000L

// ---- §6.2 "Always use" player setting ----

/** What a plain OK on a stream should do, given the setting. */
sealed interface PlayerDecision {
    data object Internal : PlayerDecision
    data object Ask : PlayerDecision
    data class External(val choice: ExternalPlayerPort.Choice) : PlayerDecision
}

/**
 * Resolves the stored player preference against what is installed RIGHT NOW.
 * Anything that can't be honored falls back to the internal player — an
 * uninstalled VLC must not turn every OK press into a dead click (§5.4 no
 * dead-ends; §6.2 detection happens at use time, not just settings time).
 */
fun resolvePreferredPlayer(
    pref: String,
    installed: List<ExternalPlayerPort.Choice>,
): PlayerDecision = when {
    pref == PLAYER_ASK ->
        if (installed.isEmpty()) PlayerDecision.Internal else PlayerDecision.Ask
    else ->
        installed.firstOrNull { it.player.name == pref }
            ?.let { PlayerDecision.External(it) }
            ?: PlayerDecision.Internal // "internal", unknown value, or uninstalled
}
