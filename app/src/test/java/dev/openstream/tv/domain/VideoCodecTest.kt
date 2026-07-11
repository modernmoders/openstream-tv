package dev.openstream.tv.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCodecTest {

    private val onnLikeBox = setOf(VideoCodec.H264, VideoCodec.HEVC, VideoCodec.VP9)

    @Test
    fun `codec the box decodes is hardware decodable`() {
        assertTrue(VideoCodec.H264.hardwareDecodable(onnLikeBox))
        assertTrue(VideoCodec.HEVC.hardwareDecodable(onnLikeBox))
    }

    @Test
    fun `codec the box lacks is not hardware decodable`() {
        // THE rainbow-artifact case: HEVC 10-bit on a box whose decoder only
        // claims 8-bit HEVC.
        assertFalse(VideoCodec.HEVC_10BIT.hardwareDecodable(onnLikeBox))
        assertFalse(VideoCodec.AV1.hardwareDecodable(onnLikeBox))
    }

    @Test
    fun `unknown codec is trusted to hardware`() {
        assertTrue((null as VideoCodec?).hardwareDecodable(onnLikeBox))
    }

    @Test
    fun `unknown box capabilities trust everything to hardware`() {
        assertTrue(VideoCodec.HEVC_10BIT.hardwareDecodable(emptySet()))
        assertTrue((null as VideoCodec?).hardwareDecodable(emptySet()))
    }
}
