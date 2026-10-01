package dev.juras.intervaltimer.engine

/** Monotonic milliseconds. Real code wraps `SystemClock.elapsedRealtime`; tests use a fake. */
fun interface MonotonicClock {
    fun nowMs(): Long
}

enum class Status { READY, RUNNING, PAUSED, FINISHED }

/**
 * A snapshot of the engine at one moment. [segment] and [next] are null when there is nothing
 * running / nothing after it. For a manual segment the time counts up ([segmentElapsedMs]) and
 * [segmentRemainingMs] is 0; [openEnded] says the total left cannot be known because a manual
 * segment is still to come (or running). [results] holds how long each segment really took, and
 * is only filled once the routine is [Status.FINISHED].
 */
data class TimerState(
    val routineName: String,
    val status: Status,
    val segment: Segment?,
    val next: Segment?,
    val segmentIndex: Int,
    val segmentCount: Int,
    val segmentElapsedMs: Long,
    val segmentRemainingMs: Long,
    val totalRemainingMs: Long,
    val openEnded: Boolean,
    val results: List<SegmentResult> = emptyList(),
    val rating: Int? = null,
    val rateEffort: Boolean = false,
) {
    /** Whole seconds left in the segment, rounded up, so the display never shows 0 early. */
    val segmentSeconds: Int get() = ceilSeconds(segmentRemainingMs)
    val totalSeconds: Int get() = ceilSeconds(totalRemainingMs)

    /** What the big digits show: seconds counting up for a manual segment, else counting down. */
    val displaySeconds: Int
        get() = if (segment?.manual == true) (segmentElapsedMs / 1000).toInt() else segmentSeconds

    private fun ceilSeconds(ms: Long) = ((ms + 999) / 1000).toInt()
}

/**
 * How long [segment] really lasted: less than planned when skipped, the user's own time when manual.
 * [rating] is how hard the user said it was ([MIN_RATING]..[MAX_RATING]), null when not rated.
 */
data class SegmentResult(val segment: Segment, val actualMs: Long, val rating: Int? = null)

const val MIN_RATING = 1
const val MAX_RATING = 10

/**
 * Runs a [Routine] against a clock. Position is computed from elapsed time, so late polls,
 * pause/resume and skips cannot drift. A manual segment never ends by itself: [skip] is how the
 * user says "done". Not thread-safe: call it from one thread.
 */
class TimerEngine(private val routine: Routine, private val clock: MonotonicClock) {
    private val segments = Timeline.of(routine).segments
    private var index = 0
    private var bankedMs = 0L
    private var runningSinceMs: Long? = null
    private var started = false
    private val results = mutableListOf<SegmentResult>()
    private val ratings = mutableMapOf<Int, Int>()

    fun start() {
        if (started) return
        started = true
        runningSinceMs = clock.nowMs()
    }

    fun pause() {
        settle()
        val since = runningSinceMs ?: return
        bankedMs += clock.nowMs() - since
        runningSinceMs = null
    }

    fun resume() {
        settle()
        if (started && runningSinceMs == null && index < segments.size) runningSinceMs = clock.nowMs()
    }

    /** Jumps to the start of the next segment; on a manual segment this is "done". */
    fun skip() {
        settle()
        if (!started || index >= segments.size) return
        results += SegmentResult(segments[index], elapsedInSegment(), ratings[index])
        index++
        bankedMs = 0
        if (runningSinceMs != null) runningSinceMs = clock.nowMs()
    }

    /**
     * Rates how hard the current segment was; null clears it. Ignored unless the routine has
     * [Routine.rateEffort], outside work segments and for numbers outside [MIN_RATING]..[MAX_RATING].
     * Works while paused, and up to the end of the segment.
     */
    fun rate(rating: Int?) {
        settle()
        val segment = segments.getOrNull(index) ?: return
        if (!routine.rateEffort || !started || !segment.rateable) return
        when {
            rating == null -> ratings.remove(index)
            rating in MIN_RATING..MAX_RATING -> ratings[index] = rating
        }
    }

    fun state(): TimerState {
        settle()
        val current = segments.getOrNull(index)
        val elapsed = elapsedInSegment()
        val remaining = if (current == null || current.manual) 0L else current.durationMs - elapsed
        val later = segments.drop(index + 1)
        return TimerState(
            routineName = routine.name,
            status = when {
                !started -> Status.READY
                current == null -> Status.FINISHED
                runningSinceMs == null -> Status.PAUSED
                else -> Status.RUNNING
            },
            segment = current,
            next = segments.getOrNull(index + 1),
            segmentIndex = index,
            segmentCount = segments.size,
            segmentElapsedMs = if (current == null) 0 else elapsed,
            segmentRemainingMs = remaining,
            totalRemainingMs = remaining + later.sumOf { it.durationMs },
            openEnded = current?.manual == true || later.any { it.manual },
            results = if (current == null) results.toList() else emptyList(),
            rating = ratings[index],
            rateEffort = routine.rateEffort,
        )
    }

    /** Moves past every timed segment that has run out, carrying the overflow into the next. */
    private fun settle() {
        if (!started) return
        val now = clock.nowMs()
        var elapsed = elapsedInSegment()
        while (index < segments.size) {
            val s = segments[index]
            if (s.manual || elapsed < s.durationMs) break
            elapsed -= s.durationMs
            results += SegmentResult(s, s.durationMs, ratings[index])
            index++
        }
        bankedMs = if (index < segments.size) elapsed else 0
        if (runningSinceMs != null) runningSinceMs = if (index < segments.size) now else null
    }

    private fun elapsedInSegment(): Long = bankedMs + (runningSinceMs?.let { clock.nowMs() - it } ?: 0L)
}
