package dev.juras.intervaltimer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualPhaseTest {
    private val clock = FakeClock()

    private fun engine(sets: Int = 2, restSeconds: Int = 60) =
        TimerEngine(Routine.untilDone("sets", restSeconds, sets, getReadySeconds = 0), clock).also { it.start() }

    @Test
    fun manualWorkCountsUpAndNeverEndsByItself() {
        val engine = engine()

        clock.advanceSeconds(500.0)
        val state = engine.state()

        assertEquals(Status.RUNNING, state.status)
        assertEquals("Work", state.segment?.name)
        assertTrue(state.segment?.manual == true)
        assertEquals(500, state.displaySeconds)
        assertEquals(0, state.segmentRemainingMs)
    }

    @Test
    fun pressingDoneStartsTheFixedRestAndNamesTheNextSet() {
        val engine = engine()
        clock.advanceSeconds(42.0)

        engine.skip()
        val state = engine.state()

        assertEquals("Rest", state.segment?.name)
        assertEquals(60, state.displaySeconds)
        assertEquals("Work", state.next?.name)
        assertEquals(1, state.segment?.round)
    }

    @Test
    fun whenTheRestIsUpTheNextSetStartsCountingFromZero() {
        val engine = engine()
        clock.advanceSeconds(42.0)
        engine.skip()

        clock.advanceSeconds(65.0)
        val state = engine.state()

        assertEquals("Work", state.segment?.name)
        assertEquals(2, state.segment?.round)
        assertEquals(5, state.displaySeconds)
    }

    @Test
    fun doneOnTheLastSetFinishesWithoutARest() {
        val engine = engine(sets = 1)
        clock.advanceSeconds(30.0)

        engine.skip()

        assertEquals(Status.FINISHED, engine.state().status)
    }

    @Test
    fun theTotalIsOpenEndedWhileManualWorkIsStillToCome() {
        val engine = engine()
        clock.advanceSeconds(10.0)
        val working = engine.state()
        engine.skip()
        val resting = engine.state()
        clock.advanceSeconds(60.0)
        engine.skip()
        val lastRest = engine.state()

        assertTrue(working.openEnded)
        assertTrue(resting.openEnded)
        assertEquals(60_000, resting.totalRemainingMs)
        assertEquals(Status.FINISHED, lastRest.status)
    }

    @Test
    fun aTimedRoutineIsNeverOpenEnded() {
        val engine = TimerEngine(Routine.repeat("timed", 10, 5, 3, getReadySeconds = 0), clock).also { it.start() }

        assertFalse(engine.state().openEnded)
    }

    @Test
    fun pausingFreezesTheCountUp() {
        val engine = engine()
        clock.advanceSeconds(20.0)

        engine.pause()
        clock.advanceSeconds(300.0)
        val paused = engine.state()
        engine.resume()
        clock.advanceSeconds(5.0)
        val resumed = engine.state()

        assertEquals(Status.PAUSED, paused.status)
        assertEquals(20, paused.displaySeconds)
        assertEquals(25, resumed.displaySeconds)
    }

    @Test
    fun manualPhasesSurviveTheStoredFormat() {
        val routine = Routine.untilDone("sets", 90, 5)

        val decoded = dev.juras.intervaltimer.data.RoutineJson.decode(dev.juras.intervaltimer.data.RoutineJson.encode(listOf(routine)))

        assertEquals(listOf(routine), decoded)
    }
}

class WithManualTest {
    @Test
    fun turningUntilDoneOffGivesTheZeroLengthPhaseARealLength() {
        val timed = Phase.untilDone().withManual(false)

        assertEquals(false, timed.manual)
        assertEquals(Phase.DEFAULT_SECONDS, timed.seconds)
    }

    @Test
    fun turningItOffKeepsALengthTheUserAlreadySet() {
        assertEquals(75, Phase.of(PhaseKind.WORK, 75).copy(manual = true).withManual(false).seconds)
    }

    @Test
    fun turningItOnKeepsTheSeconds() {
        assertEquals(true, Phase.of(PhaseKind.WORK, 30).withManual(true).manual)
    }
}
