package dev.juras.intervaltimer.engine

import kotlinx.serialization.Serializable
import java.util.UUID

/** What a phase is for. Decides its default name and colour and the cue played when it starts. */
enum class PhaseKind(val defaultName: String, val defaultColor: Long) {
    WARM_UP("Get ready", 0xFFE65100),
    WORK("Work", 0xFF2E7D32),
    REST("Rest", 0xFFC62828),
    COOL_DOWN("Cool down", 0xFF1565C0),
}

/**
 * One timed step. [deltaSeconds] is added for every round after the first (never going below
 * [minSeconds]); [spoken] is read aloud at the start and defaults to [name].
 */
@Serializable
data class Phase(
    val name: String,
    val seconds: Int,
    val kind: PhaseKind = PhaseKind.WORK,
    val color: Long = kind.defaultColor,
    val deltaSeconds: Int = 0,
    val minSeconds: Int = 1,
    val spoken: String? = null,
) {
    fun secondsInRound(round: Int): Int =
        if (deltaSeconds == 0) seconds else maxOf(minSeconds, seconds + deltaSeconds * (round - 1))

    companion object {
        fun of(kind: PhaseKind, seconds: Int) = Phase(kind.defaultName, seconds, kind)
    }
}

/** [phases] run in order, [rounds] times; [restAfterSeconds] separates this block from the next. */
@Serializable
data class Block(
    val rounds: Int = 1,
    val phases: List<Phase>,
    val restAfterSeconds: Int = 0,
)

@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Long = PhaseKind.WORK.defaultColor,
    val warmUp: Phase? = null,
    val blocks: List<Block>,
    val coolDown: Phase? = null,
) {
    companion object {
        /** The common shape: [rounds] times work then rest. No rest when [restSeconds] is 0. */
        fun repeat(
            name: String,
            workSeconds: Int,
            restSeconds: Int,
            rounds: Int,
            getReadySeconds: Int = 10,
        ): Routine {
            val phases = buildList {
                add(Phase.of(PhaseKind.WORK, workSeconds))
                if (restSeconds > 0) add(Phase.of(PhaseKind.REST, restSeconds))
            }
            return Routine(
                name = name,
                warmUp = getReadySeconds.takeIf { it > 0 }?.let { Phase.of(PhaseKind.WARM_UP, it) },
                blocks = listOf(Block(rounds, phases)),
            )
        }

        /** A plain countdown. */
        fun timer(name: String, seconds: Int, getReadySeconds: Int = 5): Routine =
            repeat(name, seconds, restSeconds = 0, rounds = 1, getReadySeconds = getReadySeconds)
    }
}
