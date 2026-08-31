package dev.openstream.tv.data

/**
 * How subtitles are drawn (owner request 2026-08-30: "a subtitle size and
 * background/color editor").
 *
 * Deliberately framework-free: every option carries plain numbers (a height
 * fraction, ARGB ints, an edge kind) so the choices are unit-testable on the
 * JVM and the player is the only place that knows about media3's
 * `CaptionStyleCompat`. Adding a colour or a size here is a one-line change
 * with no Android types involved.
 *
 * Sizes are a FRACTION OF SCREEN HEIGHT, not sp: a TV is watched from across
 * the room, and Android's system caption sizing doesn't exist on most of these
 * boxes. media3's `SubtitleView.setFractionalTextSize` uses the same unit, and
 * 0.0533 is its own default — that is what NORMAL matches exactly, so a box
 * that never opens this screen looks exactly as it always did.
 */
enum class SubtitleTextSize(val label: String, val fractionOfHeight: Float) {
    SMALL("Small", 0.0400f),
    NORMAL("Normal", 0.0533f),
    LARGE("Large", 0.0700f),
    HUGE("Extra large", 0.0900f),
}

/**
 * Caption colours. These four are the broadcast-caption set (the same ones a
 * TV's own subtitle menu offers) — high contrast against video, and familiar
 * to anyone who has used closed captions on a cable box.
 */
enum class SubtitleTextColor(val label: String, val argb: Int) {
    WHITE("White", 0xFFFFFFFF.toInt()),
    YELLOW("Yellow", 0xFFFFEB3B.toInt()),
    CYAN("Cyan", 0xFF4DD0E1.toInt()),
    GREEN("Green", 0xFF81C784.toInt()),
}

/** How a subtitle is separated from the picture behind it. */
enum class SubtitleEdge { NONE, OUTLINE, DROP_SHADOW }

/** Fully transparent ARGB — "no fill behind the words". Top-level rather than
 *  in [SubtitleBackdrop]'s companion: an enum entry cannot read its own
 *  companion while the entries are still being constructed. */
const val SUBTITLE_TRANSPARENT: Int = 0x00000000

/**
 * What sits behind the words. A box is the most readable over busy video; an
 * outline/shadow keeps more of the picture visible. [backgroundArgb] is the
 * fill painted directly behind the glyphs (transparent = none).
 */
enum class SubtitleBackdrop(
    val label: String,
    val backgroundArgb: Int,
    val edge: SubtitleEdge,
) {
    OUTLINE("Black outline", SUBTITLE_TRANSPARENT, SubtitleEdge.OUTLINE),
    SHADOW("Soft shadow", SUBTITLE_TRANSPARENT, SubtitleEdge.DROP_SHADOW),
    DIM_BOX("Dark box", 0xA6000000.toInt(), SubtitleEdge.NONE),
    SOLID_BOX("Solid black box", 0xFF000000.toInt(), SubtitleEdge.NONE),
    NONE("Nothing — plain text", SUBTITLE_TRANSPARENT, SubtitleEdge.NONE),
}

/** The edge/outline colour. Black is the only one that reads on every video. */
const val SUBTITLE_EDGE_ARGB: Int = 0xFF000000.toInt()

/**
 * The full subtitle look. Defaults reproduce media3's out-of-the-box
 * appearance (white, normal size, outlined) so this feature changes nothing
 * for a box whose owner never visits the screen.
 */
data class SubtitleStyle(
    val size: SubtitleTextSize = SubtitleTextSize.NORMAL,
    val color: SubtitleTextColor = SubtitleTextColor.WHITE,
    val backdrop: SubtitleBackdrop = SubtitleBackdrop.OUTLINE,
) {
    /** True when nothing has been customised — used to word the screen. */
    val isDefault: Boolean get() = this == SubtitleStyle()
}
