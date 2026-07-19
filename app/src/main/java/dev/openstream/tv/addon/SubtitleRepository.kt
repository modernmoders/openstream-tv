package dev.openstream.tv.addon

import dev.openstream.tv.diagnostics.DiagnosticsSink
import dev.openstream.tv.diagnostics.toDiagnosticDetail
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Addon subtitles fan-out (MASTER_PLAN §4.1 gap): until now the player only
 * used subtitles embedded in the chosen stream object; installed addons'
 * dedicated `subtitles` resource (AIOMetadata + AIOStreams both declare it,
 * OpenSubtitlesV3+/OpenSubtitles-backed) was never queried. Same shape as
 * [StreamRepository].
 */
@Singleton
class SubtitleRepository @Inject constructor(
    private val client: AddonClient,
    private val addonRepository: AddonRepository,
    private val diagnostics: DiagnosticsSink = DiagnosticsSink.NONE,
) {

    /** Enabled addons that declare subtitles for this type+id, in user order. */
    suspend fun subtitleAddons(type: String, videoId: String): List<InstalledAddon> =
        addonRepository.observeInstalled().first()
            .filter { it.enabled && it.manifest.declares("subtitles", type, videoId) }

    suspend fun fetch(
        addon: InstalledAddon,
        type: String,
        videoId: String,
    ): Result<List<Subtitle>> = client.subtitles(addon.baseUrl, type, videoId)
        .onFailure {
            diagnostics.record(
                "subtitles",
                "${addon.manifest.name} for $type $videoId: ${it.toDiagnosticDetail()}",
            )
        }

    /**
     * Every declaring addon queried in parallel, merged in the user's addon
     * order. A missing/broken source must never block or degrade playback —
     * failures are dropped here (already logged by [fetch]) rather than
     * surfaced to the caller.
     */
    suspend fun fetchAll(type: String, videoId: String): List<Subtitle> = coroutineScope {
        subtitleAddons(type, videoId)
            .map { addon -> async { fetch(addon, type, videoId).getOrElse { emptyList() } } }
            .map { it.await() }
            .flatten()
    }
}
