package dev.openstream.tv.addon

import java.io.File
import java.io.IOException
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Disk-cache policy for addon traffic (owner feedback 2026-07-05: a cold
 * start refetched every catalog over the network and "took forever" on the
 * onn boxes — 32-bit, 2–3 GB RAM, and one AIOMetadata instance is 60+
 * catalog requests).
 *
 * Policy, per URL path:
 * - `/catalog/` and `/meta/` GETs: cacheable for [ttlSeconds] (addons rarely
 *   send cache headers, so we impose our own on the response). Within the
 *   TTL, OkHttp serves straight from disk — zero network on a relaunch.
 *   When the network fails, any stale copy is served instead of an error.
 * - `/stream/` is NEVER cached: AIOStreams-style responses embed tokenized,
 *   expiring URLs — a stale one plays nothing.
 * - Everything else (manifests, subtitles): untouched. Addons keep whatever
 *   caching semantics they declare; manifests must stay fresh for installs.
 */
class AddonHttpCache(private val ttlSeconds: Long = DEFAULT_TTL_SECONDS) {

    /**
     * Network-level interceptor: stamps our TTL onto successful catalog/meta
     * responses so OkHttp's cache stores them, and forbids storing stream
     * responses no matter what the addon says.
     */
    val responsePolicy = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        when {
            isStream(request) -> response.newBuilder()
                .header("Cache-Control", "no-store")
                .build()
            isCacheable(request) && response.isSuccessful -> response.newBuilder()
                .removeHeader("Pragma")
                // CDN-fronted addons (Cinemeta = Cloudflare) send Age in the
                // thousands; left in place it makes our max-age stale ON
                // ARRIVAL, and every launch refetches. Freshness must count
                // from when WE received the response.
                .removeHeader("Age")
                .removeHeader("Expires")
                .header("Cache-Control", "public, max-age=$ttlSeconds")
                .build()
            else -> response
        }
    }

    /**
     * Application-level interceptor: when the network path fails (addon
     * down, TV offline), retry the same catalog/meta request cache-only with
     * unlimited staleness — last-known data beats an error chip.
     */
    val staleIfOffline = Interceptor { chain ->
        val request = chain.request()
        try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (!isCacheable(request)) throw e
            val fromCache = request.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(Int.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                )
                .build()
            // 504 Unsatisfiable Request when nothing is cached — the client
            // surfaces that like any other HTTP failure.
            chain.proceed(fromCache)
        }
    }

    private fun isCacheable(request: okhttp3.Request): Boolean =
        request.method == "GET" && CACHEABLE_PATH.containsMatchIn(request.url.encodedPath)

    private fun isStream(request: okhttp3.Request): Boolean =
        STREAM_PATH.containsMatchIn(request.url.encodedPath)

    companion object {
        /** 30 min: catalogs change daily-ish; relaunches are minutes apart. */
        const val DEFAULT_TTL_SECONDS = 1800L

        private const val MAX_SIZE_BYTES = 50L * 1024 * 1024

        private val CACHEABLE_PATH = Regex("/(catalog|meta)/")
        private val STREAM_PATH = Regex("/stream/")

        fun diskCache(cacheDir: File): Cache =
            Cache(File(cacheDir, "addon_http"), MAX_SIZE_BYTES)
    }
}
