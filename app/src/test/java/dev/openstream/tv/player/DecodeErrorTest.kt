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
    fun `network, server and container failures are not — only a different stream helps those`() {
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertFalse(isDecodeErrorCode(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
}
