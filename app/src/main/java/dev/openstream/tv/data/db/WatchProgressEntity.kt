package dev.openstream.tv.data.db

import androidx.room.Entity
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress

/**
 * Watch progress, keyed by (sourceKind, externalId) — the MediaRef — per
 * MASTER_PLAN §8.4: never keyed by IMDb id, never assumes addon content.
 */
@Entity(tableName = "watch_progress", primaryKeys = ["sourceKind", "externalId"])
data class WatchProgressEntity(
    val sourceKind: String,
    val externalId: String,
    val metaId: String,
    val metaType: String,
    val title: String,
    val poster: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

fun WatchProgressEntity.toDomain() = WatchProgress(
    ref = MediaRef(sourceKind, externalId),
    metaId = metaId,
    metaType = metaType,
    title = title,
    poster = poster,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAt = updatedAt,
)

fun WatchProgress.toEntity() = WatchProgressEntity(
    sourceKind = ref.sourceKind,
    externalId = ref.externalId,
    metaId = metaId,
    metaType = metaType,
    title = title,
    poster = poster,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAt = updatedAt,
)
