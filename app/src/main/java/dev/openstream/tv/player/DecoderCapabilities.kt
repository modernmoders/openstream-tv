package dev.openstream.tv.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import dev.openstream.tv.domain.VideoCodec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What video codecs THIS box can HARDWARE-decode, read once from the platform
 * [MediaCodecList]. Feeds [dev.openstream.tv.autoplay.StreamCascade] so the
 * stream picker and auto-play prefer streams the box plays cleanly over ones
 * that force the software decoder / macroblock — the onn boxes' HEVC-10bit
 * problem that made the software-player toggle necessary (owner 2026-07-09).
 *
 * Best-effort: if the query throws or finds nothing the set is empty, and
 * StreamCascade then treats every stream as playable (behaviour unchanged from
 * before this existed — we only ever DEMOTE codecs we positively know the box
 * can't hardware-decode).
 */
@Singleton
class DecoderCapabilities @Inject constructor() {

    /** Hardware-decodable video codecs. Queried lazily, once, then cached. */
    val hardwareVideoCodecs: Set<VideoCodec> by lazy {
        runCatching { query() }.getOrDefault(emptySet())
    }

    private fun query(): Set<VideoCodec> {
        val out = mutableSetOf<VideoCodec>()
        for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
            if (info.isEncoder || !isHardware(info)) continue
            for (type in info.supportedTypes) {
                when (type.lowercase()) {
                    "video/avc" -> out += VideoCodec.H264
                    "video/hevc" -> {
                        out += VideoCodec.HEVC
                        if (supportsProfile(info, type, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)) {
                            out += VideoCodec.HEVC_10BIT
                        }
                    }
                    "video/av01" -> out += VideoCodec.AV1
                    "video/x-vnd.on2.vp9" -> out += VideoCodec.VP9
                }
            }
        }
        return out
    }

    /** True hardware decoder, not a software fallback. */
    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            // Pre-Q: software decoders are named OMX.google.* / c2.android.*.
            val n = info.name.lowercase()
            !n.startsWith("omx.google.") && !n.startsWith("c2.android.")
        }

    private fun supportsProfile(info: MediaCodecInfo, type: String, profile: Int): Boolean =
        runCatching {
            info.getCapabilitiesForType(type).profileLevels.any { it.profile == profile }
        }.getOrDefault(false)
}
