package dev.openstream.tv.autoplay

import dev.openstream.tv.addon.AddonClient
import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.addon.MetaRepository
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Everything AutoplayController needs from the addon world, as a seam:
 * the real repositories are concrete classes, and the controller's timing
 * rules must be testable with fakes (§9.2).
 */
interface AutoplayGateway {
    /** Full meta for the playing item, or null (autoplay silently stands down). */
    suspend fun resolveMeta(type: String, id: String): MetaItem?

    /** Stream-declaring addons for the next episode, user order (§4.1.7). */
    suspend fun streamAddons(type: String, videoId: String): List<InstalledAddon>

    /** One addon's streams; failures are an empty list (the §7.1 fan-out counts responses, not successes). */
    suspend fun fetchStreams(addon: InstalledAddon, type: String, videoId: String): List<Stream>
}

@Singleton
class AddonAutoplayGateway @Inject constructor(
    private val metaRepository: MetaRepository,
    private val streamRepository: StreamRepository,
    /** Long read timeout: §7.1 patience must survive addons the interactive 15s budget would kill. */
    @Named("patientAddonClient") private val patientClient: AddonClient,
) : AutoplayGateway {

    override suspend fun resolveMeta(type: String, id: String): MetaItem? =
        metaRepository.resolveMeta(type, id).getOrNull()

    override suspend fun streamAddons(type: String, videoId: String): List<InstalledAddon> =
        streamRepository.streamAddons(type, videoId)

    override suspend fun fetchStreams(addon: InstalledAddon, type: String, videoId: String): List<Stream> =
        patientClient.streams(addon.baseUrl, type, videoId).getOrElse { emptyList() }
}

/**
 * Hand-off slot for the addon-world context of the playing stream (which
 * addon, which Stream) — autoplay's tier-1/2 ranking input. Lives here, NOT
 * in PlaybackRequest: StreamMapping is the only place addon types may cross
 * into player types (§8.2), and playback must keep working when this is null.
 */
@Singleton
class AutoplayOriginHolder @Inject constructor() {
    var origin: StreamCascade.CurrentStream? = null
}
