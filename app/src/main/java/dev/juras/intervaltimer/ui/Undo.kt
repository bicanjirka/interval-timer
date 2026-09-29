package dev.juras.intervaltimer.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val UNDO_MS = 3000L

/**
 * The app's answer to "are you sure?": do the thing at once and offer to take it back for [UNDO_MS].
 * Returns true if the user pressed the action.
 */
suspend fun SnackbarHostState.showUndo(message: String, actionLabel: String): Boolean = coroutineScope {
    val timeout = launch {
        delay(UNDO_MS)
        currentSnackbarData?.dismiss()
    }
    val result = showSnackbar(message, actionLabel, withDismissAction = false, duration = SnackbarDuration.Indefinite)
    timeout.cancel()
    result == SnackbarResult.ActionPerformed
}
