package dev.openstream.tv.ui.components

/** Milliseconds → "1:23:45" / "23:45" clock text (player overlay, resume dialog). */
fun Long.asClock(): String {
    if (this <= 0) return "0:00"
    val totalSeconds = this / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
