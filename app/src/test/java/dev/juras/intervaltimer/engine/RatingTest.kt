package dev.juras.intervaltimer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingTest {
    private val clock = FakeClock()

    private fun engine(routine: Routine, rateEffort: Boolean = true) =
        TimerEngine(routine.copy(rateEffort = rateEffort), clock).also { it.start() }

    private fun summary(engine: TimerEngine) = WorkoutSummary(engine.state().results)

    @Test
    fun aRoutineWithoutRateEffortIgnoresRatings() {
        val engine = engine(Routine.timer("t", 60, getReadySeconds = 0), rateEffort = false)

        engine.rate(7)

        assertNull(engine.state().rating)
        assertEquals(false, engine.state().rateEffort)
    }

    @Test
    fun theStateTellsWhetherTheRoutineAsksForRatings() {
        assertEquals(true, engine(Routine.timer("t", 60, getReadySeconds = 0)).state().rateEffort)
    }

    @Test
    fun aRatedWorkPhaseShowsItsRatingInTheState() {
        val engine = engine(Routine.repeat("r", 10, 5, rounds = 2, getReadySeconds = 0))

        engine.rate(7)

        assertEquals(7, engine.state().rating)
    }

    @Test
    fun theRatingStartsEmptyInTheNextPhase() {
        val engine = engine(Routine.repeat("r", 10, 5, rounds = 2, getReadySeconds = 0))
        engine.rate(7)

        clock.advanceSeconds(10.0) // into rest

        assertNull(engine.state().rating)
    }

    @Test
    fun restGetReadyAndCoolDownCannotBeRated() {
        val routine = Routine.repeat("r", 10, 5, rounds = 2, getReadySeconds = 5)
            .copy(coolDown = Phase.of(PhaseKind.COOL_DOWN, 20))
        val engine = engine(routine)

        engine.rate(3) // get ready
        assertNull(engine.state().rating)
        clock.advanceSeconds(5.0 + 10.0) // rest of round 1
        engine.rate(3)
        assertNull(engine.state().rating)
        clock.advanceSeconds(5.0 + 10.0) // cool-down
        engine.rate(3)
        assertNull(engine.state().rating)
        clock.advanceSeconds(100.0)

        assertEquals(emptyList<Int?>(), summary(engine).rounds.mapNotNull { it.rating })
    }

    @Test
    fun aTapAfterTheWorkTimeRanOutDoesNotRateTheRest() {
        val engine = engine(Routine.repeat("r", 10, 5, rounds = 2, getReadySeconds = 0))

        clock.advanceSeconds(10.5) // nobody looked at the state, but work is over
        engine.rate(9)

        assertNull(engine.state().rating)
        clock.advanceSeconds(100.0)
        assertNull(summary(engine).rounds.first().rating)
    }

    @Test
    fun aRatingCanBeChangedAndCleared() {
        val engine = engine(Routine.timer("t", 60, getReadySeconds = 0))

        engine.rate(4)
        engine.rate(8)
        assertEquals(8, engine.state().rating)
        engine.rate(null)
        assertNull(engine.state().rating)
    }

    @Test
    fun numbersOutsideOneToTenAreIgnored() {
        val engine = engine(Routine.timer("t", 60, getReadySeconds = 0))
        engine.rate(5)

        engine.rate(0)
        engine.rate(11)

        assertEquals(5, engine.state().rating)
    }

    @Test
    fun aRatingSurvivesPauseAndCanBeGivenWhilePaused() {
        val engine = engine(Routine.timer("t", 60, getReadySeconds = 0))

        engine.pause()
        engine.rate(6)
        engine.resume()

        assertEquals(6, engine.state().rating)
    }

    @Test
    fun workUntilDoneRatingsLandOnTheirOwnRounds() {
        val engine = engine(Routine.untilDone("u", restSeconds = 30, sets = 3, getReadySeconds = 0))

        clock.advanceSeconds(40.0)
        engine.rate(6)
        engine.skip()
        clock.advanceSeconds(30.0) // rest
        clock.advanceSeconds(50.0) // set 2 is left unrated
        engine.skip()
        clock.advanceSeconds(30.0)
        clock.advanceSeconds(45.0)
        engine.rate(9)
        engine.skip()

        assertEquals(listOf(6, null, 9), summary(engine).rounds.map { it.rating })
    }

    @Test
    fun timedRoundsKeepTheirRatingsInTheSummary() {
        val engine = engine(Routine.repeat("r", 10, 5, rounds = 3, getReadySeconds = 0))

        engine.rate(5)
        clock.advanceSeconds(15.0)
        engine.rate(7)
        clock.advanceSeconds(15.0)
        engine.rate(8)
        clock.advanceSeconds(100.0)

        assertEquals(listOf(5, 7, 8), summary(engine).rounds.map { it.rating })
    }

    @Test
    fun theAverageIgnoresUnratedRounds() {
        val engine = engine(Routine.repeat("r", 10, 5, rounds = 3, getReadySeconds = 0))

        engine.rate(5)
        clock.advanceSeconds(30.0) // round 2 unrated
        engine.rate(8)
        clock.advanceSeconds(100.0)

        assertEquals(6.5, summary(engine).averageRating!!, 0.001)
    }

    @Test
    fun withoutAnyRatingThereIsNoAverage() {
        val engine = engine(Routine.timer("t", 5, getReadySeconds = 0))

        clock.advanceSeconds(10.0)

        assertNull(summary(engine).averageRating)
    }
}
