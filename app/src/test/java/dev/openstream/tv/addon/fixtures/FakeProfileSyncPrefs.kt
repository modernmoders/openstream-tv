package dev.openstream.tv.addon.fixtures

import dev.openstream.tv.data.ProfileLink
import dev.openstream.tv.data.ProfileSyncPrefs

/** In-memory stand-in for the DataStore-backed profile-link prefs. */
class FakeProfileSyncPrefs(var link: ProfileLink? = null) : ProfileSyncPrefs {
    override suspend fun get(): ProfileLink? = link
    override suspend fun save(link: ProfileLink) {
        this.link = link
    }

    override suspend fun clear() {
        link = null
    }
}
