package dev.openstream.tv.addon

import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OkHttp implementation of [AddonClient].
 *
 * Endpoint grammar (protocol.md): `/{resource}/{type}/{id}.json`, optionally
 * `/{resource}/{type}/{id}/{extraProps}.json` where extraProps is a
 * query-string-style *path segment* (e.g. `search=game%20of%20thrones&skip=100`).
 *
 * Per-request timeouts belong to the injected [OkHttpClient]; the ~15s
 * per-addon budget of the parallel fan-out (MASTER_PLAN §4.1.5) is enforced by
 * the repository layer coordinating multiple addons, not here.
 */
class OkHttpAddonClient @Inject constructor(
    private val httpClient: OkHttpClient,
) : AddonClient {

    override suspend fun fetchManifest(manifestUrl: String): Result<Manifest> {
        val normalized = AddonUrls.normalizeManifestUrl(manifestUrl)
            ?: return Result.failure(
                AddonRequestException(
                    manifestUrl, AddonRequestException.Reason.INVALID_URL,
                    "not an http(s)/stremio manifest.json URL"
                )
            )
        return get(normalized, Manifest.serializer()).mapCatching { manifest ->
            if (!manifest.isUsable) throw AddonRequestException(
                normalized, AddonRequestException.Reason.INVALID_MANIFEST,
                "manifest is missing id/name/resources"
            )
            manifest
        }
    }

    override suspend fun catalog(
        baseUrl: String, type: String, id: String, extra: Map<String, String>,
    ): Result<List<MetaItem>> =
        get(resourceUrl(baseUrl, "catalog", type, id, extra), CatalogResponse.serializer())
            .map { it.metas }

    override suspend fun meta(baseUrl: String, type: String, id: String): Result<MetaItem> =
        get(resourceUrl(baseUrl, "meta", type, id), MetaResponse.serializer())
            .mapCatching { response ->
                response.meta?.takeIf { it.isUsable } ?: throw AddonRequestException(
                    baseUrl, AddonRequestException.Reason.BAD_JSON, "response has no usable meta"
                )
            }

    override suspend fun streams(
        baseUrl: String, type: String, videoId: String,
    ): Result<List<Stream>> =
        get(resourceUrl(baseUrl, "stream", type, videoId), StreamsResponse.serializer())
            .map { it.streams }

    override suspend fun subtitles(
        baseUrl: String, type: String, id: String, extra: Map<String, String>,
    ): Result<List<Subtitle>> =
        get(resourceUrl(baseUrl, "subtitles", type, id, extra), SubtitlesResponse.serializer())
            .map { it.subtitles }

    /**
     * Build `{baseUrl}/{resource}/{type}/{id}[/{extraProps}].json`.
     * The id may contain `:` (e.g. `tt0108778:1:1`) — legal in a path segment.
     */
    private fun resourceUrl(
        baseUrl: String, resource: String, type: String, id: String,
        extra: Map<String, String> = emptyMap(),
    ): String? {
        val base = baseUrl.trimEnd('/').toHttpUrlOrNull() ?: return null
        val builder: HttpUrl.Builder = base.newBuilder()
            .addPathSegment(resource)
            .addPathSegment(type)
        if (extra.isEmpty()) {
            builder.addPathSegment("$id.json")
        } else {
            builder.addPathSegment(id)
            // extraProps segment: percent-encode keys/values but keep the
            // literal = and & separators the protocol expects in the path.
            // URLEncoder emits form-style "+" for spaces; the protocol
            // examples use %20, so rewrite.
            val props = extra.entries.joinToString("&") { (k, v) ->
                encode(k) + "=" + encode(v)
            }
            builder.addEncodedPathSegment("$props.json")
        }
        return builder.build().toString()
    }

    private fun encode(raw: String): String =
        URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")

    /** GET [url] and decode the body with the lenient [AddonJson]. */
    private suspend fun <T> get(url: String?, strategy: DeserializationStrategy<T>): Result<T> {
        if (url == null) {
            return Result.failure(
                AddonRequestException("", AddonRequestException.Reason.INVALID_URL, "unparseable base URL")
            )
        }
        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            AddonRequestException(
                                url, AddonRequestException.Reason.HTTP_STATUS, "HTTP ${response.code}"
                            )
                        )
                    }
                    val body = response.body.string()
                    try {
                        Result.success(AddonJson.decodeFromString(strategy, body))
                    } catch (e: Exception) {
                        Result.failure(
                            AddonRequestException(
                                url, AddonRequestException.Reason.BAD_JSON,
                                e.message ?: "undecodable JSON", e
                            )
                        )
                    }
                }
            } catch (e: IOException) {
                Result.failure(
                    AddonRequestException(
                        url, AddonRequestException.Reason.NETWORK, e.message ?: "network error", e
                    )
                )
            }
        }
    }
}
