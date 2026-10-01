package dev.juras.intervaltimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.juras.intervaltimer.data.CueMode
import dev.juras.intervaltimer.data.Settings
import dev.juras.intervaltimer.service.Cue
import dev.juras.intervaltimer.service.CuePlayer

private val modeLabels = mapOf(
    CueMode.SOUND to "Sound",
    CueMode.SOUND_AND_VIBRATION to "Sound and vibration",
    CueMode.VIBRATION to "Vibration only (silent)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val cues = remember { CuePlayer(context) }
    DisposableEffect(Unit) { onDispose { cues.shutdown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = barColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                Text("Signal a new phase with", fontSize = 18.sp)
                CueMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(settings.mode == mode, role = Role.RadioButton) {
                            onChange(settings.copy(mode = mode))
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.mode == mode, onClick = null)
                        Text(modeLabels.getValue(mode), fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
                    }
                }
            }
            Column {
                var volume by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
                val sound = settings.mode.sound
                Text("Timer volume  ${(volume * 100).toInt()}%", fontSize = 18.sp, color = dimUnless(sound))
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = { onChange(settings.copy(volume = volume)) },
                    enabled = sound,
                )
                Button(
                    onClick = {
                        cues.apply(settings.copy(volume = volume))
                        cues.play(Cue.WORK)
                        cues.speak("Work", delayMs = 400)
                    },
                ) { Text("Test the signal") }
            }
            ToggleRow("Lower other audio while a cue plays", settings.ducking, settings.mode.sound) { onChange(settings.copy(ducking = it)) }
            ToggleRow("Speak phase names", settings.voice, settings.mode.sound) { onChange(settings.copy(voice = it)) }
            ToggleRow("Keep screen on while running", settings.keepScreenOn) { onChange(settings.copy(keepScreenOn = it)) }
        }
    }
}

private fun dimUnless(enabled: Boolean) = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp, color = dimUnless(enabled), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
