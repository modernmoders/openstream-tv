package dev.openstream.tv.addon

import dev.openstream.tv.data.ProfileLink
import dev.openstream.tv.data.ProfileSyncPrefs
import dev.openstream.tv.diagnostics.DiagnosticsSink
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * What one re-sync run would change. Pure data so the diff logic is
 * table-testable without any I/O.
 */
data class SyncPlan(
    /** Manifest URLs to install, in profile order (§4.1.7: order matters). */
    val install: List<String>,
    /** Manifest URLs to uninstall — always a subset of previously managed. */
    val remove: List<String>,
    /** The new managed set to persist after applying. */
    val managed: Set<String>,
)

/**
 * Diff the hosted profile against the box. The profile is the source of
 * truth for the addons IT manages; anything the user installed by hand
 * (not in [previouslyManaged]) is invisible to the sync and never removed.
 * A user-removed profile addon is reinstalled — deliberate: the owner's
 * hosted file always wins, that's the whole point of remote management.
 */
fun planSync(
    profileUrls: List<String>,
    installedUrls: Set<String>,
    previouslyManaged: Set<String>,
): SyncPlan {
    val wanted = profileUrls.distinct()
    val wantedSet = wanted.toSet()
    return SyncPlan(
        install = wanted.filter { it !in installedUrls },
        remove = previouslyManaged.filter { it in installedUrls && it !in wantedSet },
        managed = wantedSet,
    )
}

/**
 * Remote addon management (owner directive 2026-07-05): on every app start,
 * re-fetch the setup link this box was installed from and bring the installed
 * addons in line with it. The owner edits one hosted JSON file; far-away
 * boxes follow on their next launch.
 *
 * Failure policy is the elder rule ("don't show them errors — log them"):
 * every failure here is silent to the user and logged; the box simply keeps
 * the addons it already has, which always still work.
 */
@Singleton
class ProfileSync @Inject constructor(
    private val prefs: ProfileSyncPrefs,
    private val profileClient: SetupProfileClient,
    private val repository: AddonRepository,
    private val diagnostics: DiagnosticsSink = DiagnosticsSink.NONE,
) {

    /**
     * Fetch-and-apply, throttled to one SUCCESSFUL sync per [MIN_INTERVAL_MS].
     * An unreachable profile is retried on the next launch (lastSync only
     * advances on success), so "fix the file, restart the app" always works.
     */
    suspend fun syncIfDue(nowMs: Long = System.currentTimeMillis()) {
        val link = prefs.get() ?: return // box was never set up from a link
        if (nowMs - link.lastSyncMs < MIN_INTERVAL_MS) return
        val profile = profileClient.fetch(link.url).getOrElse { e ->
            // No URLs in logs — they embed tokens (CLAUDE.md rule).
            diagnostics.record(
                "profile-sync",
                "re-sync skipped: ${(e as? AddonRequestException)?.reason ?: e::class.simpleName}",
            )
            return
        }
        val wanted = profile.addons.mapNotNull { AddonUrls.normalizeManifestUrl(it.url) }
        if (wanted.isEmpty()) return // isUsable guarantees ≥1; belt and suspenders
        val installed = repository.observeInstalled().first().mapTo(mutableSetOf()) { it.manifestUrl }
        val plan = planSync(wanted, installed, link.managedUrls)
        var failed = 0
        // Sequential like the install-all flow: install order = profile order.
        plan.install.forEach { url -> repository.install(url).onFailure { failed++ } }
        plan.remove.forEach { repository.uninstall(it) }
        if (plan.install.isNotEmpty() || plan.remove.isNotEmpty()) {
            diagnostics.record(
                "profile-sync",
                "re-synced: ${plan.install.size - failed} installed ($failed failed), ${plan.remove.size} removed"
            )
        }
        // Failed installs stay wanted-but-absent, so the next sync retries them.
        prefs.save(ProfileLink(link.url, plan.managed, nowMs))
    }

    companion object {
        /**
         * Short on purpose: the owner's support flow is "I changed your
         * addons — restart the app", which must beat the throttle. This only
         * limits successful syncs, one tiny JSON GET each.
         */
        const val MIN_INTERVAL_MS: Long = 15 * 60 * 1000
    }
}
