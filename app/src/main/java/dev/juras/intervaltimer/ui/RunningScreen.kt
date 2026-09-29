package dev.juras.intervaltimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.juras.intervaltimer.engine.RoundStat
import dev.juras.intervaltimer.engine.Segment
import dev.juras.intervaltimer.engine.Status
import dev.juras.intervaltimer.engine.TimerState
import dev.juras.intervaltimer.engine.WorkoutSummary
import dev.juras.intervaltimer.engine.formatBig
import dev.juras.intervaltimer.engine.formatSeconds

private val DoneColor = Color(0xFF1B5E20)
private val ButtonColors @Composable get() = ButtonDefaults.buttonColors(
    containerColor = Color.Black.copy(alpha = 0.4f),
    contentColor = Color.White,
)
/** Heights of the button area (see [Controls]): three buttons in a row, or DONE above two buttons. */
private const val CONTROLS_DP = 96
private const val MANUAL_CONTROLS_DP = 140 + 12 + 96
private val Soft = Color.White.copy(alpha = 0.85f)

/** Full-screen countdown in the colour of the current phase; when the routine is finished, what it took. */
@Composable
fun RunningScreen(
    state: TimerState,
    keepScreenOn: Boolean,
    snackbarHostState: SnackbarHostState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    KeepScreenOn(keepScreenOn)
    val finished = state.status == Status.FINISHED
    val background = if (finished) DoneColor else Color(state.segment?.color ?: 0xFF000000)
    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (finished) {
                DoneBody(Modifier.weight(1f), state, onClose)
            } else {
                Header(state)
                RunningBody(Modifier.weight(1f), state)
                Controls(state.segment?.manual == true, state.status == Status.PAUSED, onPause, onResume, onSkip, onStop)
            }
        }
        // Right above the buttons, so after an accidental Stop the undo is next to where the thumb just was.
        val controlsHeight = if (state.segment?.manual == true) MANUAL_CONTROLS_DP.dp else CONTROLS_DP.dp
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 4.dp).padding(bottom = controlsHeight + 16.dp),
        )
    }
}

/** Phase name at the top left, round at the top right, the routine's name quietly below. */
@Composable
private fun Header(state: TimerState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.segment?.name.orEmpty(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            state.segment?.let(::roundLabel)?.let {
                Text(it, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text(state.routineName, fontSize = 16.sp, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RunningBody(modifier: Modifier, state: TimerState) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BigDigits(formatBig(state.displaySeconds), Modifier.weight(1f))
        Text(
            state.next?.let { "Next: ${it.name} ${if (it.manual) "(until done)" else formatSeconds((it.durationMs / 1000).toInt())}" }
                ?: "Last one",
            fontSize = 26.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (state.openEnded) " " else "Total left ${formatSeconds(state.totalSeconds)}",
            fontSize = 20.sp,
            color = Soft,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
    }
}

/**
 * Digits sized to fill the space left, whatever the length of the text, and drawn centred on the real
 * outline of the digits (not on the font's line box, which sits off-centre and let a single digit run
 * out of its space). The text is measured at a reference size and scaled: the width uses the advance
 * width, the height the outline of all ten digits, so the size doesn't jump from one number to the
 * next. A condensed face lets the digits grow taller, tabular figures keep the width steady.
 */
@Composable
private fun BigDigits(text: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = TextStyle(
        fontFamily = DigitFont,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    fun measure(value: String, sizePx: Float) =
        measurer.measure(value, base.copy(fontSize = with(density) { sizePx.toSp() }), maxLines = 1, softWrap = false)

    val digitsInk = remember(measurer, density) {
        measure("0123456789", REFERENCE_PX).getPathForRange(0, 10).getBounds()
    }
    Canvas(modifier = modifier.fillMaxWidth().semantics { contentDescription = text }) {
        val referenceWidth = measure(text, REFERENCE_PX).size.width
        val scale = minOf(size.width / referenceWidth, size.height / digitsInk.height)
        val layout = measure(text, REFERENCE_PX * scale)
        drawText(
            layout,
            color = Color.White,
            topLeft = Offset((size.width - layout.size.width) / 2, size.height / 2 - digitsInk.center.y * scale),
        )
    }
}

private const val REFERENCE_PX = 200f

private val DigitFont = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold))

@Composable
private fun Controls(
    manual: Boolean,
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    val pauseOrResume = if (paused) "Resume" else "Pause"
    if (manual) {
        // One huge target for the thing you do at the end of every set.
        Button(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) { Text("DONE", fontSize = 48.sp, fontWeight = FontWeight.Bold) }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlButton(pauseOrResume, Modifier.weight(1f), if (paused) onResume else onPause)
            ControlButton("Stop", Modifier.weight(1f), onStop)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlButton(pauseOrResume, Modifier.weight(1.4f), if (paused) onResume else onPause)
            ControlButton("Skip", Modifier.weight(1f), onSkip)
            ControlButton("Stop", Modifier.weight(1f), onStop)
        }
    }
}

@Composable
private fun ControlButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(96.dp), colors = ButtonColors) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** The finish screen: total time, work time and each round's real times (the point of work until done). */
@Composable
private fun DoneBody(modifier: Modifier, state: TimerState, onClose: () -> Unit) {
    val summary = WorkoutSummary(state.results)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Done!", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(state.routineName, fontSize = 16.sp, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Total", summary.totalMs, Modifier.weight(1f))
            Stat("Work", summary.workMs, Modifier.weight(1f))
            if (summary.rounds.size > 1) Stat("Avg work", summary.averageWorkMs, Modifier.weight(1f))
        }
        val longest = summary.rounds.maxOfOrNull { it.workMs } ?: 0
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(summary.rounds) { round -> RoundRow(round, longest, showBlock = summary.rounds.any { it.block > 1 }) }
        }
        ControlButton("Close", Modifier.fillMaxWidth().padding(top = 12.dp), onClose)
    }
}

@Composable
private fun Stat(label: String, ms: Long, modifier: Modifier) {
    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatMs(ms), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 14.sp, color = Soft)
    }
}

/** One round: its number, real work time (with a bar against the longest round) and rest time. */
@Composable
private fun RoundRow(round: RoundStat, longestWorkMs: Long, showBlock: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showBlock) "B${round.block} #${round.round}" else "#${round.round}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text("Work ${formatMs(round.workMs)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (round.restMs > 0) Text("  Rest ${formatMs(round.restMs)}", fontSize = 18.sp, color = Soft)
        }
        if (longestWorkMs > 0) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(round.workMs.toFloat() / longestWorkMs)
                    .height(6.dp)
                    .background(Color.White, RoundedCornerShape(3.dp)),
            )
        }
    }
}

private fun formatMs(ms: Long) = formatSeconds(((ms + 500) / 1000).toInt())

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** `#3/8`, with the block in front when there are several; null outside any round. */
private fun roundLabel(segment: Segment): String? {
    if (segment.rounds == 0) return null
    val block = if (segment.blocks > 1 && segment.block > 0) "B${segment.block} " else ""
    return "$block#${segment.round}/${segment.rounds}"
}
