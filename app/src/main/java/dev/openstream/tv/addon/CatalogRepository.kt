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
     * The home/discover catalog list: enabled addons in user order, each
     * addon's browsable-feed catalogs in manifest order (§4.1.7 ordering is
     * sacred). The owner curates these lists server-side (merged catalogs in
     * AIOMetadata) — the app's job is to honor that curation, not re-sort it.
     */
    fun catalogRefs(addons: List<InstalledAddon>): List<CatalogRef> =
        addons
            .filter { it.enabled }
            .flatMap { addon ->
                addon.manifest.catalogs
                    .filter { it.isBrowsableFeed }
                    .map { CatalogRef(addon, it) }
            }

    /** Catalogs that can serve a text search (modern or legacy notation). */
    fun searchRefs(addons: List<InstalledAddon>): List<CatalogRef> =
        addons
            .filter { it.enabled }
            .flatMap { addon ->
                addon.manifest.catalogs
                    .filter { it.supportsExtra("search") }
                    .map { CatalogRef(addon, it) }
            }

    /** Fetch one catalog's items, optionally with extras (search/skip/genre). */
    suspend fun fetch(
        ref: CatalogRef,
        extra: Map<String, String> = emptyMap(),
    ): Result<List<MetaItem>> =
        client.catalog(ref.addon.baseUrl, ref.catalog.type, ref.catalog.id, extra)
            .map { metas -> metas.filter { it.isUsable } }
}
