package dev.openstream.tv.addon

import dev.openstream.tv.diagnostics.DiagnosticsSink
import dev.openstream.tv.diagnostics.toDiagnosticDetail
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Resolves full metadata for one item (MASTER_PLAN §4.1.6):
 * 1. ask each enabled addon that DECLARES meta for this type+id (§4.1.3),
 *    in the user's addon order, first usable answer wins;
 * 2. fall back to Cinemeta for IMDb ids — stream-only addons often match
 *    items that no installed catalog addon can describe.
 */
@Singleton
class MetaRepository @Inject constructor(
    private val client: AddonClient,
    private val addonRepository: AddonRepository,
    @Named("cinemetaBaseUrl") private val cinemetaBaseUrl: String,
    private val diagnostics: DiagnosticsSink = DiagnosticsSink.NONE,
) {

    suspend fun resolveMeta(type: String, id: String): Result<MetaItem> {
        val addons = addonRepository.observeInstalled().first().filter { it.enabled }

        var lastError: Throwable? = null
        for (addon in addons) {
            if (!addon.manifest.declares("meta", type, id)) continue
            client.meta(addon.baseUrl, type, id)
                .onSuccess { return Result.success(it.dedupedVideos()) }
                .onFailure { lastError = it }
        }

        // Cinemeta knows every IMDb id; its manifest declares idPrefixes=["tt"].
        if (id.startsWith("tt")) {
            client.meta(cinemetaBaseUrl, type, id)
                .onSuccess { return Result.success(it.dedupedVideos()) }
                .onFailure { lastError = it }
        }

        val error = lastError ?: AddonRequestException(
            id, AddonRequestException.Reason.INVALID_MANIFEST,
            "no installed addon can describe this item"
        )
        // Only the FINAL failure is logged: a stream-only addon skipping an
        // item is normal, every addon failing to describe it is a diagnosis.
        diagnostics.record("meta", "couldn't describe $type $id: ${error.toDiagnosticDetail()}")
        return Result.failure(error)
    }

    /** Same trust boundary as CatalogRepository's distinctBy: addons DO repeat
     *  ids (owner crash 2026-07-10), and Details keys episode rows by video id
     *  — duplicate lazy keys are fatal. */
    private fun MetaItem.dedupedVideos(): MetaItem =
        if (videos.isEmpty()) this else copy(videos = videos.distinctBy { it.id })
}
