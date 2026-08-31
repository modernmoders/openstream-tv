package dev.openstream.tv.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decode-class errors earn a same-stream software retry; nothing else does. */
class DecodeErrorTest {

    @Test
    fun `decoder failures are decode errors`() {
        assertTrue(isDecodeErrorCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertTrue(isDecodeErrorCode(PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED))
        assertTrue(isDecodeErrorCode(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertTrue(isDecodeErrorCode(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES))
        assertTrue(isDecodeErrorCode(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
    }

    @Test
    fun `network, server and container failures are not decode errors`() {
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }

    @Test
    fun `dropped connections earn a wait-and-rejoin on the same stream`() {
        assertTrue(isNetworkErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(isNetworkErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertTrue(isNetworkErrorCode(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertTrue(isNetworkErrorCode(PlaybackException.ERROR_CODE_TIMEOUT))
    }

    @Test
    fun `a dead link or broken file is not worth waiting for — walk to the next stream`() {
        // A 404/403 and a malformed container don't get better with time, so
        // these must NOT trigger the reconnect path (owner 2026-07-28).
        assertFalse(isNetworkErrorCode(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(isNetworkErrorCode(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertFalse(isNetworkErrorCode(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertFalse(isNetworkErrorCode(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertFalse(isNetworkErrorCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
    }

    @Test
    fun `decode and network classes never overlap`() {
        // Both branches sit in the same `when` in PlayerViewModel; an error
        // that satisfied both would make the ordering there load-bearing.
        val codes = listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )
        codes.forEach { assertFalse(isDecodeErrorCode(it) && isNetworkErrorCode(it)) }
    }
}
