package dev.juras.intervaltimer.engine

/** `m:ss`, or `h:mm:ss` from an hour up. */
fun formatSeconds(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
