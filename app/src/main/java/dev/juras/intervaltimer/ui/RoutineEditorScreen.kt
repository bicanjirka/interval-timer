package dev.juras.intervaltimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.juras.intervaltimer.engine.Block
import dev.juras.intervaltimer.engine.Phase
import dev.juras.intervaltimer.engine.PhaseKind
import dev.juras.intervaltimer.engine.Routine
import dev.juras.intervaltimer.engine.Timeline
import dev.juras.intervaltimer.engine.formatSeconds
import dev.juras.intervaltimer.ui.theme.Danger
import dev.juras.intervaltimer.ui.theme.PhasePalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NEUTRAL = 0xFF424242
private const val SECONDS_STEP = 5

/** Edits [routine] in place through [onChange]; leaving the screen saves it (unless nothing changed). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    routine: Routine,
    onChange: (Routine) -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    var pickingColor by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit routine") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Save and go back") }
                },
                colors = barColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            // imePadding lifts the list above the keyboard so the field being edited stays visible.
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(routine.color) { pickingColor = true }
                    OutlinedTextField(
                        value = routine.name,
                        onValueChange = { onChange(routine.copy(name = it)) },
                        label = { Text("Name (optional)") },
                        // Without a name the list labels the routine by its content.
                        placeholder = { Text(routine.shape()) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }
            item {
                val timed = formatSeconds((Timeline.of(routine).totalMs / 1000).toInt())
                Text(if (routine.isOpenEnded) "Total $timed plus however long your work takes" else "Total $timed", fontSize = 16.sp)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = routine.rateEffort, onCheckedChange = { onChange(routine.copy(rateEffort = it)) })
                    Text("Rate effort 1–10 during work", fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                }
            }
            item {
                OptionalPhase("Get ready first", routine.warmUp, PhaseKind.WARM_UP, 10) { onChange(routine.copy(warmUp = it)) }
            }
            itemsIndexed(routine.blocks) { i, block ->
                BlockEditor(
                    index = i,
                    count = routine.blocks.size,
                    block = block,
                    onChange = { onChange(routine.copy(blocks = routine.blocks.replaced(i, it))) },
                    onMove = { onChange(routine.copy(blocks = routine.blocks.moved(i, it))) },
                    onDuplicate = { onChange(routine.copy(blocks = routine.blocks.duplicated(i))) },
                    onDelete = { onChange(routine.copy(blocks = routine.blocks.without(i))) },
                )
            }
            item {
                OutlinedButton(
                    onClick = { onChange(routine.copy(blocks = routine.blocks + newBlock())) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null)
                    Text("Add block", modifier = Modifier.padding(start = 8.dp))
                }
            }
            item {
                OptionalPhase("Cool down at the end", routine.coolDown, PhaseKind.COOL_DOWN, 60) { onChange(routine.copy(coolDown = it)) }
            }
        }
    }
    if (pickingColor) {
        ColorPickerDialog(onPick = { onChange(routine.copy(color = it)); pickingColor = false }, onDismiss = { pickingColor = false })
    }
}

private fun newBlock() = Block(rounds = 8, phases = listOf(Phase.of(PhaseKind.WORK, 40), Phase.of(PhaseKind.REST, 20)))

@Composable
private fun OptionalPhase(label: String, phase: Phase?, kind: PhaseKind, defaultSeconds: Int, onChange: (Phase?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color(phase?.color ?: NEUTRAL), contentColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = phase != null, onCheckedChange = { onChange(if (it) Phase.of(kind, defaultSeconds) else null) })
                Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
            if (phase != null) {
                NumberField("Seconds", phase.seconds, min = 1, step = SECONDS_STEP) { onChange(phase.copy(seconds = it)) }
            }
        }
    }
}

@Composable
private fun BlockEditor(
    index: Int,
    count: Int,
    block: Block,
    onChange: (Block) -> Unit,
    onMove: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Block ${index + 1}", fontSize = 18.sp, modifier = Modifier.weight(1f))
                RowActions(index > 0, index < count - 1, { onMove(-1) }, { onMove(1) }, onDuplicate, onDelete, Modifier)
            }
            NumberField("Rounds", block.rounds, min = 1, step = 1) { onChange(block.copy(rounds = it)) }
            block.phases.forEachIndexed { j, phase ->
                PhaseEditor(
                    phase = phase,
                    canMoveUp = j > 0,
                    canMoveDown = j < block.phases.size - 1,
                    onChange = { onChange(block.copy(phases = block.phases.replaced(j, it))) },
                    onMove = { onChange(block.copy(phases = block.phases.moved(j, it))) },
                    onDuplicate = { onChange(block.copy(phases = block.phases.duplicated(j))) },
                    onDelete = { onChange(block.copy(phases = block.phases.without(j))) },
                )
            }
            TextButton(onClick = { onChange(block.copy(phases = block.phases + nextPhase(block.phases))) }) {
                Icon(Icons.Default.Add, null)
                Text("Add phase", modifier = Modifier.padding(start = 8.dp))
            }
            if (index < count - 1) {
                NumberField("Rest before next block (sec)", block.restAfterSeconds, min = 0, step = SECONDS_STEP) {
                    onChange(block.copy(restAfterSeconds = it))
                }
            }
        }
    }
}

private fun nextPhase(phases: List<Phase>) =
    if (phases.lastOrNull()?.kind == PhaseKind.WORK) Phase.of(PhaseKind.REST, 20) else Phase.of(PhaseKind.WORK, 40)

/** A phase card in the phase's own colour, so the editor already looks like the running screen. */
@Composable
private fun PhaseEditor(
    phase: Phase,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (Phase) -> Unit,
    onMove: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var pickingColor by remember { mutableStateOf(false) }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color(phase.color), contentColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(phase.color, outlined = true) { pickingColor = true }
                OutlinedTextField(
                    value = phase.name,
                    onValueChange = { onChange(phase.copy(name = it)) },
                    singleLine = true,
                    colors = onColorFields(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                TextButton(onClick = { onChange(phase.withKind(if (phase.kind == PhaseKind.REST) PhaseKind.WORK else PhaseKind.REST)) }) {
                    Text(if (phase.kind == PhaseKind.REST) "Rest" else "Work", color = Color.White)
                }
            }
            if (phase.kind == PhaseKind.WORK) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = phase.manual, onCheckedChange = { onChange(phase.withManual(it)) })
                    Text("Until I press Done (time counts up)", fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
            if (!phase.manual) {
                NumberField("Seconds", phase.seconds, min = 1, step = SECONDS_STEP) { onChange(phase.copy(seconds = it)) }
                NumberField("± sec per round", phase.deltaSeconds, min = -3600, step = SECONDS_STEP, allowNegative = true) {
                    onChange(phase.copy(deltaSeconds = it))
                }
            }
            RowActions(canMoveUp, canMoveDown, { onMove(-1) }, { onMove(1) }, onDuplicate, onDelete)
        }
    }
    if (pickingColor) {
        ColorPickerDialog(onPick = { onChange(phase.copy(color = it)); pickingColor = false }, onDismiss = { pickingColor = false })
    }
}

/** Text fields that sit on a coloured card: white text and outline. */
@Composable
private fun onColorFields() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
    cursorColor = Color.White,
)

/** Switching kind renames and recolours only if the user hadn't customised those. */
private fun Phase.withKind(newKind: PhaseKind) = copy(
    kind = newKind,
    name = if (name == kind.defaultName) newKind.defaultName else name,
    color = if (color == kind.defaultColor) newKind.defaultColor else color,
)

@Composable
private fun ColorPickerDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhasePalette.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { color -> ColorDot(color) { onPick(color) } }
                    }
                }
            }
        },
    )
}

@Composable
private fun ColorDot(color: Long, outlined: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (outlined) Color.White else Color(color))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (outlined) Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(color)))
    }
}

@Composable
private fun RowActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(horizontalArrangement = Arrangement.End, modifier = modifier) {
        IconButton(onClick = onUp, enabled = canMoveUp) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
        IconButton(onClick = onDown, enabled = canMoveDown) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
        IconButton(onClick = onDuplicate) { Icon(Icons.Filled.ContentCopy, "Duplicate") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Danger) }
    }
}

/**
 * A whole-number setting: `−` and `+` step by [step] (hold to repeat) and the number in the middle can
 * still be typed. Text that isn't a number yet (empty, "-") is kept without changing the value.
 */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    min: Int,
    step: Int,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        StepButton(Icons.Filled.Remove, "Less $label") { onValue(stepped(value, -1, step).coerceAtLeast(min)) }
        BasicTextField(
            value = text,
            onValueChange = { input ->
                val cleaned = input.filterIndexed { i, c -> c.isDigit() || (allowNegative && i == 0 && c == '-') }.take(5)
                text = cleaned
                cleaned.toIntOrNull()?.let { onValue(it.coerceAtLeast(min)) }
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Number),
            decorationBox = { field ->
                Box(
                    modifier = Modifier.width(80.dp).padding(horizontal = 6.dp)
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { field() }
            },
        )
        StepButton(Icons.Default.Add, "More $label") { onValue(stepped(value, 1, step)) }
    }
}

/** A round button that steps once when pressed and keeps stepping while it is held. */
@Composable
private fun StepButton(icon: ImageVector, description: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { step(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    coroutineScope {
                        val repeating = launch {
                            delay(HOLD_DELAY_MS)
                            while (true) {
                                step()
                                delay(REPEAT_MS)
                            }
                        }
                        tryAwaitRelease()
                        repeating.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White) }
}

private const val HOLD_DELAY_MS = 450L
private const val REPEAT_MS = 90L
