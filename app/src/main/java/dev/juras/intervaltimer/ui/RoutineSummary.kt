package dev.juras.intervaltimer.ui

import dev.juras.intervaltimer.engine.PhaseKind
import dev.juras.intervaltimer.engine.Routine
import dev.juras.intervaltimer.engine.Timeline
import dev.juras.intervaltimer.engine.formatSeconds

/** One line for the routine list, e.g. `8 × 40 s work / 20 s rest · 9:30`. */
fun Routine.summary(): String {
    val total = formatSeconds((Timeline.of(this).totalMs / 1000).toInt())
    val block = blocks.singleOrNull()
    val shape = if (block != null) {
        val work = block.phases.singleOrNull { it.kind == PhaseKind.WORK }
        val rest = block.phases.singleOrNull { it.kind == PhaseKind.REST }
        when {
            work != null && block.phases.size == 1 && block.rounds == 1 -> "${work.seconds} s"
            work != null && block.phases.size == 1 -> "${block.rounds} × ${work.seconds} s"
            work != null && rest != null && block.phases.size == 2 ->
                "${block.rounds} × ${work.seconds} s work / ${rest.seconds} s rest"
            else -> "${block.rounds} rounds"
        }
    } else {
        "${blocks.size} blocks"
    }
    return "$shape · $total"
}
