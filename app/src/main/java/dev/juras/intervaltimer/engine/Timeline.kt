package dev.juras.intervaltimer.engine

/**
 * One stretch of a running routine, with its length already resolved (deltas applied).
 * [rounds] is 0 for steps that belong to no round (warm-up, cool-down, rest between blocks).
 * A [manual] segment has no length (durationMs is 0): it lasts until the user ends it.
 */
data class Segment(
    val name: String,
    val kind: PhaseKind,
    val color: Long,
    val durationMs: Long,
    val spoken: String,
    val block: Int = 1,
    val blocks: Int = 1,
    val round: Int = 0,
    val rounds: Int = 0,
    val manual: Boolean = false,
) {
    /** Only work can be rated for effort: not get-ready, rest or cool-down. */
    val rateable: Boolean get() = kind == PhaseKind.WORK
}

/** A routine flattened into consecutive [segments]. */
class Timeline(val segments: List<Segment>) {
    /** Total length of the timed segments; manual ones add nothing. */
    val totalMs: Long = segments.sumOf { it.durationMs }

    companion object {
        /**
         * Rest phases at the end of a block's last round are dropped: the next block, the block
         * rest or the end of the routine follows instead. Zero-length steps are skipped.
         */
        fun of(routine: Routine): Timeline {
            val blockCount = routine.blocks.size
            val segments = buildList {
                routine.warmUp?.let { add(segment(it, 1, blockCount)) }
                routine.blocks.forEachIndexed { b, block ->
                    for (round in 1..block.rounds) {
                        val phases = if (round == block.rounds) {
                            block.phases.dropLastWhile { it.kind == PhaseKind.REST }
                        } else {
                            block.phases
                        }
                        phases.forEach { add(segment(it, round, blockCount, b + 1, block.rounds)) }
                    }
                    if (b < blockCount - 1 && block.restAfterSeconds > 0) {
                        add(segment(Phase.of(PhaseKind.REST, block.restAfterSeconds), 1, blockCount))
                    }
                }
                routine.coolDown?.let { add(segment(it, 1, blockCount)) }
            }
            return Timeline(segments.filter { it.durationMs > 0 || it.manual })
        }

        /** [block] and [rounds] stay 0 for steps outside any round. */
        private fun segment(phase: Phase, round: Int, blocks: Int, block: Int = 0, rounds: Int = 0) = Segment(
            name = phase.name,
            kind = phase.kind,
            color = phase.color,
            durationMs = phase.secondsInRound(round) * 1000L,
            spoken = phase.spoken ?: phase.name,
            block = block,
            blocks = blocks,
            round = if (rounds > 0) round else 0,
            rounds = rounds,
            manual = phase.manual,
        )
    }
}
