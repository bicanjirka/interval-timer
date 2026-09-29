package dev.juras.intervaltimer.engine

/**
 * One stretch of a running routine, with its length already resolved (deltas applied).
 * [rounds] is 0 for steps that belong to no round (warm-up, cool-down, rest between blocks).
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
)

/** A routine flattened into consecutive [segments]; [startsMs] holds where each one begins. */
class Timeline(val segments: List<Segment>) {
    val startsMs: LongArray = LongArray(segments.size).also { starts ->
        var at = 0L
        segments.forEachIndexed { i, s -> starts[i] = at; at += s.durationMs }
    }
    val totalMs: Long = segments.sumOf { it.durationMs }

    /** Index of the segment running at [elapsedMs], or [segments].size once everything is done. */
    fun indexAt(elapsedMs: Long): Int {
        if (elapsedMs >= totalMs) return segments.size
        val i = startsMs.indexOfLast { it <= elapsedMs }
        return i.coerceAtLeast(0)
    }

    fun endMs(index: Int): Long = startsMs[index] + segments[index].durationMs

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
            return Timeline(segments.filter { it.durationMs > 0 })
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
        )
    }
}
