package dev.openstream.tv.domain

/**
 * Video codecs we can tell apart — from a release label today, from a live
 * channel's declared format later. Domain-level (not autoplay) so playback
 * types like [PlayableSource] can carry one without knowing about addons.
 */
enum class VideoCodec { H264, HEVC, HEVC_10BIT, AV1, VP9 }

/**
 * Can a box with [hardwareCodecs] hardware-decode this codec? Unknown codec
 * (null) or unknown box capabilities (empty set) is TRUE: we only ever demote
 * to software what we positively know the hardware can't decode cleanly —
 * the onn boxes' vendor decoders emit macroblocked garbage on encodes they
 * don't really support (owner screenshots 2026-07-08/10).
 */
fun VideoCodec?.hardwareDecodable(hardwareCodecs: Set<VideoCodec>): Boolean {
    if (hardwareCodecs.isEmpty()) return true
    val codec = this ?: return true
    return codec in hardwareCodecs
}
