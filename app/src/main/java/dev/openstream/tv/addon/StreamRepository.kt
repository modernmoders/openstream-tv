package dev.openstream.tv.addon

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Stream resolution support (MASTER_PLAN §4.1.5): which addons to ask for a
 * video, and one fetch per addon. The parallel fan-out lives in the
 * ViewModel (same shape as home/search).
 *
 * Ordering rules (§4.1.7, sacred): addon groups follow the user's install
 * order; streams inside a group stay exactly as the addon returned them
 * (AIOStreams pre-sorts server-side — client re-sorting would destroy the
 * user's configured sort).
 */
@Singleton
class StreamRepository @Inject constructor(
    private val client: AddonClient,
    private val addonRepository: AddonRepository,
) {

    /** Enabled addons that declare streams for this type+id, in user order. */
    suspend fun streamAddons(type: String, videoId: String): List<InstalledAddon> =
        addonRepository.observeInstalled().first()
            .filter { it.enabled && it.manifest.declares("stream", type, videoId) }

    suspend fun fetch(
        addon: InstalledAddon,
        type: String,
        videoId: String,
    ): Result<List<Stream>> = client.streams(addon.baseUrl, type, videoId)
}
