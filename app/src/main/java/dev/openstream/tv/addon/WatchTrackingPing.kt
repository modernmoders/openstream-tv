package dev.openstream.tv.addon

import dev.openstream.tv.di.ApplicationScope
import dev.openstream.tv.diagnostics.DiagnosticsSink
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Tell subtitle-declaring addons that playback started.
 *
 * A catalog/meta addon gets exactly ONE playback-time signal from a
 * Stremio-protocol client: the `subtitles/{type}/{id}.json` request the player
 * fires when a video starts. AIOMetadata's "Watch Tracking → Trakt Check-in"
 * hangs off that request — which is why check-ins worked in Stremio but never
 * in this app: we exposed [AddonClient.subtitles] and then never called it
 * (owner 2026-07-09).
 *
 * Fire-and-forget on the application scope: the response is discarded (we don't
 * render subtitles yet) and every failure is swallowed to the diagnostics log —
 * a missed check-in must never break playback.
 */
@Singleton
class WatchTrackingPing @Inject constructor(
    private val addonRepository: AddonRepository,
    private val client: AddonClient,
    @ApplicationScope private val appScope: CoroutineScope,
    private val diagnostics: DiagnosticsSink = DiagnosticsSink.NONE,
) {

    /** Call once per video when playback actually begins. */
    fun playbackStarted(type: String, videoId: String) {
        if (type.isBlank() || videoId.isBlank()) return
        appScope.launch {
            val targets = addonRepository.observeInstalled().first()
                .filter { it.enabled && it.manifest.resource("subtitles") != null }
            targets.forEach { addon ->
                client.subtitles(addon.baseUrl, type, videoId)
                    .onFailure {
                        diagnostics.record(
                            "watch-tracking",
                            "check-in ping failed for ${addon.manifest.name}: ${it::class.simpleName}",
                        )
                    }
            }
        }
    }
}
