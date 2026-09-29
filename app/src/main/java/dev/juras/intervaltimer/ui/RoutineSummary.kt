package dev.juras.intervaltimer.ui

import dev.juras.intervaltimer.engine.PhaseKind
import dev.juras.intervaltimer.engine.Routine
import dev.juras.intervaltimer.engine.Timeline
import dev.juras.intervaltimer.engine.formatSeconds

/** The shape of the routine in words, e.g. `8 × 40 s work / 20 s rest`. */
fun Routine.shape(): String {
    val block = blocks.singleOrNull() ?: return "${blocks.size} blocks"
    val work = block.phases.singleOrNull { it.kind == PhaseKind.WORK }
    val rest = block.phases.singleOrNull { it.kind == PhaseKind.REST }
    val workText = work?.let { if (it.manual) "until done" else "${it.seconds} s" }
    return when {
        work != null && block.phases.size == 1 && block.rounds == 1 -> workText!!
        work != null && block.phases.size == 1 -> "${block.rounds} × $workText"
        // "until done" already says it is work, and the card is narrow, so no "work" after it.
        work != null && rest != null && block.phases.size == 2 ->
            "${block.rounds} × $workText${if (work.manual) "" else " work"} / ${rest.seconds} s rest"
        else -> "${block.rounds} rounds"
    }
}

/** Total length, or that there is none because some phase lasts until the user is done. */
fun Routine.total(): String =
    if (isOpenEnded) "no limit" else formatSeconds((Timeline.of(this).totalMs / 1000).toInt())

/** One line, e.g. `8 × 40 s work / 20 s rest · 9:30`. */
fun Routine.summary(): String = "${shape()} · ${total()}"

/** The name, or the shape when the routine has no name: an unnamed routine is labelled by its content. */
fun Routine.title(): String = name.ifBlank { shape() }

/** Under the title: the rest of [summary] that the title doesn't already say. */
fun Routine.subtitle(): String = if (name.isBlank()) total() else summary()
