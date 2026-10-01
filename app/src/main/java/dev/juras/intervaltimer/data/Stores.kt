package dev.juras.intervaltimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.juras.intervaltimer.engine.Routine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("interval_timer")

/** How the timer signals a change of phase. */
enum class CueMode(val sound: Boolean, val vibrate: Boolean) {
    SOUND(sound = true, vibrate = false),
    SOUND_AND_VIBRATION(sound = true, vibrate = true),
    VIBRATION(sound = false, vibrate = true),
}

data class Settings(
    val volume: Float = 0.8f,
    val mode: CueMode = CueMode.SOUND,
    val ducking: Boolean = true,
    val voice: Boolean = true,
    val keepScreenOn: Boolean = true,
)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map {
        val d = Settings()
        Settings(
            volume = it[VOLUME] ?: d.volume,
            mode = it[MODE]?.let { name -> CueMode.entries.firstOrNull { m -> m.name == name } } ?: d.mode,
            ducking = it[DUCKING] ?: d.ducking,
            voice = it[VOICE] ?: d.voice,
            keepScreenOn = it[KEEP_SCREEN_ON] ?: d.keepScreenOn,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(settings: Settings) {
        store.edit {
            it[VOLUME] = settings.volume
            it[MODE] = settings.mode.name
            it[DUCKING] = settings.ducking
            it[VOICE] = settings.voice
            it[KEEP_SCREEN_ON] = settings.keepScreenOn
        }
    }

    private companion object {
        val VOLUME = floatPreferencesKey("volume")
        val MODE = stringPreferencesKey("cue_mode")
        val DUCKING = booleanPreferencesKey("ducking")
        val VOICE = booleanPreferencesKey("voice")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }
}

/** All routines as one JSON document. A fresh install gets [DefaultRoutines]. */
class RoutineStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val routines: Flow<List<Routine>> = store.data.map { prefs ->
        prefs[ROUTINES]?.let { runCatching { RoutineJson.decode(it) }.getOrNull() } ?: DefaultRoutines.all()
    }

    suspend fun get(id: String): Routine? = routines.first().firstOrNull { it.id == id }

    /** Replaces the routine with the same id, or adds it at the end. */
    suspend fun save(routine: Routine) = modify { list ->
        if (list.any { it.id == routine.id }) list.map { if (it.id == routine.id) routine else it } else list + routine
    }

    suspend fun delete(id: String) = modify { list -> list.filterNot { it.id == id } }

    /** Puts the routine at [index] (clamped), replacing any routine with the same id. */
    suspend fun insert(routine: Routine, index: Int) = modify { list ->
        val rest = list.filterNot { it.id == routine.id }
        rest.toMutableList().also { it.add(index.coerceIn(0, rest.size), routine) }
    }

    /** Orders the routines as [ids] say; routines not named keep their relative order after those that are. */
    suspend fun reorder(ids: List<String>) = modify { list ->
        list.sortedBy { r -> ids.indexOf(r.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    private suspend fun modify(change: (List<Routine>) -> List<Routine>) {
        store.edit { prefs ->
            val current = prefs[ROUTINES]?.let { runCatching { RoutineJson.decode(it) }.getOrNull() }
                ?: DefaultRoutines.all()
            prefs[ROUTINES] = RoutineJson.encode(change(current))
        }
    }

    private companion object {
        val ROUTINES = stringPreferencesKey("routines")
    }
}
