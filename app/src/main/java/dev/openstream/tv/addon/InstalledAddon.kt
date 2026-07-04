package dev.openstream.tv.addon

/**
 * An installed addon as the rest of the app sees it: the parsed manifest plus
 * the user's per-addon state. Identity is [manifestUrl] (MASTER_PLAN §4.2).
 */
data class InstalledAddon(
    val manifestUrl: String,
    val manifest: Manifest,
    val enabled: Boolean,
    val sortOrder: Int,
) {
    val baseUrl: String get() = AddonUrls.baseUrlOf(manifestUrl)
}
