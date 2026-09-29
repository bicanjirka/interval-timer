package dev.juras.intervaltimer.ui

import dev.juras.intervaltimer.engine.Block
import dev.juras.intervaltimer.engine.Phase
import dev.juras.intervaltimer.engine.PhaseKind
import dev.juras.intervaltimer.engine.Routine
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineSummaryTest {
    @Test
    fun workRestRepeatShowsRoundsAndBothLengths() {
        val routine = Routine.repeat("x", workSeconds = 40, restSeconds = 20, rounds = 8, getReadySeconds = 0)

        // 8 x 40 + 7 x 20 = 460 s
        assertEquals("8 × 40 s work / 20 s rest · 7:40", routine.summary())
    }

    @Test
    fun plainTimerShowsJustItsLength() {
        assertEquals("300 s · 5:00", Routine.timer("x", seconds = 300, getReadySeconds = 0).summary())
    }

    @Test
    fun roundsWithoutRestShowRoundsAndWork() {
        val routine = Routine.repeat("x", workSeconds = 30, restSeconds = 0, rounds = 4, getReadySeconds = 0)

        assertEquals("4 × 30 s · 2:00", routine.summary())
    }

    @Test
    fun workUntilDoneShowsSetsAndNoTimeLimit() {
        val routine = Routine.untilDone("x", restSeconds = 90, sets = 5, getReadySeconds = 0)

        assertEquals("5 × until done work / 90 s rest · no time limit", routine.summary())
    }

    @Test
    fun severalBlocksShowTheBlockCount() {
        val block = Block(phases = listOf(Phase.of(PhaseKind.WORK, 10)))
        val routine = Routine(name = "x", blocks = listOf(block, block))

        assertEquals("2 blocks · 0:20", routine.summary())
    }
}
