package dev.openstream.tv.addon

import dev.openstream.tv.data.db.InstalledAddonDao
import dev.openstream.tv.data.db.InstalledAddonEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages the user's installed addons (MASTER_PLAN §4.1.1): fetch + validate a
 * manifest by URL, persist it, and expose the ordered list.
 *
 * All addon network access for install goes through [AddonClient]; reads are
 * Room [Flow]s so UI reacts to install/uninstall/reorder instantly.
 */
@Singleton
class AddonRepository @Inject constructor(
    private val client: AddonClient,
    private val dao: InstalledAddonDao,
) {

    /** Installed addons in user order. Unparseable rows are skipped defensively. */
    fun observeInstalled(): Flow<List<InstalledAddon>> =
        dao.observeAll().map { entities -> entities.mapNotNull { it.toDomain() } }

    /**
     * Fetch, validate, and persist the addon at [rawUrl] (user input; accepts
     * stremio:// links). Re-installing an existing URL refreshes its manifest
     * but keeps the user's ordering and enabled flag.
     */
    suspend fun install(rawUrl: String): Result<InstalledAddon> {
        val manifestUrl = AddonUrls.normalizeManifestUrl(rawUrl)
            ?: return Result.failure(
                AddonRequestException(
                    rawUrl, AddonRequestException.Reason.INVALID_URL,
                    "not an http(s)/stremio manifest.json URL"
                )
            )
        return client.fetchManifest(manifestUrl).map { manifest ->
            val existing = dao.get(manifestUrl)
            val entity = InstalledAddonEntity(
                manifestUrl = manifestUrl,
                manifestJson = AddonJson.encodeToString(Manifest.serializer(), manifest),
                sortOrder = existing?.sortOrder ?: nextSortOrder(),
                enabled = existing?.enabled ?: true,
            )
            dao.upsert(entity)
            checkNotNull(entity.toDomain())
        }
    }

    suspend fun uninstall(manifestUrl: String) = dao.delete(manifestUrl)

    /** Removes every installed addon (owner request: a "Reset this TV" escape
     *  hatch in Settings back to the Welcome/name-setup screen, no adb needed). */
    suspend fun uninstallAll() {
        dao.getAll().forEach { dao.delete(it.manifestUrl) }
    }

    suspend fun setEnabled(manifestUrl: String, enabled: Boolean) =
        dao.setEnabled(manifestUrl, enabled)

    /** Persist a full user reordering; [orderedManifestUrls] is the new order. */
    suspend fun reorder(orderedManifestUrls: List<String>) {
        orderedManifestUrls.forEachIndexed { index, url -> dao.setSortOrder(url, index) }
    }

    private suspend fun nextSortOrder(): Int =
        (dao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1

    private fun InstalledAddonEntity.toDomain(): InstalledAddon? {
        val manifest = runCatching {
            AddonJson.decodeFromString(Manifest.serializer(), manifestJson)
        }.getOrNull() ?: return null // stored by us, so this "never" happens — but never crash
        return InstalledAddon(manifestUrl, manifest, enabled, sortOrder)
    }
}
