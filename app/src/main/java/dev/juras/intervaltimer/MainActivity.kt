package dev.juras.intervaltimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.juras.intervaltimer.log.AppLog
import dev.juras.intervaltimer.ui.App
import dev.juras.intervaltimer.ui.theme.IntervalTimerTheme

class MainActivity : ComponentActivity() {
    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AppLog.i(TAG, "notification permission ${if (granted) "granted" else "denied"}")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        AppLog.i(TAG, "created (restored ${savedInstanceState != null}), notification permission $notifications")
        if (!notifications) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            IntervalTimerTheme {
                App()
            }
        }
    }

    override fun onDestroy() {
        AppLog.i(TAG, "destroyed (finishing $isFinishing)")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Activity"
    }
}
