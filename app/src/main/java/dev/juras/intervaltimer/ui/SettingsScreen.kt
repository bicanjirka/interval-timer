package dev.juras.intervaltimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.juras.intervaltimer.data.Settings
import dev.juras.intervaltimer.service.AudioCues
import dev.juras.intervaltimer.service.Cue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val cues = remember { AudioCues(context) }
    DisposableEffect(Unit) { onDispose { cues.shutdown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back", fontSize = 16.sp) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                var volume by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
                Text("Timer volume  ${(volume * 100).toInt()}%", fontSize = 18.sp)
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = { onChange(settings.copy(volume = volume)) },
                )
                Button(
                    onClick = {
                        cues.apply(settings.copy(volume = volume))
                        cues.play(Cue.WORK)
                        cues.speak("Work", delayMs = 400)
                    },
                ) { Text("Play test sound") }
            }
            ToggleRow("Lower other audio while a cue plays", settings.ducking) { onChange(settings.copy(ducking = it)) }
            ToggleRow("Speak phase names", settings.voice) { onChange(settings.copy(voice = it)) }
            ToggleRow("Keep screen on while running", settings.keepScreenOn) { onChange(settings.copy(keepScreenOn = it)) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
