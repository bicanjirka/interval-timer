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
import dev.juras.intervaltimer.log.AppLog
import dev.juras.intervaltimer.ui.title
import kotlinx.coroutines.CancellationException
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
    private var stopJob: Job? = null
    private var pausedBeforeStop = false
    private lateinit var cues: CuePlayer
    private lateinit var wakeLock: PowerManager.WakeLock
    private val settingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        cues = CuePlayer(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "intervaltimer:running")
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        scope.launch { settingsStore.settings.collect { cues.apply(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        AppLog.i(TAG, "command ${action?.substringAfterLast('.')} (engine ${if (engine == null) "absent" else "present"})")
        try {
            when (action) {
                ACTION_START -> intent.getStringExtra(EXTRA_ROUTINE)?.let { begin(RoutineJson.decode(it).first()) }
                ACTION_PAUSE -> { cancelStop(restore = false); engine?.pause() }
                ACTION_RESUME -> { cancelStop(restore = false); engine?.resume() }
                ACTION_SKIP -> { cancelStop(restore = false); engine?.skip() }
                ACTION_RATE -> {
                    val rating = intent.getIntExtra(EXTRA_RATING, 0).takeIf { it > 0 }
                    AppLog.i(TAG, "rating ${rating ?: "cleared"}")
                    engine?.rate(rating)
                }
                ACTION_STOP -> requestStop()
                ACTION_UNDO_STOP -> cancelStop(restore = true)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "command $action failed", e)
        }
        if (engine == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLog.i(TAG, "destroyed (engine ${if (engine == null) "absent" else "still running"})")
        scope.cancel()
        cues.shutdown()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun begin(routine: Routine) {
        cancelStop(restore = false)
        loop?.cancel()
        val timer = TimerEngine(routine) { SystemClock.elapsedRealtime() }
        engine = timer
        startForeground(NOTIFICATION_ID, notification(timer.state()), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        loop = scope.launch {
            // The saved cue mode must be in place before the first cue, or a silent-mode
            // workout would open with a beep. The routine's clock starts only afterwards.
            cues.apply(settingsStore.current())
            timer.start()
            AppLog.i(TAG, "routine '${routine.name}' started, ${timer.state().segmentCount} segments")
            run(timer)
        }
    }

    private suspend fun run(timer: TimerEngine) {
        var published: List<Any?>? = null
        var lastStatus: Status? = null
        var lastIndex = -1
        var cueIndex = -1
        var tickedSecond = -1
        var lastTickAt = SystemClock.elapsedRealtime()
        while (scope.isActive) {
            val tickAt = SystemClock.elapsedRealtime()
            if (tickAt - lastTickAt > STALL_LOG_MS) {
                AppLog.w(TAG, "timer loop stalled for ${tickAt - lastTickAt} ms (cues may be late; screen off or Doze?)")
            }
            lastTickAt = tickAt
            try {
                val state = timer.state()
                if (state.status != lastStatus) {
                    AppLog.i(TAG, "status $lastStatus -> ${state.status}")
                    lastStatus = state.status
                }
                if (state.segmentIndex != lastIndex) {
                    lastIndex = state.segmentIndex
                    AppLog.i(TAG, "segment ${state.segmentIndex + 1}/${state.segmentCount}: ${describe(state)}")
                }
                val key = listOf(state.status, state.segmentIndex, state.displaySeconds, state.totalSeconds, state.rating)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "tick failed; carrying on", e)
            }
            delay(POLL_MS)
        }
    }

    private fun describe(state: TimerState): String {
        val s = state.segment ?: return "none"
        val length = if (s.manual) "until done" else "${s.durationMs / 1000} s"
        return "'${s.name}' $length, round ${s.round}/${s.rounds}, next ${state.next?.name}"
    }

    private suspend fun finish() {
        AppLog.i(TAG, "routine finished")
        cues.play(Cue.DONE)
        cues.speak(getString(R.string.done_spoken), delayMs = DONE_SPEECH_DELAY_MS)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (wakeLock.isHeld) wakeLock.release()
        delay(FINISH_LINGER_MS)
        engine = null
        stopSelf()
    }

    /**
     * Stop is not final at once: the routine is paused for [STOP_GRACE_MS] while the UI offers "Undo stop",
     * and only then really ends. Asking again during that time changes nothing.
     */
    private fun requestStop() {
        val timer = engine ?: return
        if (stopJob?.isActive == true) return
        pausedBeforeStop = timer.state().status == Status.PAUSED
        timer.pause()
        AppLog.i(TAG, "stop requested, ending in $STOP_GRACE_MS ms unless undone")
        mutablePendingStop.value = true
        stopJob = scope.launch {
            delay(STOP_GRACE_MS)
            end()
        }
    }

    /** Drops a pending stop; [restore] also continues the routine unless it was paused before. */
    private fun cancelStop(restore: Boolean) {
        val pending = stopJob?.isActive == true
        stopJob?.cancel()
        stopJob = null
        mutablePendingStop.value = false
        if (pending) {
            AppLog.i(TAG, "stop undone")
            if (restore && !pausedBeforeStop) engine?.resume()
        }
    }

    private fun end() {
        AppLog.i(TAG, "routine stopped by the user")
        stopJob = null
        mutablePendingStop.value = false
        loop?.cancel()
        engine = null
        mutableState.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (wakeLock.isHeld) wakeLock.release()
        stopSelf()
    }

    private fun keepAwake(state: TimerState) {
        if (state.status == Status.RUNNING && !wakeLock.isHeld) {
            wakeLock.acquire(state.totalRemainingMs + WAKE_SLACK_MS)
            AppLog.d(TAG, "wake lock acquired")
        }
        if (state.status == Status.PAUSED && wakeLock.isHeld) {
            wakeLock.release()
            AppLog.d(TAG, "wake lock released (paused)")
        }
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
        private const val ACTION_UNDO_STOP = "dev.juras.intervaltimer.UNDO_STOP"
        private const val ACTION_RATE = "dev.juras.intervaltimer.RATE"
        private const val EXTRA_ROUTINE = "routine"
        private const val EXTRA_RATING = "rating"
        private const val CHANNEL = "timer"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "Service"
        private const val POLL_MS = 50L
        private const val STALL_LOG_MS = 500L
        private const val START_SPEECH_DELAY_MS = 350L
        private const val DONE_SPEECH_DELAY_MS = 900L
        private const val FINISH_LINGER_MS = 5000L
        private const val WAKE_SLACK_MS = 60_000L
        private const val STOP_GRACE_MS = 3000L

        private val mutableState = MutableStateFlow<TimerState?>(null)
        private val mutablePendingStop = MutableStateFlow(false)

        /** The running (or just finished) routine, null when nothing is running. */
        val state: StateFlow<TimerState?> = mutableState

        /** True during the few seconds after Stop when it can still be undone. */
        val stopPending: StateFlow<Boolean> = mutablePendingStop

        fun start(context: Context, routine: Routine) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROUTINE, RoutineJson.encode(listOf(routine.copy(name = routine.title()))))
            AppLog.i(TAG, "start requested for '${routine.title()}'")
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                AppLog.e(TAG, "could not start the service", e)
            }
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun skip(context: Context) = send(context, ACTION_SKIP)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun undoStop(context: Context) = send(context, ACTION_UNDO_STOP)

        /** Rates the current work phase; null clears the rating. */
        fun rate(context: Context, rating: Int?) =
            send(context, ACTION_RATE) { it.putExtra(EXTRA_RATING, rating ?: 0) }

        /** Clears the finished state once the user has seen it. */
        fun dismiss() {
            if (mutableState.value?.status == Status.FINISHED) mutableState.value = null
        }

        private fun send(context: Context, action: String, extras: (Intent) -> Intent = { it }) {
            try {
                context.startService(extras(Intent(context, TimerService::class.java).setAction(action)))
            } catch (e: Exception) {
                AppLog.e(TAG, "could not send ${action.substringAfterLast('.')} to the service", e)
            }
        }
    }
}
