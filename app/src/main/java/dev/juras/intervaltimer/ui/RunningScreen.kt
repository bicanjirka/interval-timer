package dev.juras.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.juras.intervaltimer.engine.Segment
import dev.juras.intervaltimer.engine.Status
import dev.juras.intervaltimer.engine.TimerState
import dev.juras.intervaltimer.engine.formatSeconds

private val DoneColor = Color(0xFF1B5E20)
private val ButtonColors @Composable get() = ButtonDefaults.buttonColors(
    containerColor = Color.Black.copy(alpha = 0.4f),
    contentColor = Color.White,
)

/** Full-screen countdown in the colour of the current phase. */
@Composable
fun RunningScreen(
    state: TimerState,
    keepScreenOn: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    KeepScreenOn(keepScreenOn)
    val finished = state.status == Status.FINISHED
    val background = if (finished) DoneColor else Color(state.segment?.color ?: 0xFF000000)
    Column(
        modifier = Modifier.fillMaxSize().background(background).safeDrawingPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.routineName, fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f))
        Text(state.segment?.let(::position).orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

        if (finished) {
            DoneBody(Modifier.weight(1f), onClose)
        } else {
            RunningBody(Modifier.weight(1f), state)
            Controls(state.status == Status.PAUSED, onPause, onResume, onSkip, onStop)
        }
    }
}

@Composable
private fun RunningBody(modifier: Modifier, state: TimerState) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            state.segment?.name.orEmpty(),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        BigDigits(formatSeconds(state.segmentSeconds), Modifier.weight(1f))
        Text(
            state.next?.let { "Next: ${it.name} ${formatSeconds((it.durationMs / 1000).toInt())}" } ?: "Last one",
            fontSize = 28.sp,
            color = Color.White,
        )
        Text(
            "Total left ${formatSeconds(state.totalSeconds)}",
            fontSize = 22.sp,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

/** Digits sized to fill the space left, whatever the length of the text. */
@Composable
private fun BigDigits(text: String, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val sizePx = minOf(constraints.maxWidth / (text.length * 0.62f), constraints.maxHeight * 0.9f)
        Text(
            text,
            fontSize = with(density) { sizePx.toSp() },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Controls(paused: Boolean, onPause: () -> Unit, onResume: () -> Unit, onSkip: () -> Unit, onStop: () -> Unit) {
    var confirmStop by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlButton(if (paused) "Resume" else "Pause", Modifier.weight(1.4f), if (paused) onResume else onPause)
        ControlButton("Skip", Modifier.weight(1f), onSkip)
        ControlButton("Stop", Modifier.weight(1f)) { confirmStop = true }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop this routine?") },
            confirmButton = { TextButton(onClick = { confirmStop = false; onStop() }) { Text("Stop", fontSize = 18.sp) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going", fontSize = 18.sp) } },
        )
    }
}

@Composable
private fun ControlButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(96.dp), colors = ButtonColors) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DoneBody(modifier: Modifier, onClose: () -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Done!", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Color.White)
        ControlButton("Close", Modifier.fillMaxWidth().padding(top = 32.dp), onClose)
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

private fun position(segment: Segment): String = buildList {
    if (segment.blocks > 1 && segment.block > 0) add("Block ${segment.block}/${segment.blocks}")
    if (segment.rounds > 0) add("Round ${segment.round}/${segment.rounds}")
}.joinToString(" · ")
