package dev.openstream.tv.diagnostics

import dev.openstream.tv.data.DiagnosticsUploadPrefs
import dev.openstream.tv.data.ProfileSyncPrefs
import dev.openstream.tv.data.SetupConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Ships this box's App log to the owner's setup site once a day (owner ask
 * 2026-07-06: "have everyone's logs sent to me"). The site's index.php
 * (api=log mode) stores it as logs/<person>.log next to the profiles, so
 * the owner reads every box's log in one place — no TV visits, no adb.
 *
 * Same posture as ProfileSync: fire-and-forget on app start, silent on
 * failure (elder rule), retried next launch because the throttle only
 * advances on success. Only the ALREADY-SANITIZED log text leaves the box
 * (DiagnosticsLog strips URLs before they ever hit disk); the box
 * identifies itself by profile filename, never by link.
 */
@Singleton
class DiagnosticsUpload @Inject constructor(
    private val log: DiagnosticsLog,
    private val config: SetupConfig,
    private val profileSyncPrefs: ProfileSyncPrefs,
    private val prefs: DiagnosticsUploadPrefs,
    private val httpClient: OkHttpClient,
) {

    suspend fun uploadIfDue(nowMs: Long = System.currentTimeMillis()) {
        if (!config.isConfigured) return // open-source build without a site
        val link = profileSyncPrefs.get() ?: return // box was never connected
        val who = profileSlug(link.url) ?: return
        if (nowMs - prefs.lastUploadMs() < MIN_INTERVAL_MS) return
        // Oldest-first on the wire so the stored file reads top-to-bottom.
        val lines = log.read().asReversed()
        if (lines.isEmpty()) return // nothing to report — don't touch the site

        val body = FormBody.Builder()
            .add("api", "log")
            .add("who", who)
            .add("log", lines.joinToString("\n"))
            .build()
        val request = try {
            Request.Builder().url(config.setupUrl).post(body).build()
        } catch (e: IllegalArgumentException) {
            return // misconfigured setup URL — nothing sensible to do
        }
        val sent = withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { it.isSuccessful }
            } catch (e: IOException) {
                false
            }
        }
        if (sent) {
            prefs.saveLastUploadMs(nowMs)
        }
        // Failure is silent AND unlogged: recording "upload failed" would
        // grow the very log we failed to ship, every single launch.
    }

    companion object {
        /** One successful upload per day; unreachable site = retry next launch. */
        const val MIN_INTERVAL_MS: Long = 24 * 60 * 60 * 1000L

        /**
         * The person's profile filename stem ("adam-savoy-cYoj-ZKYTwQ" from
         * ".../adam-savoy-cYoj-ZKYTwQ.json") — a safe, token-free identity
         * the site can validate against its own profile files.
         */
        fun profileSlug(profileUrl: String): String? {
            val file = profileUrl.substringAfterLast('/').substringBefore('?')
            if (!file.endsWith(".json", ignoreCase = true)) return null
            val stem = file.dropLast(5)
            return stem.takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
        }
    }
}
