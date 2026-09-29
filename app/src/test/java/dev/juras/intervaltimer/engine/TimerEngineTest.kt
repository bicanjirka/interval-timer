package dev.juras.intervaltimer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeClock(var nowMs: Long = 1_000) : MonotonicClock {
    override fun nowMs() = nowMs

    fun advanceSeconds(seconds: Double) {
        nowMs += (seconds * 1000).toLong()
    }
}

class TimerEngineTest {
    private val clock = FakeClock()

    private fun engine(routine: Routine) = TimerEngine(routine, clock).also { it.start() }

    private val threeByTen = Routine.repeat("3x10/5", workSeconds = 10, restSeconds = 5, rounds = 3, getReadySeconds = 0)

    @Test
    fun engineIsReadyAndShowsTheFirstSegmentBeforeItIsStarted() {
        val engine = TimerEngine(threeByTen, clock)

        val state = engine.state()

        assertEquals(Status.READY, state.status)
        assertEquals("Work", state.segment?.name)
        assertEquals(10, state.segmentSeconds)
    }

    @Test
    fun remainingTimeCountsDownWithTheClock() {
        val engine = engine(threeByTen)

        clock.advanceSeconds(3.5)
        val state = engine.state()

        assertEquals(Status.RUNNING, state.status)
        assertEquals(6_500, state.segmentRemainingMs)
        assertEquals(7, state.segmentSeconds)
    }

    @Test
    fun totalRemainingTimeIgnoresTheRestAfterTheLastRound() {
        val engine = engine(threeByTen)

        val state = engine.state()

        // 3 x 10 s work + 2 x 5 s rest; the trailing rest is dropped.
        assertEquals(40_000, state.totalRemainingMs)
        assertEquals(5, state.segmentCount)
    }

    @Test
    fun segmentChangesWhenItsTimeIsUp() {
        val engine = engine(threeByTen)

        clock.advanceSeconds(10.0)
        val state = engine.state()

        assertEquals("Rest", state.segment?.name)
        assertEquals("Work", state.next?.name)
        assertEquals(1, state.segment?.round)
    }

    @Test
    fun roundCounterAdvancesAfterEachWorkAndRestPair() {
        val engine = engine(threeByTen)

        clock.advanceSeconds(15.0)
        val state = engine.state()

        assertEquals("Work", state.segment?.name)
        assertEquals(2, state.segment?.round)
        assertEquals(3, state.segment?.rounds)
    }

    @Test
    fun routineFinishesAfterTheLastWorkPhase() {
        val engine = engine(threeByTen)

        clock.advanceSeconds(40.0)
        val state = engine.state()

        assertEquals(Status.FINISHED, state.status)
        assertNull(state.segment)
        assertEquals(0, state.totalRemainingMs)
    }

    @Test
    fun aLatePollLandsInTheRightSegment() {
        val engine = engine(threeByTen)

        clock.advanceSeconds(32.0)
        val state = engine.state()

        assertEquals("Work", state.segment?.name)
        assertEquals(3, state.segment?.round)
        assertEquals(8, state.segmentSeconds)
    }

    @Test
    fun pausingFreezesTimeAndResumingContinuesFromThere() {
        val engine = engine(threeByTen)
        clock.advanceSeconds(4.0)

        engine.pause()
        clock.advanceSeconds(100.0)
        val paused = engine.state()
        engine.resume()
        clock.advanceSeconds(1.0)
        val resumed = engine.state()

        assertEquals(Status.PAUSED, paused.status)
        assertEquals(6_000, paused.segmentRemainingMs)
        assertEquals(Status.RUNNING, resumed.status)
        assertEquals(5_000, resumed.segmentRemainingMs)
    }

    @Test
    fun pausingTwiceOrResumingWhileRunningChangesNothing() {
        val engine = engine(threeByTen)
        clock.advanceSeconds(2.0)

        engine.resume()
        engine.pause()
        engine.pause()
        clock.advanceSeconds(50.0)
        engine.resume()
        engine.resume()
        clock.advanceSeconds(1.0)

        assertEquals(7_000, engine.state().segmentRemainingMs)
    }

    @Test
    fun skipJumpsToTheStartOfTheNextSegment() {
        val engine = engine(threeByTen)
        clock.advanceSeconds(3.0)

        engine.skip()
        val state = engine.state()

        assertEquals("Rest", state.segment?.name)
        assertEquals(5_000, state.segmentRemainingMs)
        assertEquals(30_000, state.totalRemainingMs)
    }

    @Test
    fun skipWhilePausedMovesOnButStaysPaused() {
        val engine = engine(threeByTen)
        clock.advanceSeconds(3.0)
        engine.pause()

        engine.skip()
        clock.advanceSeconds(10.0)
        val state = engine.state()

        assertEquals(Status.PAUSED, state.status)
        assertEquals("Rest", state.segment?.name)
        assertEquals(5_000, state.segmentRemainingMs)
    }

    @Test
    fun skippingTheLastSegmentFinishesTheRoutine() {
        val engine = engine(Routine.timer("5 s", seconds = 5, getReadySeconds = 0))

        engine.skip()

        assertEquals(Status.FINISHED, engine.state().status)
    }

    @Test
    fun skippingOrResumingAfterFinishDoesNothing() {
        val engine = engine(Routine.timer("5 s", seconds = 5, getReadySeconds = 0))
        clock.advanceSeconds(6.0)

        engine.skip()
        engine.resume()

        assertEquals(Status.FINISHED, engine.state().status)
    }

    @Test
    fun warmUpAndCoolDownBracketTheBlocks() {
        val routine = Routine(
            name = "full",
            warmUp = Phase.of(PhaseKind.WARM_UP, 5),
            blocks = listOf(Block(rounds = 1, phases = listOf(Phase.of(PhaseKind.WORK, 10)))),
            coolDown = Phase.of(PhaseKind.COOL_DOWN, 20),
        )
        val engine = engine(routine)

        val names = generateSequence(0.0) { it + 1.0 }.take(36).map {
            clock.nowMs = 1_000 + (it * 1000).toLong()
            engine.state().segment?.name
        }.distinct().toList()

        assertEquals(listOf("Get ready", "Work", "Cool down", null), names)
    }

    @Test
    fun restBetweenBlocksFallsBetweenBlocksOnly() {
        val routine = Routine(
            name = "two blocks",
            blocks = listOf(
                Block(rounds = 1, phases = listOf(Phase.of(PhaseKind.WORK, 10)), restAfterSeconds = 30),
                Block(rounds = 1, phases = listOf(Phase.of(PhaseKind.WORK, 20)), restAfterSeconds = 30),
            ),
        )
        val timeline = Timeline.of(routine)

        assertEquals(listOf("Work", "Rest", "Work"), timeline.segments.map { it.name })
        assertEquals(listOf(1, 0, 2), timeline.segments.map { it.block })
    }

    @Test
    fun perRoundDeltaChangesLengthButNeverGoesBelowTheMinimum() {
        val routine = Routine(
            name = "shrinking rest",
            blocks = listOf(
                Block(
                    rounds = 4,
                    phases = listOf(
                        Phase.of(PhaseKind.WORK, 10),
                        Phase("Rest", 10, PhaseKind.REST, deltaSeconds = -4, minSeconds = 3),
                        Phase.of(PhaseKind.WORK, 1),
                    ),
                ),
            ),
        )

        val rests = Timeline.of(routine).segments.filter { it.kind == PhaseKind.REST }

        assertEquals(listOf(10_000L, 6_000L, 3_000L, 3_000L), rests.map { it.durationMs })
    }

    @Test
    fun zeroLengthPhasesAreLeftOut() {
        val routine = Routine(
            name = "zero",
            blocks = listOf(Block(rounds = 2, phases = listOf(Phase.of(PhaseKind.WORK, 0)))),
        )

        assertEquals(emptyList<Segment>(), Timeline.of(routine).segments)
    }

    @Test
    fun emptyRoutineIsFinishedRightAfterStart() {
        val engine = engine(Routine(name = "nothing", blocks = emptyList()))

        assertEquals(Status.FINISHED, engine.state().status)
    }

    @Test
    fun plainTimerIsOneWorkSegmentAfterGetReady() {
        val timeline = Timeline.of(Routine.timer("5 min", seconds = 300))

        assertEquals(listOf("Get ready", "Work"), timeline.segments.map { it.name })
        assertEquals(305_000, timeline.totalMs)
    }

    @Test
    fun restIsOptionalInTheRepeatFactory() {
        val timeline = Timeline.of(Routine.repeat("no rest", 30, restSeconds = 0, rounds = 3, getReadySeconds = 0))

        assertEquals(listOf("Work", "Work", "Work"), timeline.segments.map { it.name })
    }
}
