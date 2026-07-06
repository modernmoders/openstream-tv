package dev.openstream.tv.addon

import dev.openstream.tv.data.ProfileLink
import dev.openstream.tv.data.ProfileSyncPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Everything a setup profile resolves to, checked and ready to confirm.
 * Shared by the Connect flow (type-your-name setup) and the expert
 * Add-addon screen (paste a setup link) so both install identically.
 */
data class ProfilePlan(
    /** The hosted profile URL — remembered on install for ProfileSync. */
    val profileUrl: String,
    val profileName: String,
    val entries: List<Entry>,
) {
    /**
     * One resolved profile row. Never carries the manifest URL into display
     * text — addon URLs stay off the screen (they embed personal tokens).
     */
    data class Entry(
        val displayName: String,
        /** Normalized manifest URL to install, or null when not installable. */
        val manifestUrl: String?,
        /** "v1.2.3" for good entries, a friendly error for bad ones. */
        val detail: String,
    ) {
        val ok: Boolean get() = manifestUrl != null
    }

    val installableCount: Int get() = entries.count { it.ok }
}

/**
 * Resolves a setup link into a [ProfilePlan] and installs a confirmed plan
 * (MASTER_PLAN §4.1.1: preview first, persist only on explicit confirm).
 */
@Singleton
class ProfileInstaller @Inject constructor(
    private val client: AddonClient,
    private val profileClient: SetupProfileClient,
    private val repository: AddonRepository,
    private val profileSyncPrefs: ProfileSyncPrefs,
) {

    /** Fetch the profile, then every entry's manifest in parallel — the plan
     *  keeps the profile's order (§4.1.7: profile order = install order). */
    suspend fun plan(profileUrl: String): Result<ProfilePlan> =
        profileClient.fetch(profileUrl).map { profile ->
            coroutineScope {
                val entries = profile.addons.mapIndexed { index, entry ->
                    async {
                        val fallbackName = entry.name.ifBlank { "Addon ${index + 1}" }
                        val url = AddonUrls.normalizeManifestUrl(entry.url)
                            ?: return@async ProfilePlan.Entry(fallbackName, null, "Not a valid addon URL")
                        client.fetchManifest(url).fold(
                            onSuccess = {
                                ProfilePlan.Entry(entry.name.ifBlank { it.name }, url, "v${it.version}")
                            },
                            onFailure = { ProfilePlan.Entry(fallbackName, null, "Not answering right now") },
                        )
                    }
                }.awaitAll()
                ProfilePlan(profileUrl, profile.name.ifBlank { "Setup link" }, entries)
            }
        }

    /**
     * Install every good entry, in plan order; returns how many succeeded.
     * Any success remembers the setup link so ProfileSync keeps this box in
     * step with the hosted profile from then on (remote management, #25).
     */
    suspend fun install(plan: ProfilePlan): Int {
        val urls = plan.entries.mapNotNull { it.manifestUrl }
        val ok = urls.map { repository.install(it) }.count { it.isSuccess } // sequential: order matters
        if (ok > 0) {
            profileSyncPrefs.save(ProfileLink(plan.profileUrl, urls.toSet(), System.currentTimeMillis()))
        }
        return ok
    }
}
