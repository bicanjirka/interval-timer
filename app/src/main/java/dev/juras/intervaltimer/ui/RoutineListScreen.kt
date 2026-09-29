package dev.juras.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.juras.intervaltimer.engine.Routine
import dev.juras.intervaltimer.ui.theme.BarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    routines: List<Routine>?,
    onStart: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onDuplicate: (Routine) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interval Timer", fontWeight = FontWeight.Bold) },
                actions = { TextButton(onClick = onSettings) { Text("Settings", fontSize = 16.sp, color = Color.White) } },
                colors = barColors(),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, containerColor = BarGreen, contentColor = Color.White) {
                Text("+ New routine", fontSize = 16.sp)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(routines.orEmpty(), key = { it.id }) { routine ->
                RoutineCard(routine, onStart, onEdit, onDuplicate)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun barColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = BarGreen,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)

/** The whole card is the routine's colour, like the running screen will be. */
@Composable
private fun RoutineCard(
    routine: Routine,
    onStart: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onDuplicate: (Routine) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(routine.color), contentColor = Color.White),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).clickable { onStart(routine) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { Text("▶", fontSize = 28.sp, color = Color.White) }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(routine.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(routine.summary(), fontSize = 14.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onEdit(routine) }) { Text("Edit", color = Color.White) }
                TextButton(onClick = { onDuplicate(routine) }) { Text("Copy", color = Color.White) }
            }
        }
    }
}
