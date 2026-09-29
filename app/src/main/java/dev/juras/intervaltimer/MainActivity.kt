package dev.juras.intervaltimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.juras.intervaltimer.ui.App
import dev.juras.intervaltimer.ui.theme.IntervalTimerTheme

class MainActivity : ComponentActivity() {
    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            IntervalTimerTheme {
                App()
            }
        }
    }
}
