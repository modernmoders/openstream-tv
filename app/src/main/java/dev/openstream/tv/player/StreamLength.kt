package dev.openstream.tv.player

/**
 * Junk-file detection by length (owner 2026-08-09: "a screen that's a solid
 * color… it'll skip to the end of the episode"). Some debrid sources are fake
 * placeholder videos — a colored card with text — usually seconds-to-minutes
 * long. They play "successfully", end almost immediately, and used to mark
 * the episode watched and trigger the next-episode flow.
 *
 * We never look at pixels: the reliable tell is that the opened file is far
 * shorter than the episode/movie could plausibly be. Two independent tripwires:
 *  - an absolute floor no real movie/episode is under (shortest real content
 *    in the wild ≈ 7-minute kids' episodes; placeholders are 10s–2min);
 *  - when the metadata declared a runtime, anything under half of it
 *    (a real encode never loses half the film; a placeholder never reaches it).
 */

/** No real movie/episode is under this long; junk placeholder cards are. */
const val JUNK_LENGTH_FLOOR_MS = 3 * 60_000L

/** Below this fraction of the metadata's declared runtime = not the real file. */
const val JUNK_LENGTH_EXPECTED_FRACTION = 0.5

/**
 * True when an opened file's real duration is too short to be the content it
 * claims to be. Duration ≤ 0 (unset/live) proves nothing — callers must only
 * pass VOD types anyway (§8: never judge live content by length).
 */
fun isImplausiblyShort(durationMs: Long, expectedRuntimeMin: Int?): Boolean {
    if (durationMs <= 0) return false
    if (durationMs < JUNK_LENGTH_FLOOR_MS) return true
    val expectedMs = expectedRuntimeMin?.let { it * 60_000L } ?: return false
    return durationMs < expectedMs * JUNK_LENGTH_EXPECTED_FRACTION
}

/**
 * Minutes out of a Stremio meta `runtime` string — free-form in the wild:
 * Cinemeta sends "45 min", others "1h 41min", "2h", or a bare "148".
 * Null when nothing parseable (or zero): the caller falls back to the
 * absolute floor only.
 */
fun parseRuntimeMinutes(runtime: String?): Int? {
    if (runtime.isNullOrBlank()) return null
    val text = runtime.lowercase()
    val hours = Regex("""(\d+)\s*h""").find(text)?.groupValues?.get(1)?.toIntOrNull()
    val minutes = Regex("""(\d+)\s*m""").find(text)?.groupValues?.get(1)?.toIntOrNull()
    val total = when {
        hours != null || minutes != null -> (hours ?: 0) * 60 + (minutes ?: 0)
        else -> Regex("""\d+""").find(text)?.value?.toIntOrNull() ?: 0
    }
    return total.takeIf { it > 0 }
}
