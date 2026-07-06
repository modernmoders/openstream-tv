package dev.openstream.tv.addon

import dev.openstream.tv.data.SetupConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** What the setup site said when we asked "who is <name>?". */
sealed interface NameLookupOutcome {
    /** Exactly one person matched — [profileUrl] is their setup profile. */
    data class Found(val fullName: String, val profileUrl: String) : NameLookupOutcome
    /** Several people matched; ask which one and retry with [SetupNameLookup.byFullName]. */
    data class Ambiguous(val choices: List<String>) : NameLookupOutcome
    /** Nobody matched — [message] is already people-friendly. */
    data class NoMatch(val message: String) : NameLookupOutcome
}

/** The JSON the setup site's api mode answers with (index.php, api=1). */
@Serializable
private data class LookupResponse(
    val ok: Boolean = false,
    val name: String = "",
    val link: String = "",
    val choices: List<String> = emptyList(),
    val error: String = "",
)

/**
 * Asks the owner's setup site to turn a typed name into that person's
 * profile URL (owner directive 2026-07-06: one-step setup — nobody ever
 * sees or copies a link; the lookup happens back here). The site keeps the
 * name→file map server-side, so profile URLs still never appear on screen.
 */
@Singleton
class SetupNameLookup @Inject constructor(
    private val httpClient: OkHttpClient,
    private val config: SetupConfig,
) {

    val isConfigured: Boolean get() = config.isConfigured

    /** Free-typed name ("adam s") — the site does the fuzzy matching. */
    suspend fun byName(who: String): Result<NameLookupOutcome> =
        post(FormBody.Builder().add("who", who).add("api", "1").build())

    /** Exact full name, picked from an [NameLookupOutcome.Ambiguous] list. */
    suspend fun byFullName(full: String): Result<NameLookupOutcome> =
        post(FormBody.Builder().add("full", full).add("api", "1").build())

    private suspend fun post(body: FormBody): Result<NameLookupOutcome> = withContext(Dispatchers.IO) {
        val url = config.setupUrl
        val request = try {
            Request.Builder().url(url).post(body).build()
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(
                AddonRequestException(url, AddonRequestException.Reason.INVALID_URL, "setup URL misconfigured", e)
            )
        }
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        AddonRequestException(url, AddonRequestException.Reason.HTTP_STATUS, "HTTP ${response.code}")
                    )
                }
                val text = response.body.string()
                val parsed = try {
                    AddonJson.decodeFromString(LookupResponse.serializer(), text)
                } catch (e: Exception) {
                    // Old index.php still answers with an HTML page: the site
                    // needs its one-file update before this flow can work.
                    return@withContext Result.failure(
                        AddonRequestException(url, AddonRequestException.Reason.BAD_JSON, e.message ?: "not JSON", e)
                    )
                }
                Result.success(
                    when {
                        parsed.ok && parsed.link.isNotBlank() -> NameLookupOutcome.Found(parsed.name, parsed.link)
                        parsed.choices.isNotEmpty() -> NameLookupOutcome.Ambiguous(parsed.choices)
                        else -> NameLookupOutcome.NoMatch(
                            parsed.error.ifBlank { "No match — check the spelling and try again." }
                        )
                    }
                )
            }
        } catch (e: IOException) {
            Result.failure(
                AddonRequestException(url, AddonRequestException.Reason.NETWORK, e.message ?: "network error", e)
            )
        }
    }
}
