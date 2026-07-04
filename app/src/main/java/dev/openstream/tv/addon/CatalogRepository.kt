package dev.openstream.tv.addon

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalog fan-out support (MASTER_PLAN §4.1.5): resolves which catalogs the
 * home screen should show and fetches one catalog per call. The *parallel*
 * fan-out and incremental rendering live in the ViewModel — this class stays
 * a thin, testable protocol layer.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val client: AddonClient,
) {

    /**
     * One catalog of one installed addon. [key] is stable across refreshes so
     * lazy rows keep their identity (§5.7 stable keys).
     */
    data class CatalogRef(
        val addon: InstalledAddon,
        val catalog: ManifestCatalog,
    ) {
        val key: String get() = "${addon.manifestUrl}|${catalog.type}|${catalog.id}"
        val title: String get() = catalog.name.ifBlank { catalog.id }
    }

    /**
     * The home-screen row list: enabled addons in user order, each addon's
     * browsable-feed catalogs in manifest order (§4.1.7 ordering is sacred).
     */
    fun catalogRefs(addons: List<InstalledAddon>): List<CatalogRef> =
        addons
            .filter { it.enabled }
            .flatMap { addon ->
                addon.manifest.catalogs
                    .filter { it.isBrowsableFeed }
                    .map { CatalogRef(addon, it) }
            }

    /** Fetch one catalog's items (plain feed, no extras). */
    suspend fun fetch(ref: CatalogRef): Result<List<MetaItem>> =
        client.catalog(ref.addon.baseUrl, ref.catalog.type, ref.catalog.id)
            .map { metas -> metas.filter { it.isUsable } }
}
