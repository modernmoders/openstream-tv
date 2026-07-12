package dev.openstream.tv.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.BuildConfig
import dev.openstream.tv.data.SetupConfig
import dev.openstream.tv.diagnostics.DiagnosticsSink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Self-update over the setup site (owner 2026-07-11: Rachael's box leaves the
 * house — adb stops being a deploy path, so the app must fetch its own next
 * build). `tools/publish_update.sh` puts `version.json` + the APK under
 * `<setup-url>/app/`; this checks, downloads, and hands the APK to Android's
 * PackageInstaller. Android then shows its own "Install?" confirmation — one
 * OK on the remote. Deliberately NOT silent: a same-signature one-click
 * update is the strongest thing a normal app is allowed to do.
 *
 * Elder rule applies: every failure collapses to "no update offered" +
 * a diagnostics line. An updater that can crash the app it updates is worse
 * than no updater.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val setupConfig: SetupConfig,
    private val diagnostics: DiagnosticsSink,
) {

    sealed interface CheckResult {
        data class UpdateAvailable(val manifest: UpdateManifest) : CheckResult
        data object UpToDate : CheckResult
        data object Unreachable : CheckResult
    }

    /** True once this process already ran its automatic launch check. */
    @Volatile
    var autoCheckDone: Boolean = false

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        if (!setupConfig.isConfigured) return@withContext CheckResult.Unreachable
        val url = setupConfig.setupUrl.trimEnd('/') + "/app/version.json"
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK) // a stale "no update" defeats the point
            .header("Accept", "application/json")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    diagnostics.record("update", "version check HTTP ${response.code}")
                    return@withContext CheckResult.Unreachable
                }
                val manifest = parseUpdateManifest(response.body.string())
                    ?: return@withContext CheckResult.Unreachable.also {
                        diagnostics.record("update", "version.json unparseable")
                    }
                val current = BuildConfig.VERSION_CODE.toLong()
                diagnostics.record(
                    "update",
                    "check: installed=$current remote=${manifest.versionCode} (${manifest.versionName})",
                )
                if (manifest.isNewerThan(current)) {
                    CheckResult.UpdateAvailable(manifest)
                } else {
                    CheckResult.UpToDate
                }
            }
        } catch (e: Exception) {
            diagnostics.record("update", "version check failed: ${e.message}")
            CheckResult.Unreachable
        }
    }

    /**
     * Download the APK and commit a PackageInstaller session. Returns false on
     * any failure before the system confirmation appears; after commit the
     * system owns the flow ([UpdateStatusReceiver] surfaces its answer).
     */
    suspend fun downloadAndInstall(manifest: UpdateManifest): Boolean = withContext(Dispatchers.IO) {
        val apk = File(context.cacheDir, "update.apk")
        try {
            httpClient.newCall(Request.Builder().url(manifest.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    diagnostics.record("update", "APK download HTTP ${response.code}")
                    return@withContext false
                }
                apk.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            }
            diagnostics.record("update", "APK downloaded (${apk.length() / 1024} KB), committing install")
            commitInstallSession(apk)
            true
        } catch (e: Exception) {
            diagnostics.record("update", "install failed: ${e.message}")
            false
        } finally {
            // The session copied the bytes; the cache file is done either way.
            apk.delete()
        }
    }

    private fun commitInstallSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateStatusReceiver::class.java)
                .setAction(UpdateStatusReceiver.ACTION_UPDATE_STATUS)
            // MUTABLE: the system writes the status extras into this intent.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}
