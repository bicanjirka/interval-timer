package dev.juras.intervaltimer.data

import dev.juras.intervaltimer.engine.Routine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The stored format. Unknown fields are ignored and every field added later needs a default, so
 * routines saved by an older app version keep loading.
 */
object RoutineJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Routine.serializer())

    fun encode(routines: List<Routine>): String = json.encodeToString(serializer, routines)

    fun decode(text: String): List<Routine> = json.decodeFromString(serializer, text)
}

/** What a fresh install starts with. The ids are fixed so an edit before the first save still matches. */
object DefaultRoutines {
    fun all(): List<Routine> = listOf(
        Routine.repeat("8 × 40 s / 20 s", workSeconds = 40, restSeconds = 20, rounds = 8).copy(id = "default-8x40-20"),
        Routine.repeat("Tabata 8 × 20 s / 10 s", workSeconds = 20, restSeconds = 10, rounds = 8).copy(id = "default-tabata"),
        Routine.timer("5 min timer", seconds = 300).copy(id = "default-5min"),
    )
}
