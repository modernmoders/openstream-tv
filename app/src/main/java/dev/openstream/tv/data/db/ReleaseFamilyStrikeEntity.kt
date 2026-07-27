package dev.openstream.tv.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * One strike record: "this release family misbehaved for this series".
 *
 * A release FAMILY is the episode-number-stripped token set of a stream's
 * label ([dev.openstream.tv.autoplay.StreamCascade.familyKey]) — the thing
 * that stays identical across a season's episodes ("Show 1080p WEB GROUP").
 * When an auto-picked stream fails to play, or opens with no English audio,
 * its family gets a strike here; later episodes of the SAME series rank that
 * family below clean candidates, so a glitchy encode isn't re-tried on every
 * episode of a binge (owner backlog 2026-07-26).
 *
 * Never blocks anything outright: struck families stay in the list, just
 * last. Manual Expert picks neither write strikes nor get filtered.
 */
@Entity(tableName = "release_family_strikes", primaryKeys = ["seriesKey", "familyKey"])
data class ReleaseFamilyStrikeEntity(
    /** The show this strike applies to — the meta id (movies: the video id). */
    val seriesKey: String,
    /** Canonical family key — see [dev.openstream.tv.autoplay.StreamCascade.familyKey]. */
    val familyKey: String,
    val strikes: Int,
    val lastStrikeAt: Long,
)

@Dao
interface ReleaseFamilyStrikeDao {

    @Query("SELECT familyKey FROM release_family_strikes WHERE seriesKey = :seriesKey")
    suspend fun familyKeysFor(seriesKey: String): List<String>

    /** Returns 0 when no row exists yet — caller inserts instead. */
    @Query(
        "UPDATE release_family_strikes SET strikes = strikes + 1, lastStrikeAt = :now " +
            "WHERE seriesKey = :seriesKey AND familyKey = :familyKey"
    )
    suspend fun bumpStrike(seriesKey: String, familyKey: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ReleaseFamilyStrikeEntity)
}
