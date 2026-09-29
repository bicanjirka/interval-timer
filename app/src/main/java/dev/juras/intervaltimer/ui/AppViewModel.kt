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

/** A routine just deleted from the list, kept so the "Undo delete" snackbar can put it back where it was. */
data class DeletedRoutine(val routine: Routine, val index: Int)

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
    private var draftOriginal: Routine? = null

    /** Set by [delete], read by the UI to offer an undo. */
    var lastDeleted by mutableStateOf<DeletedRoutine?>(null)
        private set

    fun openSettings() {
        screen = Screen.SETTINGS
    }

    fun back() {
        screen = Screen.LIST
    }

    fun newRoutine() {
        // No name: the list labels it by its content until the user types one.
        draft = Routine.repeat("", workSeconds = 40, restSeconds = 20, rounds = 8)
        draftOriginal = draft
        screen = Screen.EDIT
    }

    fun edit(routine: Routine) {
        draft = routine
        draftOriginal = routine
        screen = Screen.EDIT
    }

    fun updateDraft(routine: Routine) {
        draft = routine
    }

    /** Saves the draft, unless nothing was changed (a new routine left as it was is not kept), and returns to the list. */
    fun saveDraft() {
        draft?.takeIf { it != draftOriginal }?.let { routine -> viewModelScope.launch { routineStore.save(routine) } }
        draft = null
        draftOriginal = null
        screen = Screen.LIST
    }

    fun delete(routine: Routine) {
        val index = routines.value.orEmpty().indexOfFirst { it.id == routine.id }
        viewModelScope.launch { routineStore.delete(routine.id) }
        lastDeleted = DeletedRoutine(routine, index)
    }

    fun undoDelete(deleted: DeletedRoutine) {
        viewModelScope.launch { routineStore.insert(deleted.routine, deleted.index) }
        if (lastDeleted == deleted) lastDeleted = null
    }

    /** The undo offer is over; the routine stays deleted. */
    fun forgetDeleted(deleted: DeletedRoutine) {
        if (lastDeleted == deleted) lastDeleted = null
    }

    /** The copy goes right below the original. */
    fun duplicate(routine: Routine) {
        val index = routines.value.orEmpty().indexOfFirst { it.id == routine.id }
        val copy = routine.copy(id = UUID.randomUUID().toString(), name = if (routine.name.isBlank()) "" else "${routine.name} copy")
        viewModelScope.launch { routineStore.insert(copy, index + 1) }
    }

    fun reorder(ids: List<String>) {
        viewModelScope.launch { routineStore.reorder(ids) }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch { settingsStore.update(settings) }
    }
}
