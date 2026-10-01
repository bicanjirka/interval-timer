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
 *
 * A [manual] phase has no length: it counts up, for information only, until the user says
 * they are done, and then the routine moves on. [seconds] is ignored for it.
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
    val manual: Boolean = false,
) {
    fun secondsInRound(round: Int): Int = when {
        manual -> 0
        deltaSeconds == 0 -> seconds
        else -> maxOf(minSeconds, seconds + deltaSeconds * (round - 1))
    }

    /** Switches between timed and until-done; a phase that becomes timed gets a real length instead of the 0 a manual one stores. */
    fun withManual(on: Boolean) = copy(manual = on, seconds = if (!on && seconds < 1) DEFAULT_SECONDS else seconds)

    companion object {
        const val DEFAULT_SECONDS = 40

        fun of(kind: PhaseKind, seconds: Int) = Phase(kind.defaultName, seconds, kind)

        /** Work of unknown length, ended by the user. */
        fun untilDone(name: String = PhaseKind.WORK.defaultName) =
            Phase(name, seconds = 0, kind = PhaseKind.WORK, manual = true)
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
    /** Whether the running screen asks for a 1–10 effort rating during each work phase. */
    val rateEffort: Boolean = false,
) {
    /** True when some phase lasts until the user ends it, so the total time can't be known. */
    val isOpenEnded: Boolean
        get() = listOfNotNull(warmUp, coolDown).any { it.manual } || blocks.any { b -> b.phases.any { it.manual } }

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

        /** Work as long as it takes (shown counting up), then a fixed rest, for [sets] sets. */
        fun untilDone(name: String, restSeconds: Int, sets: Int, getReadySeconds: Int = 5): Routine =
            Routine(
                name = name,
                warmUp = getReadySeconds.takeIf { it > 0 }?.let { Phase.of(PhaseKind.WARM_UP, it) },
                blocks = listOf(Block(sets, listOf(Phase.untilDone(), Phase.of(PhaseKind.REST, restSeconds)))),
            )
    }
}
