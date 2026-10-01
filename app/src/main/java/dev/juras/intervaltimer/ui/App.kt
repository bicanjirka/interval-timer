package dev.juras.intervaltimer.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.juras.intervaltimer.data.Settings
import dev.juras.intervaltimer.service.TimerService

/**
 * Shows the running screen whenever a routine is running, otherwise the screen the view model is on.
 * Nothing asks "are you sure?": stopping and deleting happen at once and a snackbar offers an undo.
 */
@Composable
fun App(viewModel: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val running by TimerService.state.collectAsStateWithLifecycle()
    val stopPending by TimerService.stopPending.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: Settings()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(stopPending) {
        if (stopPending && snackbar.showUndo("Routine stopped", "Undo stop")) TimerService.undoStop(context)
    }
    val deleted = viewModel.lastDeleted
    LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect
        if (snackbar.showUndo("Deleted “${deleted.routine.title()}”", "Undo delete")) {
            viewModel.undoDelete(deleted)
        } else {
            viewModel.forgetDeleted(deleted)
        }
    }

    running?.let { state ->
        RunningScreen(
            state = state,
            keepScreenOn = current.keepScreenOn,
            snackbarHostState = snackbar,
            onRate = { TimerService.rate(context, it) },
            onPause = { TimerService.pause(context) },
            onResume = { TimerService.resume(context) },
            onSkip = { TimerService.skip(context) },
            onStop = { TimerService.stop(context) },
            onClose = TimerService::dismiss,
        )
        return
    }

    when (viewModel.screen) {
        Screen.LIST -> RoutineListScreen(
            routines = routines,
            snackbarHostState = snackbar,
            onStart = { TimerService.start(context, it) },
            onEdit = viewModel::edit,
            onDuplicate = viewModel::duplicate,
            onDelete = viewModel::delete,
            onReorder = viewModel::reorder,
            onNew = viewModel::newRoutine,
            onSettings = viewModel::openSettings,
        )

        Screen.EDIT -> viewModel.draft?.let { draft ->
            RoutineEditorScreen(routine = draft, onChange = viewModel::updateDraft, onDone = viewModel::saveDraft)
        }

        Screen.SETTINGS -> SettingsScreen(settings = current, onChange = viewModel::updateSettings, onBack = viewModel::back)
    }
}
