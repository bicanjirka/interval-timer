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
        Routine.repeat("10 × 1 min", workSeconds = 60, restSeconds = 0, rounds = 10)
            .copy(id = "default-10x1min", color = 0xFF1565C0),
        Routine.untilDone("Work until done, rest 60 s", restSeconds = 60, sets = 10)
            .copy(id = "default-until-done", color = 0xFF6A1B9A),
    )
}
