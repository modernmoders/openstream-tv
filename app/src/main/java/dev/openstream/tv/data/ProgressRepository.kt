package dev.openstream.tv.data

import dev.openstream.tv.data.db.WatchProgressDao
import dev.openstream.tv.data.db.toDomain
import dev.openstream.tv.data.db.toEntity
import dev.openstream.tv.di.ApplicationScope
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Watch-progress persistence (MASTER_PLAN §10 Phase 2 unit 4). Keyed by
 * opaque [MediaRef] (§8.4), so future Live-TV sources can track progress
 * without touching this class.
 */
@Singleton
class ProgressRepository @Inject constructor(
    private val dao: WatchProgressDao,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    /** Continue Watching contents: resumable only, most recently watched first. */
    fun observeContinueWatching(): Flow<List<WatchProgress>> =
        dao.observeAll().map { entities -> continueWatching(entities.map { it.toDomain() }) }

    /** Every raw progress row — the Library screen's whole world (its
     *  grouping/filtering are pure functions in LibraryModel). */
    fun observeAllProgress(): Flow<List<WatchProgress>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Every stored progress row, keyed by [MediaRef.externalId] — for the
     * Details episode list, which shows a progress bar for a partly-watched
     * episode and a ✓ for a finished one (owner 2026-07-08). Episodes key on
     * their video id (§8.4), which is exactly the externalId, so a lookup is a
     * plain map get. Re-emits live, so returning from the player updates the
     * bars/checks without a reload.
     */
    fun observeProgressByExternalId(): Flow<Map<String, WatchProgress>> =
        dao.observeAll().map { entities ->
            entities.associate { it.externalId to it.toDomain() }
        }

    /**
     * Latest progress per TITLE, keyed "metaType/metaId" — for the poster
     * watched/progress indicators on browse tiles (owner round 14 #5). A
     * series has one row per episode; the tile can only carry one signal, so
     * the most recently watched row speaks for the whole show. Re-emits live,
     * so backing out of the player updates the tiles without a reload.
     */
    fun observeProgressByMetaKey(): Flow<Map<String, WatchProgress>> =
        dao.observeAll().map { entities -> latestByMetaKey(entities.map { it.toDomain() }) }

    suspend fun get(ref: MediaRef): WatchProgress? =
        dao.get(ref.sourceKind, ref.externalId)?.toDomain()

    /** Position to offer in a resume dialog, or null if not worth resuming. */
    suspend fun resumePositionFor(ref: MediaRef): Long? =
        get(ref)?.takeIf { isResumable(it) }?.positionMs

    /**
     * Live [resumePositionFor]: re-emits as playback saves land, so a screen
     * that survives on the back stack while the player runs never offers a
     * stale resume position.
     */
    fun observeResumePosition(ref: MediaRef): Flow<Long?> =
        dao.observe(ref.sourceKind, ref.externalId)
            .map { entity -> entity?.toDomain()?.takeIf { isResumable(it) }?.positionMs }

    suspend fun save(progress: WatchProgress) = dao.upsert(progress.toEntity())

    suspend fun clear(ref: MediaRef) = dao.delete(ref.sourceKind, ref.externalId)

    /** Fire-and-forget variants for callers that can't suspend (onCleared()). */
    fun saveAsync(progress: WatchProgress) {
        appScope.launch { save(progress) }
    }

    fun clearAsync(ref: MediaRef) {
        appScope.launch { clear(ref) }
    }

    companion object {
        /**
         * Resume-dialog floor. Deliberately low: when a stream is swapped
         * mid-episode (broken source, quality change) even 15 seconds in
         * should carry over — owner feedback 2026-07-04, "start from where
         * the other quit".
         */
        const val MIN_RESUME_POSITION_MS = 15_000L

        /** Continue Watching floor stays high: an accidental 20-second
         *  click must not squat on the home screen's first row. */
        const val MIN_CONTINUE_WATCHING_MS = 60_000L

        /** Past 95% = finished; offering "resume" into credits is noise. */
        const val WATCHED_FRACTION = 0.95

        private const val MAX_CONTINUE_WATCHING = 20

        fun isResumable(
            p: WatchProgress,
            minPositionMs: Long = MIN_RESUME_POSITION_MS,
        ): Boolean =
            p.positionMs >= minPositionMs &&
                p.durationMs > 0 &&
                p.positionMs < p.durationMs * WATCHED_FRACTION

        /**
         * Finished (or as good as): past [WATCHED_FRACTION]. A naturally-ended
         * episode is stored at position == duration (PlayerViewModel), so this
         * is the "show a ✓" test for the Details episode list. The inverse of
         * the resumable upper bound — the same 95% line splits "keep watching"
         * from "watched".
         */
        fun isWatched(p: WatchProgress): Boolean =
            p.durationMs > 0 && p.positionMs >= p.durationMs * WATCHED_FRACTION

        /** Key a browse tile can compute from its MetaItem: "metaType/metaId". */
        fun metaKey(metaType: String, metaId: String): String = "$metaType/$metaId"

        /** Latest row per title — the reduction behind [observeProgressByMetaKey]. */
        fun latestByMetaKey(all: List<WatchProgress>): Map<String, WatchProgress> =
            all.groupBy { metaKey(it.metaType, it.metaId) }
                .mapValues { (_, rows) -> rows.maxBy { it.updatedAt } }

        fun continueWatching(all: List<WatchProgress>): List<WatchProgress> =
            all.filter { isResumable(it, MIN_CONTINUE_WATCHING_MS) }
                .sortedByDescending { it.updatedAt }
                // One tile per show, not per episode (owner 2026-07-09): every
                // episode of a series shares the same [metaId] (its details
                // target), so keeping the first after the recency sort leaves
                // only the latest-watched episode of each series. Movies keep
                // their own unique metaId, so they're unaffected.
                .distinctBy { it.metaType to it.metaId }
                .take(MAX_CONTINUE_WATCHING)
    }
}
