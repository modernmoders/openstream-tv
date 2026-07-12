package dev.openstream.tv.player.skip

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bundled IMDb→MAL map (asset `anime_imdb_mal.json`, ~110 KB, generated
 * by `tools/build_anime_map.py`). Loaded and parsed once on first use — the
 * skip lookup already runs on Dispatchers.IO, so the lazy read never touches
 * the main thread. A missing or corrupt asset just means an empty map: no
 * skip button, never a crash (elder rule of the whole skip feature).
 */
@Singleton
class BundledAnimeMap @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val map: Map<String, List<ImdbMalEntry>> by lazy {
        runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.map(::parseAnimeMap).getOrDefault(emptyMap())
    }

    /** The MAL entries for one IMDb series id ("tt0409591"), or empty. */
    fun entriesFor(imdbId: String): List<ImdbMalEntry> = map[imdbId].orEmpty()

    private companion object {
        const val ASSET_NAME = "anime_imdb_mal.json"
    }
}
