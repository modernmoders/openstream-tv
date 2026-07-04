package dev.openstream.tv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One installed addon.
 *
 * Keyed by manifest URL, NOT by the manifest's `id`: the owner runs several
 * instances of the same addon (different AIOStreams configs share one id),
 * and they must coexist (MASTER_PLAN §4.2).
 *
 * The manifest is stored as JSON text rather than exploded into columns so a
 * manifest-shape change never needs a Room migration. It is re-encoded by us
 * at install time, so re-parsing it at read time cannot fail.
 */
@Entity(tableName = "installed_addon")
data class InstalledAddonEntity(
    @PrimaryKey val manifestUrl: String,
    val manifestJson: String,
    /** Position in the user's addon list; also the stream-group order (§4.1.7). */
    val sortOrder: Int,
    val enabled: Boolean,
)
