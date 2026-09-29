package dev.juras.intervaltimer.data

import dev.juras.intervaltimer.engine.Block
import dev.juras.intervaltimer.engine.Phase
import dev.juras.intervaltimer.engine.PhaseKind
import dev.juras.intervaltimer.engine.Routine
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineJsonTest {
    @Test
    fun routinesSurviveAnEncodeDecodeRoundTrip() {
        val routines = DefaultRoutines.all() + Routine(
            name = "everything",
            warmUp = Phase.of(PhaseKind.WARM_UP, 5),
            blocks = listOf(
                Block(
                    rounds = 3,
                    phases = listOf(Phase("Squats", 30, PhaseKind.WORK, deltaSeconds = 5, minSeconds = 10, spoken = "Squat")),
                    restAfterSeconds = 45,
                ),
            ),
            coolDown = Phase.of(PhaseKind.COOL_DOWN, 60),
        )

        val decoded = RoutineJson.decode(RoutineJson.encode(routines))

        assertEquals(routines, decoded)
    }

    @Test
    fun aRoutineWithOnlyTheOriginalFieldsStillLoads() {
        val old = """[{"id":"a","name":"old","blocks":[{"phases":[{"name":"Work","seconds":20}]}]}]"""

        val routine = RoutineJson.decode(old).single()

        assertEquals("old", routine.name)
        assertEquals(1, routine.blocks.single().rounds)
        assertEquals(PhaseKind.WORK, routine.blocks.single().phases.single().kind)
    }

    @Test
    fun fieldsFromANewerVersionAreIgnored() {
        val newer = """[{"id":"a","name":"n","futureThing":1,"blocks":[]}]"""

        assertEquals("n", RoutineJson.decode(newer).single().name)
    }
}
