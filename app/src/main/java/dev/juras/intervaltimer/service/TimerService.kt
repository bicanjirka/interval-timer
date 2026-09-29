package dev.juras.intervaltimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import dev.juras.intervaltimer.MainActivity
import dev.juras.intervaltimer.R
import dev.juras.intervaltimer.data.RoutineJson
import dev.juras.intervaltimer.data.SettingsStore
import dev.juras.intervaltimer.engine.Routine
import dev.juras.intervaltimer.engine.Status
import dev.juras.intervaltimer.engine.TimerEngine
import dev.juras.intervaltimer.engine.TimerState
import dev.juras.intervaltimer.engine.formatSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs one routine in the foreground so it keeps going with the screen off. The UI reads [state]
 * and sends commands through the companion functions; timing lives in [TimerEngine].
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: TimerEngine? = null
    private var loop: Job? = null
    private lateinit var cues: CuePlayer
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        cues = CuePlayer(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "intervaltimer:running")
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        scope.launch { SettingsStore(this@TimerService).settings.collect { cues.apply(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_ROUTINE)?.let { begin(RoutineJson.decode(it).first()) }
            ACTION_PAUSE -> engine?.pause()
            ACTION_RESUME -> engine?.resume()
            ACTION_SKIP -> engine?.skip()
            ACTION_STOP -> end()
        }
        if (engine == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        cues.shutdown()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun begin(routine: Routine) {
        loop?.cancel()
        val timer = TimerEngine(routine) { SystemClock.elapsedRealtime() }
        engine = timer
        timer.start()
        startForeground(NOTIFICATION_ID, notification(timer.state()), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        loop = scope.launch { run(timer) }
    }

    private suspend fun run(timer: TimerEngine) {
        var published: List<Any?>? = null
        var cueIndex = -1
        var tickedSecond = -1
        while (scope.isActive) {
            val state = timer.state()
            val key = listOf(state.status, state.segmentIndex, state.displaySeconds, state.totalSeconds)
            if (key != published) {
                published = key
                mutableState.value = state
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
            }
            keepAwake(state)
            if (state.status == Status.FINISHED) {
                finish()
                return
            }
            val segment = state.segment
            if (state.status == Status.RUNNING && segment != null) {
                if (state.segmentIndex != cueIndex) {
                    cueIndex = state.segmentIndex
                    tickedSecond = -1
                    cues.play(Cue.startOf(segment.kind))
                    cues.speak(segment.spoken, delayMs = START_SPEECH_DELAY_MS)
                } else if (segment.durationMs > 3000 && state.segmentSeconds in 1..3 && state.segmentSeconds != tickedSecond) {
                    tickedSecond = state.segmentSeconds
                    cues.play(Cue.TICK)
                }
            }
            delay(POLL_MS)
        }
    }

    private suspend fun finish() {
        cues.play(Cue.DONE)
        cues.speak(getString(R.string.done_spoken), delayMs = DONE_SPEECH_DELAY_MS)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (wakeLock.isHeld) wakeLock.release()
        delay(FINISH_LINGER_MS)
        engine = null
        stopSelf()
    }

    private fun end() {
        loop?.cancel()
        engine = null
        mutableState.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun keepAwake(state: TimerState) {
        if (state.status == Status.RUNNING && !wakeLock.isHeld) wakeLock.acquire(state.totalRemainingMs + WAKE_SLACK_MS)
        if (state.status == Status.PAUSED && wakeLock.isHeld) wakeLock.release()
    }

    private fun notification(state: TimerState): Notification {
        val segment = state.segment
        val paused = state.status == Status.PAUSED
        val title = segment?.let { "${it.name}  ${formatSeconds(state.displaySeconds)}" } ?: state.routineName
        val text = buildString {
            append(state.routineName)
            if (!state.openEnded) append(" · ").append(getString(R.string.total_left, formatSeconds(state.totalSeconds)))
            state.next?.let { append(" · ").append(getString(R.string.next_up, it.name)) }
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action(if (paused) ACTION_RESUME else ACTION_PAUSE, if (paused) R.string.resume else R.string.pause))
            .addAction(action(ACTION_SKIP, if (segment?.manual == true) R.string.done else R.string.skip))
            .addAction(action(ACTION_STOP, R.string.stop))
            .build()
    }

    private fun action(action: String, label: Int): Notification.Action {
        val intent = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, TimerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_timer), getString(label), intent).build()
    }

    companion object {
        private const val ACTION_START = "dev.juras.intervaltimer.START"
        private const val ACTION_PAUSE = "dev.juras.intervaltimer.PAUSE"
        private const val ACTION_RESUME = "dev.juras.intervaltimer.RESUME"
        private const val ACTION_SKIP = "dev.juras.intervaltimer.SKIP"
        private const val ACTION_STOP = "dev.juras.intervaltimer.STOP"
        private const val EXTRA_ROUTINE = "routine"
        private const val CHANNEL = "timer"
        private const val NOTIFICATION_ID = 1
        private const val POLL_MS = 50L
        private const val START_SPEECH_DELAY_MS = 350L
        private const val DONE_SPEECH_DELAY_MS = 900L
        private const val FINISH_LINGER_MS = 5000L
        private const val WAKE_SLACK_MS = 60_000L

        private val mutableState = MutableStateFlow<TimerState?>(null)

        /** The running (or just finished) routine, null when nothing is running. */
        val state: StateFlow<TimerState?> = mutableState

        fun start(context: Context, routine: Routine) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROUTINE, RoutineJson.encode(listOf(routine)))
            context.startForegroundService(intent)
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun skip(context: Context) = send(context, ACTION_SKIP)
        fun stop(context: Context) = send(context, ACTION_STOP)

        /** Clears the finished state once the user has seen it. */
        fun dismiss() {
            if (mutableState.value?.status == Status.FINISHED) mutableState.value = null
        }

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, TimerService::class.java).setAction(action))
        }
    }
}
