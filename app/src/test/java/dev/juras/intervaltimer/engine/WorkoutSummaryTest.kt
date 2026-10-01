package dev.juras.intervaltimer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSummaryTest {
    private val clock = FakeClock()

    private fun engine(routine: Routine) = TimerEngine(routine, clock).also { it.start() }

    @Test
    fun resultsAreEmptyUntilTheRoutineIsFinished() {
        val engine = engine(Routine.timer("t", 10, getReadySeconds = 0))
        clock.advanceSeconds(4.0)

        assertTrue(engine.state().results.isEmpty())
    }

    @Test
    fun timedSegmentsReportTheirPlannedLength() {
        val engine = engine(Routine.repeat("r", 10, 5, rounds = 2, getReadySeconds = 0))

        clock.advanceSeconds(60.0)
        val summary = WorkoutSummary(engine.state().results)

        assertEquals(listOf(RoundStat(1, 1, 10_000, 5_000), RoundStat(1, 2, 10_000, 0)), summary.rounds)
        assertEquals(25_000, summary.totalMs)
    }

    @Test
    fun workUntilDoneReportsTheTimeTheUserTook() {
        val engine = engine(Routine.untilDone("u", restSeconds = 30, sets = 2, getReadySeconds = 0))

        clock.advanceSeconds(42.0)
        engine.skip() // done after 42 s
        clock.advanceSeconds(30.0) // rest
        clock.advanceSeconds(75.0)
        engine.skip() // second set took 75 s
        val summary = WorkoutSummary(engine.state().results)

        assertEquals(listOf(RoundStat(1, 1, 42_000, 30_000), RoundStat(1, 2, 75_000, 0)), summary.rounds)
        assertEquals(58_500, summary.averageWorkMs)
        assertEquals(117_000, summary.workMs)
    }

    @Test
    fun aSkippedSegmentCountsOnlyTheTimeItRan() {
        val engine = engine(Routine.timer("t", 60, getReadySeconds = 0))

        clock.advanceSeconds(20.0)
        engine.skip()
        val summary = WorkoutSummary(engine.state().results)

        assertEquals(20_000, summary.totalMs)
    }

    @Test
    fun pausedTimeIsNotCounted() {
        val engine = engine(Routine.untilDone("u", restSeconds = 30, sets = 1, getReadySeconds = 0))

        clock.advanceSeconds(10.0)
        engine.pause()
        clock.advanceSeconds(500.0)
        engine.resume()
        clock.advanceSeconds(5.0)
        engine.skip()

        assertEquals(15_000, WorkoutSummary(engine.state().results).workMs)
    }

    @Test
    fun identicalTimedRoundsAreNotWorthListing() {
        val engine = engine(Routine.repeat("r", 60, 30, rounds = 10, getReadySeconds = 0))

        clock.advanceSeconds(2000.0)

        assertFalse(WorkoutSummary(engine.state().results).worthListingRounds)
    }

    @Test
    fun workUntilDoneRoundsAreWorthListingWhenTheyDiffer() {
        val engine = engine(Routine.untilDone("u", restSeconds = 30, sets = 2, getReadySeconds = 0))

        clock.advanceSeconds(42.0)
        engine.skip()
        clock.advanceSeconds(30.0 + 75.0)
        engine.skip()

        assertTrue(WorkoutSummary(engine.state().results).worthListingRounds)
    }

    @Test
    fun aSkippedRoundMakesTheListWorthShowing() {
        val engine = engine(Routine.repeat("r", 60, 0, rounds = 3, getReadySeconds = 0))

        clock.advanceSeconds(60.0)
        clock.advanceSeconds(20.0)
        engine.skip()
        clock.advanceSeconds(100.0)

        assertTrue(WorkoutSummary(engine.state().results).worthListingRounds)
    }

    @Test
    fun ratedRoundsAreWorthListingEvenWhenIdentical() {
        val engine = TimerEngine(Routine.repeat("r", 10, 5, rounds = 2, getReadySeconds = 0).copy(rateEffort = true), clock)
            .also { it.start() }

        engine.rate(6)
        clock.advanceSeconds(100.0)

        assertTrue(WorkoutSummary(engine.state().results).worthListingRounds)
    }

    @Test
    fun aPlainTimerIsNotWorthListing() {
        val engine = engine(Routine.timer("t", 30, getReadySeconds = 0))

        clock.advanceSeconds(40.0)

        assertFalse(WorkoutSummary(engine.state().results).worthListingRounds)
    }

    @Test
    fun warmUpAndCoolDownBelongToNoRound() {
        val routine = Routine.repeat("r", 10, 0, rounds = 1, getReadySeconds = 5)
            .copy(coolDown = Phase.of(PhaseKind.COOL_DOWN, 20))
        val engine = engine(routine)

        clock.advanceSeconds(100.0)
        val summary = WorkoutSummary(engine.state().results)

        assertEquals(1, summary.rounds.size)
        assertEquals(35_000, summary.totalMs)
        assertEquals(10_000, summary.workMs)
    }
}
