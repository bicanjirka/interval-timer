package dev.juras.intervaltimer.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.juras.intervaltimer.data.RoutineStore
import dev.juras.intervaltimer.data.Settings
import dev.juras.intervaltimer.data.SettingsStore
import dev.juras.intervaltimer.engine.Routine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { LIST, EDIT, SETTINGS }

/** Which screen is open, the routine being edited, and the stored routines and settings. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val routineStore = RoutineStore(app)
    private val settingsStore = SettingsStore(app)

    /** Null until the first load finishes. */
    val routines: StateFlow<List<Routine>?> =
        routineStore.routines.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<Settings?> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var screen by mutableStateOf(Screen.LIST)
        private set
    var draft by mutableStateOf<Routine?>(null)
        private set
    private var draftIsNew = false

    val editingExisting: Boolean get() = !draftIsNew

    fun openSettings() {
        screen = Screen.SETTINGS
    }

    fun back() {
        screen = Screen.LIST
    }

    fun newRoutine() {
        draft = Routine.repeat("New routine", workSeconds = 40, restSeconds = 20, rounds = 8)
        draftIsNew = true
        screen = Screen.EDIT
    }

    fun edit(routine: Routine) {
        draft = routine
        draftIsNew = false
        screen = Screen.EDIT
    }

    fun updateDraft(routine: Routine) {
        draft = routine
    }

    /** Saves the draft and returns to the list. */
    fun saveDraft() {
        draft?.let { routine -> viewModelScope.launch { routineStore.save(routine) } }
        draft = null
        screen = Screen.LIST
    }

    fun deleteDraft() {
        draft?.let { routine -> viewModelScope.launch { routineStore.delete(routine.id) } }
        draft = null
        screen = Screen.LIST
    }

    fun duplicate(routine: Routine) {
        viewModelScope.launch {
            routineStore.save(routine.copy(id = UUID.randomUUID().toString(), name = "${routine.name} copy"))
        }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch { settingsStore.update(settings) }
    }
}
