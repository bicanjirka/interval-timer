package dev.juras.intervaltimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.juras.intervaltimer.data.Settings
import dev.juras.intervaltimer.service.TimerService

/** Shows the running screen whenever a routine is running, otherwise the screen the view model is on. */
@Composable
fun App(viewModel: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val running by TimerService.state.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: Settings()

    running?.let { state ->
        RunningScreen(
            state = state,
            keepScreenOn = current.keepScreenOn,
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
            onStart = { TimerService.start(context, it) },
            onEdit = viewModel::edit,
            onDuplicate = viewModel::duplicate,
            onNew = viewModel::newRoutine,
            onSettings = viewModel::openSettings,
        )

        Screen.EDIT -> viewModel.draft?.let { draft ->
            RoutineEditorScreen(
                routine = draft,
                canDelete = viewModel.editingExisting,
                onChange = viewModel::updateDraft,
                onDone = viewModel::saveDraft,
                onDelete = viewModel::deleteDraft,
            )
        }

        Screen.SETTINGS -> SettingsScreen(settings = current, onChange = viewModel::updateSettings, onBack = viewModel::back)
    }
}
