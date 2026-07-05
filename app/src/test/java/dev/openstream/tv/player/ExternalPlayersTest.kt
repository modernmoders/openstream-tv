package dev.openstream.tv.player

import dev.openstream.tv.domain.PlayableSource
import dev.openstream.tv.domain.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.2 external player intent contracts + §7.1.6 result interpretation.
 * VLC and MX speak different extras dialects — every field is table-tested
 * here so a regression in either can't hide behind the other.
 */
class ExternalPlayersTest {

    private val source = PlayableSource(
        url = "https://cdn.example/ep1.mkv",
        title = "S01E01 · Pilot",
        headers = mapOf("Authorization" to "Bearer t", "X-Debrid" to "1"),
        subtitles = listOf(
            SubtitleTrack("https://subs.example/en.srt", "en"),
            SubtitleTrack("https://subs.example/de.srt", "de"),
        ),
    )

    // ---- VLC launch ----

    @Test
    fun `vlc resume launch carries position, no from_start`() {
        val launch = buildExternalLaunch(
            ExternalPlayer.VLC, "org.videolan.vlc", source.copy(startPositionMs = 123_000)
        )
        assertEquals("org.videolan.vlc", launch.packageName)
        assertEquals("video/*", launch.mimeType)
        assertEquals(ExtraValue.LongVal(123_000), launch.extras["position"])
        assertNull(launch.extras["from_start"])
        assertEquals(ExtraValue.Str("S01E01 · Pilot"), launch.extras["title"])
    }

    @Test
    fun `vlc start-over launch forces from_start so VLC's own resume memory loses`() {
        val launch = buildExternalLaunch(ExternalPlayer.VLC, "org.videolan.vlc", source)
        assertEquals(ExtraValue.BoolVal(true), launch.extras["from_start"])
        assertNull(launch.extras["position"])
    }

    @Test
    fun `vlc gets first subtitle and flattened headers`() {
        val launch = buildExternalLaunch(ExternalPlayer.VLC, "org.videolan.vlc", source)
        assertEquals(ExtraValue.Str("https://subs.example/en.srt"), launch.extras["subtitles_location"])
        assertEquals(
            ExtraValue.StrArray(listOf("Authorization", "Bearer t", "X-Debrid", "1")),
            launch.extras["http-headers"],
        )
    }

    // ---- MX launch ----

    @Test
    fun `mx launch always requests a result and speaks int positions`() {
        val launch = buildExternalLaunch(
            ExternalPlayer.MX_PLAYER, "com.mxtech.videoplayer.ad", source.copy(startPositionMs = 45_000)
        )
        assertEquals(ExtraValue.BoolVal(true), launch.extras["return_result"])
        assertEquals(ExtraValue.IntVal(45_000), launch.extras["position"])
        assertEquals(
            ExtraValue.StrArray(listOf("Authorization", "Bearer t", "X-Debrid", "1")),
            launch.extras["headers"],
        )
    }

    @Test
    fun `mx subs arrays stay aligned url-to-name`() {
        val launch = buildExternalLaunch(ExternalPlayer.MX_PLAYER, "com.mxtech.videoplayer.ad", source)
        assertEquals(
            ExtraValue.UriArray(listOf("https://subs.example/en.srt", "https://subs.example/de.srt")),
            launch.extras["subs"],
        )
        assertEquals(ExtraValue.StrArray(listOf("en", "de")), launch.extras["subs.name"])
    }

    @Test
    fun `mx start-over omits position`() {
        val launch = buildExternalLaunch(ExternalPlayer.MX_PLAYER, "com.mxtech.videoplayer.ad", source)
        assertNull(launch.extras["position"])
    }

    // ---- Generic launch ----

    @Test
    fun `generic chooser sends no extras and no package`() {
        val launch = buildExternalLaunch(ExternalPlayer.GENERIC, null, source)
        assertNull(launch.packageName)
        assertTrue(launch.extras.isEmpty())
    }

    @Test
    fun `mime hint is respected when present`() {
        val launch = buildExternalLaunch(
            ExternalPlayer.GENERIC, null, source.copy(mimeTypeHint = "video/mp2t")
        )
        assertEquals("video/mp2t", launch.mimeType)
    }

    // ---- Result interpretation (§6.2 round-trip + §7.1.6 near-complete) ----

    @Test
    fun `non-ok result is unknown for every player`() {
        ExternalPlayer.entries.forEach { player ->
            assertEquals(
                ExternalOutcome.Unknown,
                interpretExternalResult(player, 0, mapOf("position" to 100_000)),
            )
        }
    }

    @Test
    fun `vlc mid-stream exit becomes progress`() {
        val outcome = interpretExternalResult(
            ExternalPlayer.VLC, EXTERNAL_RESULT_OK,
            mapOf("extra_position" to 600_000L, "extra_duration" to 2_400_000L),
        )
        assertEquals(ExternalOutcome.Progress(600_000, 2_400_000), outcome)
    }

    @Test
    fun `vlc near-complete return is finished — the Up Next trigger`() {
        val outcome = interpretExternalResult(
            ExternalPlayer.VLC, EXTERNAL_RESULT_OK,
            mapOf("extra_position" to 2_300_000L, "extra_duration" to 2_400_000L),
        )
        assertEquals(ExternalOutcome.Finished, outcome)
    }

    @Test
    fun `vlc zero position is unknown, not a progress wipe`() {
        val outcome = interpretExternalResult(
            ExternalPlayer.VLC, EXTERNAL_RESULT_OK,
            mapOf("extra_position" to 0L, "extra_duration" to 2_400_000L),
        )
        assertEquals(ExternalOutcome.Unknown, outcome)
    }

    @Test
    fun `mx playback_completion is finished even without position extras`() {
        val outcome = interpretExternalResult(
            ExternalPlayer.MX_PLAYER, EXTERNAL_RESULT_OK,
            mapOf("end_by" to "playback_completion"),
        )
        assertEquals(ExternalOutcome.Finished, outcome)
    }

    @Test
    fun `mx user exit mid-stream becomes progress, int extras read leniently`() {
        val outcome = interpretExternalResult(
            ExternalPlayer.MX_PLAYER, EXTERNAL_RESULT_OK,
            mapOf("end_by" to "user", "position" to 600_000, "duration" to 2_400_000),
        )
        assertEquals(ExternalOutcome.Progress(600_000, 2_400_000), outcome)
    }

    @Test
    fun `mx exit in the last 30 seconds is finished`() {
        val outcome = interpretExternalResult(
            ExternalPlayer.MX_PLAYER, EXTERNAL_RESULT_OK,
            mapOf("end_by" to "user", "position" to 2_380_000, "duration" to 2_400_000),
        )
        assertEquals(ExternalOutcome.Finished, outcome)
    }

    @Test
    fun `generic player returns nothing usable`() {
        val outcome = interpretExternalResult(
            ExternalPlayer.GENERIC, EXTERNAL_RESULT_OK,
            mapOf("position" to 600_000, "duration" to 2_400_000),
        )
        assertEquals(ExternalOutcome.Unknown, outcome)
    }

    @Test
    fun `missing or garbage extras are unknown`() {
        assertEquals(
            ExternalOutcome.Unknown,
            interpretExternalResult(ExternalPlayer.MX_PLAYER, EXTERNAL_RESULT_OK, emptyMap()),
        )
        assertEquals(
            ExternalOutcome.Unknown,
            interpretExternalResult(
                ExternalPlayer.VLC, EXTERNAL_RESULT_OK,
                mapOf("extra_position" to "not a number"),
            ),
        )
    }

    // ---- Near-complete rule boundaries (§7.1.6) ----

    @Test
    fun `near-complete at exactly 95 percent`() {
        // Duration long enough that the 30s-tail rule can't mask the fraction rule
        assertTrue(isNearComplete(950_000, 1_000_000))
        assertFalse(isNearComplete(949_999, 1_000_000))
    }

    @Test
    fun `near-complete inside the 30-second tail regardless of fraction`() {
        // 50% watched but only 20s left (short video): still "done enough"
        assertTrue(isNearComplete(20_000, 40_000))
        assertFalse(isNearComplete(20_000, 120_000))
    }

    @Test
    fun `mx pro is preferred over free when both package names listed`() {
        // §6.2: someone who paid for Pro expects it to be the one launched
        assertEquals(
            listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad"),
            ExternalPlayer.MX_PLAYER.packageNames,
        )
    }
}
