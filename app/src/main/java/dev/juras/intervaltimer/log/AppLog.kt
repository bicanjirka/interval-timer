package dev.juras.intervaltimer.log

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The app's log: every line goes to logcat (tag `IntervalTimer`) and to a file that survives the
 * logcat buffer rolling over during a long workout. The file lives in the app's external files
 * directory, so it can be fetched from a debug or release build without root:
 * `adb pull /sdcard/Android/data/dev.juras.intervaltimer/files/logs/`.
 * Uncaught exceptions are logged with their stack trace before the app dies.
 */
object AppLog {
    private const val TAG = "IntervalTimer"

    private var file: LogFile? = null
    private val writer = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "app-log").apply { isDaemon = true } }

    fun init(context: Context) {
        val dir = context.getExternalFilesDir("logs") ?: File(context.filesDir, "logs")
        file = LogFile(File(dir, "timer.log"))
        installCrashLogging()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        i("App", "started v$version, ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    fun d(tag: String, message: String) = log(Log.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Log.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = log(Log.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = log(Log.ERROR, tag, message, error)

    private fun log(priority: Int, tag: String, message: String, error: Throwable?) {
        Log.println(priority, TAG, "$tag: $message" + (error?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
        val target = file ?: return
        val line = format(System.currentTimeMillis(), priority, tag, message, error)
        writer.execute { runCatching { target.append(line) } }
    }

    private fun installCrashLogging() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Written directly: the app may be gone before the writer thread runs.
            runCatching { file?.append(format(System.currentTimeMillis(), Log.ERROR, "Crash", "uncaught in ${thread.name}", error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun format(now: Long, priority: Int, tag: String, message: String, error: Throwable?): String {
        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(now))
        val level = when (priority) {
            Log.ERROR -> "E"
            Log.WARN -> "W"
            Log.DEBUG -> "D"
            else -> "I"
        }
        return "$time $level $tag: $message" + (error?.let { "\n" + Log.getStackTraceString(it).trimEnd() } ?: "")
    }
}
