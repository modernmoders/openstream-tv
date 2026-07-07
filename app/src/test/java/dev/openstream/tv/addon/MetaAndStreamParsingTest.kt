package dev.openstream.tv.addon

import dev.openstream.tv.addon.fixtures.Fixtures
import dev.openstream.tv.domain.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaAndStreamParsingTest {

    @Test
    fun `catalog keeps movie series channel tv and unknown types`() {
        val metas = AddonJson
            .decodeFromString(CatalogResponse.serializer(), Fixtures.load("catalog_mixed"))
            .metas
        // MASTER_PLAN §8.1: nothing gets dropped
        assertEquals(5, metas.size)
        assertEquals(
            listOf(
                ContentType.MOVIE, ContentType.SERIES, ContentType.CHANNEL,
                ContentType.TV, ContentType.OTHER,
            ),
            metas.map { it.contentType },
        )
        // Raw type string survives for round-tripping/storage
        assertEquals("hologram", metas.last().type)
    }

    @Test
    fun `series meta parses videos with either title or name`() {
        val meta = AddonJson
            .decodeFromString(MetaResponse.serializer(), Fixtures.load("meta_series"))
            .meta!!
        assertEquals(ContentType.SERIES, meta.contentType)
        assertEquals(3, meta.videos.size)

        val pilot = meta.videos[0]
        assertEquals("Pilot", pilot.displayTitle)          // from "name" (Cinemeta style)
        assertEquals(1, pilot.season)
        assertEquals(1, pilot.episode)
        // Episode-row upgrade (owner request): thumbnail + a synopsis are
        // already lenient-parsed fields on Video — this locks that in.
        assertEquals("https://example.com/s01e01.jpg", pilot.thumbnail)
        assertEquals(
            "Monica and the gang introduce Rachel to the real world.",
            pilot.overview,
        )

        // Second episode carries neither — the row must degrade gracefully.
        assertNull(meta.videos[1].thumbnail)
        assertNull(meta.videos[1].overview)

        val e2 = meta.videos[1]
        assertEquals("The One with the Sonogram at the End", e2.displayTitle) // from "title"

        // defaultVideoId sent as explicit null -> coerced to default
        assertNull(meta.behaviorHints.defaultVideoId)
    }

    @Test
    fun `video with embedded streams is recognized`() {
        val meta = AddonJson
            .decodeFromString(MetaResponse.serializer(), Fixtures.load("meta_series"))
            .meta!!
        val embedded = meta.videos.first { it.id == "tt0108778:embedded" }
        // Spec: embedded streams mean "do not query stream addons for this video"
        assertTrue(embedded.streams.isNotEmpty())
    }

    @Test
    fun `streams parse hints and classify v1 playability`() {
        val streams = AddonJson
            .decodeFromString(StreamsResponse.serializer(), Fixtures.load("streams_full"))
            .streams
        assertEquals(5, streams.size)

        val uhd = streams[0]
        assertTrue(uhd.isPlayableInV1)
        assertEquals("fixture-2160p-webdl", uhd.behaviorHints.bingeGroup)
        assertTrue(uhd.behaviorHints.notWebReady)
        assertEquals("OpenStream", uhd.behaviorHints.proxyHeaders?.request?.get("User-Agent"))
        assertEquals(4_294_967_296L, uhd.behaviorHints.videoSize)
        assertEquals("Friends.S01E01.2160p.WEB-DL.mkv", uhd.behaviorHints.filename)
        assertEquals("eng", uhd.subtitles.single().lang)

        // Legacy "title" description field
        assertEquals("Friends S01E01 1080p (legacy title field)", streams[1].displayDescription)

        // Torrent + external: parsed, kept, but not v1-playable (MASTER_PLAN §4.1.4)
        val torrent = streams[2]
        assertFalse(torrent.isPlayableInV1)
        assertEquals("6366e0a6d44d49c8fa09c04669375c024e42bf7e", torrent.infoHash)
        assertFalse(streams[3].isPlayableInV1)

        // Bare stream with no hints doesn't blow up
        assertTrue(streams[4].isPlayableInV1)
        assertNull(streams[4].behaviorHints.bingeGroup)
    }

    @Test
    fun `string-valued array fields parse as single-element lists`() {
        // Regression: AIOMetadata sends director/writer as plain strings where
        // the spec says arrays (found live during the Phase 1 gate test).
        val sloppy = """
            {
                "metas": [{
                    "id": "tt0486655",
                    "type": "movie",
                    "name": "Stardust",
                    "director": "Matthew Vaughn",
                    "genres": ["Fantasy", "Adventure"],
                    "cast": null,
                    "_hasPoster": true,
                    "app_extras": { "cacheMaxAge": 3600 }
                }]
            }
        """
        val meta = AddonJson
            .decodeFromString(CatalogResponse.serializer(), sloppy)
            .metas.single()
        assertEquals(listOf("Matthew Vaughn"), meta.director)
        assertEquals(listOf("Fantasy", "Adventure"), meta.genres)
        assertTrue(meta.cast.isEmpty())
    }

    @Test
    fun `empty and blank responses yield empty lists`() {
        assertTrue(
            AddonJson.decodeFromString(StreamsResponse.serializer(), Fixtures.load("streams_empty"))
                .streams.isEmpty()
        )
        assertTrue(
            AddonJson.decodeFromString(StreamsResponse.serializer(), "{}").streams.isEmpty()
        )
    }
}
