package dev.openstream.tv.data

/**
 * Where this build phones home for one-step setup (owner directive
 * 2026-07-06: people type their NAME, the app does the rest — nobody
 * copies links). Injected as a plain value object so everything that
 * needs it stays JVM-testable.
 *
 * The real values come from BuildConfig, which reads the gitignored
 * local.properties (`setup.url`, `setup.brand`): the setup domain unlocks
 * name→profile lookups, so it must never reach the public repo
 * (CLAUDE.md security). Builds without the keys simply hide the
 * name-setup flow and keep the classic add-addon path.
 */
data class SetupConfig(
    /** Setup site base URL, or "" when this build has no setup site. */
    val setupUrl: String,
    /** What the app calls itself on friendly screens. */
    val brand: String,
) {
    val isConfigured: Boolean get() = setupUrl.isNotBlank()
}
