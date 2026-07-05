package dev.openstream.tv.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android edge of §6.2 external playback: which players exist on this
 * device, spec → Intent, result Intent → plain map. All decisions live in
 * the pure ExternalPlayers.kt; nothing here is worth unit-testing.
 * The interface is the JVM-testability seam (AutoplayGateway precedent).
 */
interface ExternalPlayerPort {
    /** A player the user can actually pick right now. */
    data class Choice(val player: ExternalPlayer, val packageName: String?)

    fun installedPlayers(): List<Choice>
    fun intentFor(launch: ExternalLaunch): Intent
    fun resultExtras(data: Intent?): Map<String, Any?>
}

@Singleton
class ExternalPlayerLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExternalPlayerPort {

    /**
     * Installed players in §6.2 support order (VLC → MX → generic chooser).
     * GENERIC appears only when some other app handles video URLs — never
     * offer a chooser that would come up empty (§5.4: no dead-ends).
     */
    override fun installedPlayers(): List<ExternalPlayerPort.Choice> {
        val pm = context.packageManager
        val known = listOf(ExternalPlayer.VLC, ExternalPlayer.MX_PLAYER).mapNotNull { player ->
            player.packageNames.firstOrNull { pm.isInstalled(it) }?.let { ExternalPlayerPort.Choice(player, it) }
        }
        val knownPackages = known.mapNotNull { it.packageName }.toSet()
        val others = pm.queryIntentActivities(genericProbeIntent(), 0)
            .map { it.activityInfo.packageName }
            .any { it != context.packageName && it !in knownPackages }
        return if (others) known + ExternalPlayerPort.Choice(ExternalPlayer.GENERIC, null) else known
    }

    override fun intentFor(launch: ExternalLaunch): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(launch.url), launch.mimeType)
        launch.packageName?.let { intent.setPackage(it) }
        launch.extras.forEach { (key, value) ->
            when (value) {
                is ExtraValue.Str -> intent.putExtra(key, value.value)
                is ExtraValue.IntVal -> intent.putExtra(key, value.value)
                is ExtraValue.LongVal -> intent.putExtra(key, value.value)
                is ExtraValue.BoolVal -> intent.putExtra(key, value.value)
                is ExtraValue.StrArray -> intent.putExtra(key, value.values.toTypedArray())
                is ExtraValue.UriArray ->
                    intent.putExtra(key, value.urls.map(Uri::parse).toTypedArray())
            }
        }
        // GENERIC: force the chooser every time — "Other apps…" must never
        // silently bind to whatever won the last "always" prompt.
        return if (launch.packageName == null) {
            Intent.createChooser(intent, "Play with")
        } else {
            intent
        }
    }

    /** The result extras interpretExternalResult() cares about, untyped. */
    override fun resultExtras(data: Intent?): Map<String, Any?> {
        val bundle = data?.extras ?: return emptyMap()
        @Suppress("DEPRECATION") // untyped get(): MX uses int where VLC uses long
        return bundle.keySet().associateWith { bundle.get(it) }
    }

    private fun genericProbeIntent(): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(PROBE_URL), "video/*")

    private fun PackageManager.isInstalled(packageName: String): Boolean =
        runCatching { getPackageInfo(packageName, 0) }.isSuccess

    companion object {
        /** Any http video URL matches the same handler set our streams will. */
        private const val PROBE_URL = "https://example.com/probe.mp4"
    }
}
