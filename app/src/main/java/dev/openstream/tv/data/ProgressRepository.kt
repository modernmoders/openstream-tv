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

    suspend fun get(ref: MediaRef): WatchProgress? =
        dao.get(ref.sourceKind, ref.externalId)?.toDomain()

    /** Position to offer in a resume dialog, or null if not worth resuming. */
    suspend fun resumePositionFor(ref: MediaRef): Long? =
        get(ref)?.takeIf { isResumable(it) }?.positionMs

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
        /** Under a minute in = not meaningfully started; don't offer resume. */
        const val MIN_RESUME_POSITION_MS = 60_000L

        /** Past 95% = finished; offering "resume" into credits is noise. */
        const val WATCHED_FRACTION = 0.95

        private const val MAX_CONTINUE_WATCHING = 20

        fun isResumable(p: WatchProgress): Boolean =
            p.positionMs >= MIN_RESUME_POSITION_MS &&
                p.durationMs > 0 &&
                p.positionMs < p.durationMs * WATCHED_FRACTION

        fun continueWatching(all: List<WatchProgress>): List<WatchProgress> =
            all.filter(::isResumable)
                .sortedByDescending { it.updatedAt }
                .take(MAX_CONTINUE_WATCHING)
    }
}
