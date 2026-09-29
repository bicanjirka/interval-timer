package dev.juras.intervaltimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.juras.intervaltimer.engine.Block
import dev.juras.intervaltimer.engine.Phase
import dev.juras.intervaltimer.engine.PhaseKind
import dev.juras.intervaltimer.engine.Routine
import dev.juras.intervaltimer.engine.Timeline
import dev.juras.intervaltimer.engine.formatSeconds
import dev.juras.intervaltimer.ui.theme.PhasePalette

private val Danger = Color(0xFFFF8A80)
private const val NEUTRAL = 0xFF424242

/** Edits [routine] in place through [onChange]; leaving the screen saves it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    routine: Routine,
    canDelete: Boolean,
    onChange: (Routine) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onDone)
    var confirmDelete by remember { mutableStateOf(false) }
    var pickingColor by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit routine") },
                navigationIcon = { TextButton(onClick = onDone) { Text("‹ Save", fontSize = 16.sp, color = Color.White) } },
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
                        label = { Text("Name") },
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
                ) { Text("+ Add block") }
            }
            item {
                OptionalPhase("Cool down at the end", routine.coolDown, PhaseKind.COOL_DOWN, 60) { onChange(routine.copy(coolDown = it)) }
            }
            if (canDelete) {
                item { TextButton(onClick = { confirmDelete = true }) { Text("Delete routine", color = Danger) } }
            }
        }
    }
    if (pickingColor) {
        ColorPickerDialog(onPick = { onChange(routine.copy(color = it)); pickingColor = false }, onDismiss = { pickingColor = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${routine.name}”?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun newBlock() = Block(rounds = 8, phases = listOf(Phase.of(PhaseKind.WORK, 40), Phase.of(PhaseKind.REST, 20)))

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

@Composable
private fun OptionalPhase(label: String, phase: Phase?, kind: PhaseKind, defaultSeconds: Int, onChange: (Phase?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(phase?.color ?: NEUTRAL), contentColor = Color.White),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = phase != null, onCheckedChange = { onChange(if (it) Phase.of(kind, defaultSeconds) else null) })
            Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            if (phase != null) {
                NumberField("sec", phase.seconds, min = 1, modifier = Modifier.weight(0.8f)) { onChange(phase.copy(seconds = it)) }
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
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Block ${index + 1}", fontSize = 18.sp, modifier = Modifier.weight(1f))
                RowActions(index > 0, index < count - 1, { onMove(-1) }, { onMove(1) }, onDuplicate, onDelete, Modifier)
            }
            NumberField("Rounds", block.rounds, min = 1, modifier = Modifier.fillMaxWidth()) { onChange(block.copy(rounds = it)) }
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
            TextButton(onClick = { onChange(block.copy(phases = block.phases + nextPhase(block.phases))) }) { Text("+ Add phase") }
            if (index < count - 1) {
                NumberField("Rest before next block (sec)", block.restAfterSeconds, min = 0, modifier = Modifier.fillMaxWidth()) {
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
    Card(colors = CardDefaults.cardColors(containerColor = Color(phase.color), contentColor = Color.White)) {
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
                    Switch(checked = phase.manual, onCheckedChange = { onChange(phase.copy(manual = it)) })
                    Text("Until I press Done (time counts up)", fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
            if (!phase.manual) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("sec", phase.seconds, min = 1, modifier = Modifier.weight(1f)) { onChange(phase.copy(seconds = it)) }
                    NumberField("± sec/round", phase.deltaSeconds, min = -3600, modifier = Modifier.weight(1f), allowNegative = true) {
                        onChange(phase.copy(deltaSeconds = it))
                    }
                }
            }
            RowActions(canMoveUp, canMoveDown, { onMove(-1) }, { onMove(1) }, onDuplicate, onDelete)
        }
    }
    if (pickingColor) {
        ColorPickerDialog(onPick = { onChange(phase.copy(color = it)); pickingColor = false }, onDismiss = { pickingColor = false })
    }
}

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
        IconButton(onClick = onUp, enabled = canMoveUp) { Text("▲", fontSize = 18.sp) }
        IconButton(onClick = onDown, enabled = canMoveDown) { Text("▼", fontSize = 18.sp) }
        IconButton(onClick = onDuplicate) { Text("⧉", fontSize = 20.sp) }
        IconButton(onClick = onDelete) { Text("✕", fontSize = 18.sp, color = Danger) }
    }
}

/** A whole-number field. Text that isn't a number yet (empty, "-") is kept without changing the value. */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    min: Int,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val cleaned = input.filterIndexed { i, c -> c.isDigit() || (allowNegative && i == 0 && c == '-') }.take(5)
            text = cleaned
            cleaned.toIntOrNull()?.let { onValue(it.coerceAtLeast(min)) }
        },
        label = { Text(label) },
        singleLine = true,
        colors = onColorFields(),
        keyboardOptions = KeyboardOptions(keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Number),
        modifier = modifier,
    )
}
