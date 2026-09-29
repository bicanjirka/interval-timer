package dev.juras.intervaltimer.engine

/** What one round took: [workMs] in work phases (real time, so it varies for work until done), [restMs] in rest phases. */
data class RoundStat(val block: Int, val round: Int, val workMs: Long, val restMs: Long)

/** The numbers shown when a routine is finished, worked out from [TimerState.results]. */
class WorkoutSummary(results: List<SegmentResult>) {
    val totalMs: Long = results.sumOf { it.actualMs }
    val workMs: Long = results.filter { it.segment.kind == PhaseKind.WORK }.sumOf { it.actualMs }

    /** One entry per round, in the order they were run; warm-up, cool-down and block rests belong to none. */
    val rounds: List<RoundStat> = results
        .filter { it.segment.round > 0 }
        .groupBy { it.segment.block to it.segment.round }
        .map { (key, parts) ->
            RoundStat(
                block = key.first,
                round = key.second,
                workMs = parts.filter { it.segment.kind == PhaseKind.WORK }.sumOf { it.actualMs },
                restMs = parts.filter { it.segment.kind == PhaseKind.REST }.sumOf { it.actualMs },
            )
        }

    val averageWorkMs: Long get() = if (rounds.isEmpty()) 0 else rounds.sumOf { it.workMs } / rounds.size
}
