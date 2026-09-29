package dev.juras.intervaltimer

import android.app.Application
import dev.juras.intervaltimer.log.AppLog

class IntervalTimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
    }
}
