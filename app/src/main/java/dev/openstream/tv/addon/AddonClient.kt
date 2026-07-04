package dev.openstream.tv.addon

/**
 * The single door to the addon protocol (MASTER_PLAN §3 escape hatch: if the
 * project ever needs stremio-core parity, swap the implementation behind this
 * interface without touching UI).
 *
 * All methods return [Result]: a failed addon call is normal operation (shown
 * as a per-addon failure chip, MASTER_PLAN §4.1.8), never an exception that
 * escapes to a crash. Failures carry [AddonRequestException] with enough
 * context to log and display.
 *
 * `baseUrl` is the manifest URL minus `/manifest.json` — see
 * [AddonUrls.baseUrlOf].
 */
interface AddonClient {
    suspend fun fetchManifest(manifestUrl: String): Result<Manifest>

    suspend fun catalog(
        baseUrl: String,
        type: String,
        id: String,
        extra: Map<String, String> = emptyMap(),
    ): Result<List<MetaItem>>

    suspend fun meta(baseUrl: String, type: String, id: String): Result<MetaItem>

    suspend fun streams(baseUrl: String, type: String, videoId: String): Result<List<Stream>>

    suspend fun subtitles(
        baseUrl: String,
        type: String,
        id: String,
        extra: Map<String, String> = emptyMap(),
    ): Result<List<Subtitle>>
}

/** One failed addon request, with the context a log line / error chip needs. */
class AddonRequestException(
    val url: String,
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception("$reason $url: $message", cause) {
    enum class Reason { NETWORK, HTTP_STATUS, BAD_JSON, INVALID_URL, INVALID_MANIFEST }
}

/** URL normalization rules for user-entered addon URLs (MASTER_PLAN §4.1.1). */
object AddonUrls {
    private const val MANIFEST_SUFFIX = "/manifest.json"

    /**
     * Normalize user input to a fetchable https manifest URL.
     * Accepts `stremio://` links (installer deep-links) by rewriting to https.
     * Returns null when the input can't be a manifest URL.
     */
    fun normalizeManifestUrl(input: String): String? {
        val trimmed = input.trim()
        val rewritten = when {
            trimmed.startsWith("stremio://") -> "https://" + trimmed.removePrefix("stremio://")
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            else -> return null
        }
        return if (rewritten.endsWith(MANIFEST_SUFFIX)) rewritten else null
    }

    /** `https://host/path/manifest.json` → `https://host/path` */
    fun baseUrlOf(manifestUrl: String): String =
        manifestUrl.removeSuffix(MANIFEST_SUFFIX)
}
