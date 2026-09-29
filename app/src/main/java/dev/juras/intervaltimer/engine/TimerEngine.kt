package dev.juras.intervaltimer.engine

/** Monotonic milliseconds. Real code wraps `SystemClock.elapsedRealtime`; tests use a fake. */
fun interface MonotonicClock {
    fun nowMs(): Long
}

enum class Status { READY, RUNNING, PAUSED, FINISHED }

/**
 * A snapshot of the engine at one moment. [segment] and [next] are null when there is nothing
 * running / nothing after it.
 */
data class TimerState(
    val routineName: String,
    val status: Status,
    val segment: Segment?,
    val next: Segment?,
    val segmentIndex: Int,
    val segmentCount: Int,
    val segmentRemainingMs: Long,
    val totalRemainingMs: Long,
) {
    /** Whole seconds left in the segment, rounded up, so the display never shows 0 early. */
    val segmentSeconds: Int get() = ceilSeconds(segmentRemainingMs)
    val totalSeconds: Int get() = ceilSeconds(totalRemainingMs)

    private fun ceilSeconds(ms: Long) = ((ms + 999) / 1000).toInt()
}

/**
 * Runs a [Routine] against a clock. Position is always computed from elapsed time, so late polls,
 * pause/resume and skips cannot drift. Not thread-safe: call it from one thread.
 */
class TimerEngine(private val routine: Routine, private val clock: MonotonicClock) {
    private val timeline = Timeline.of(routine)
    private var bankedMs = 0L
    private var runningSinceMs: Long? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        runningSinceMs = clock.nowMs()
    }

    fun pause() {
        val since = runningSinceMs ?: return
        bankedMs = bankedMs + (clock.nowMs() - since)
        runningSinceMs = null
    }

    fun resume() {
        if (started && runningSinceMs == null && !isFinished()) runningSinceMs = clock.nowMs()
    }

    /** Jumps to the start of the next segment; skipping the last one finishes the routine. */
    fun skip() {
        if (!started || isFinished()) return
        val index = timeline.indexAt(elapsedMs())
        bankedMs = timeline.endMs(index)
        if (runningSinceMs != null) runningSinceMs = clock.nowMs()
    }

    fun state(): TimerState {
        val elapsed = elapsedMs()
        val index = timeline.indexAt(elapsed)
        val segments = timeline.segments
        val finished = started && index >= segments.size
        return TimerState(
            routineName = routine.name,
            status = when {
                !started -> Status.READY
                finished -> Status.FINISHED
                runningSinceMs == null -> Status.PAUSED
                else -> Status.RUNNING
            },
            segment = segments.getOrNull(index),
            next = segments.getOrNull(index + 1),
            segmentIndex = index,
            segmentCount = segments.size,
            segmentRemainingMs = if (index < segments.size) timeline.endMs(index) - elapsed else 0,
            totalRemainingMs = (timeline.totalMs - elapsed).coerceAtLeast(0),
        )
    }

    private fun isFinished() = elapsedMs() >= timeline.totalMs

    private fun elapsedMs(): Long {
        val running = runningSinceMs?.let { clock.nowMs() - it } ?: 0L
        return (bankedMs + running).coerceAtMost(timeline.totalMs)
    }
}
