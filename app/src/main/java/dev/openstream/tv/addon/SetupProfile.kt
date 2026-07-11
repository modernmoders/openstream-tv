package dev.openstream.tv.addon

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A "setup link" (DECISIONS #14): one privately-hosted JSON file listing
 * addon manifest URLs, so a fresh install becomes paste-one-link instead of
 * typing every addon. Generated per person by `tools/make_profiles.py`.
 *
 * This is OUR convenience format, not part of the Stremio addon protocol —
 * hence its own client rather than a method on [AddonClient].
 *
 * SECURITY: the app never ships addon URLs (they embed personal config
 * tokens — CLAUDE.md rule). A profile lives wherever its owner hosts it and
 * is fetched at install time only; every addon still previews and installs
 * through the normal on-TV confirm flow (§4.1.1).
 */
@Serializable
data class SetupProfile(
    /** Format marker + version; anything ≥ 1 identifies a setup profile. */
    val openstream: Int = 0,
    val name: String = "",
    val addons: List<Entry> = emptyList(),
) {
    @Serializable
    data class Entry(val name: String = "", val url: String = "")

    /** Usable = explicitly marked as a profile and at least one installable entry. */
    val isUsable: Boolean
        get() = openstream >= 1 && addons.any { AddonUrls.normalizeManifestUrl(it.url) != null }
}

/** Fetches a [SetupProfile]; failures reuse [AddonRequestException] reasons. */
@Singleton
class SetupProfileClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(url: String): Result<SetupProfile> = withContext(Dispatchers.IO) {
        val request = try {
            // Never trust cache freshness here: the profile is the remote-
            // management channel, and shared hosts stamp static JSON with a
            // multi-day max-age (Dreamhost: 172800s) — box .117 served a
            // 2-day-old profile from disk cache on every sync, zero network,
            // until 2026-07-11. noCache = always revalidate; with the host's
            // ETag an unchanged file still costs only a 304.
            Request.Builder().url(url)
                .cacheControl(CacheControl.Builder().noCache().build())
                .build()
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(
                AddonRequestException(url, AddonRequestException.Reason.INVALID_URL, "not an http(s) URL", e)
            )
        }
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        AddonRequestException(url, AddonRequestException.Reason.HTTP_STATUS, "HTTP ${response.code}")
                    )
                }
                val body = response.body.string()
                val profile = try {
                    AddonJson.decodeFromString(SetupProfile.serializer(), body)
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        AddonRequestException(url, AddonRequestException.Reason.BAD_JSON, e.message ?: "undecodable JSON", e)
                    )
                }
                if (!profile.isUsable) {
                    // Valid JSON, wrong shape: random pages decode to defaults
                    // (openstream=0) thanks to lenient parsing, so gate hard here.
                    return@withContext Result.failure(
                        AddonRequestException(
                            url, AddonRequestException.Reason.INVALID_MANIFEST,
                            "JSON is not a setup profile with installable addons"
                        )
                    )
                }
                Result.success(profile)
            }
        } catch (e: IOException) {
            // Class name only — the URL and exception message can repeat the
            // owner's setup domain / tokens (same rule as AddonClient).
            android.util.Log.w("SetupProfile", "fetch failed: ${e::class.simpleName}")
            Result.failure(
                AddonRequestException(url, AddonRequestException.Reason.NETWORK, e.message ?: "network error", e)
            )
        }
    }
}
