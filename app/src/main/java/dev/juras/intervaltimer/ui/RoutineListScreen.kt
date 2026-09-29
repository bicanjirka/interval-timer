package dev.juras.intervaltimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.juras.intervaltimer.engine.Routine

private val CardGap = 12.dp

/**
 * One tap starts a routine. A long press selects it, which swaps the top bar for edit / copy / delete
 * and lets the card be dragged to a new place; while something is selected taps only move the selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    routines: List<Routine>?,
    snackbarHostState: SnackbarHostState,
    onStart: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onDuplicate: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
    onReorder: (List<String>) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val stored = routines.orEmpty()
    val latestStored by rememberUpdatedState(stored)

    var selectedId by remember { mutableStateOf<String?>(null) }
    // While dragging (and until the store has caught up) the list shows this order instead of the stored one.
    var dragOrder by remember { mutableStateOf<List<Routine>?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    val gapPx = with(LocalDensity.current) { CardGap.toPx() }

    LaunchedEffect(routines) { if (draggedId == null) dragOrder = null }

    val shown = dragOrder ?: stored
    val selected = shown.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }

    fun endDrag() {
        val order = dragOrder
        draggedId = null
        dragDelta = 0f
        if (order != null) {
            val ids = order.map { it.id }
            if (ids == latestStored.map { it.id }) dragOrder = null else onReorder(ids)
        }
    }

    Scaffold(
        topBar = {
            if (selected == null) {
                TopAppBar(
                    title = { Text("Interval Timer", fontWeight = FontWeight.Bold) },
                    actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
                    colors = barColors(),
                )
            } else {
                TopAppBar(
                    title = { Text(selected.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = { selectedId = null }) { Icon(Icons.Default.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = { onEdit(selected); selectedId = null }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { onDuplicate(selected); selectedId = null }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                        IconButton(onClick = { onDelete(selected); selectedId = null }) { Icon(Icons.Default.Delete, "Delete") }
                    },
                    colors = barColors(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
            if (selected == null) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    expanded = expanded,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New routine", fontSize = 16.sp) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            items(shown, key = { it.id }) { routine ->
                val dragging = routine.id == draggedId
                RoutineCard(
                    routine = routine,
                    selected = routine.id == selectedId,
                    onClick = {
                        when {
                            selectedId == null -> onStart(routine)
                            selectedId == routine.id -> selectedId = null
                            else -> selectedId = routine.id
                        }
                    },
                    modifier = if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = dragDelta; scaleX = 1.03f; scaleY = 1.03f }
                    } else {
                        Modifier.animateItem()
                    }.pointerInput(routine.id) {
                        // Every card is the same height, so one step is a card plus the gap.
                        val step = size.height + gapPx
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedId = routine.id
                                draggedId = routine.id
                                dragDelta = 0f
                                dragOrder = latestStored
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDelta += amount.y
                                var order = dragOrder ?: return@detectDragGesturesAfterLongPress
                                var index = order.indexOfFirst { it.id == routine.id }
                                while (dragDelta > step / 2 && index < order.lastIndex) {
                                    order = order.moved(index, 1); index++; dragDelta -= step
                                }
                                while (dragDelta < -step / 2 && index > 0) {
                                    order = order.moved(index, -1); index--; dragDelta += step
                                }
                                dragOrder = order
                            },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun barColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)

/** The whole card is the routine's colour, like the running screen will be. */
@Composable
private fun RoutineCard(routine: Routine, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color(routine.color), contentColor = Color.White),
        border = if (selected) BorderStroke(3.dp, Color.White) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(36.dp)) }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(routine.title(), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(routine.subtitle(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
