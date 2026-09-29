package dev.juras.intervaltimer.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import dev.juras.intervaltimer.data.DefaultRoutines
import dev.juras.intervaltimer.data.Settings
import dev.juras.intervaltimer.engine.FakeClock
import dev.juras.intervaltimer.engine.Routine
import dev.juras.intervaltimer.engine.TimerEngine
import dev.juras.intervaltimer.engine.TimerState
import dev.juras.intervaltimer.ui.theme.IntervalTimerTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen to a PNG in `app/build/screens` when run with `recordRoborazziDebug`, so
 * the look can be checked without a phone. In a normal test run these only check that the screens
 * compose without crashing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { IntervalTimerTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private fun stateAt(routine: Routine, seconds: Double, skips: Int = 0): TimerState {
        val clock = FakeClock()
        val engine = TimerEngine(routine, clock).also { it.start() }
        clock.advanceSeconds(seconds)
        repeat(skips) { engine.skip() }
        return engine.state()
    }

    private fun running(state: TimerState) = @Composable {
        RunningScreen(state, keepScreenOn = false, snackbarHostState = SnackbarHostState(), onPause = {}, onResume = {}, onSkip = {}, onStop = {}, onClose = {})
    }

    private val tenByOneMinute = DefaultRoutines.all()[0]
    private val untilDone = DefaultRoutines.all()[1]

    @Test
    fun runningWorkScreen() = capture("running-work", running(stateAt(tenByOneMinute, 10.0 + 23.0)))

    @Test
    fun runningGetReadyScreen() = capture("running-get-ready", running(stateAt(tenByOneMinute, 3.0)))

    @Test
    fun runningRestScreen() = capture("running-rest", running(stateAt(untilDone, 5.0 + 40.0, skips = 1)))

    @Test
    fun runningWorkUntilDoneScreen() = capture("running-until-done", running(stateAt(untilDone, 5.0 + 42.0)))

    @Test
    fun runningTwoMinuteScreen() = capture("running-long", running(stateAt(Routine.timer("Plank", 300, 0), 100.0)))

    @Test
    fun finishedScreen() = capture("finished", running(stateAt(Routine.timer("t", 5, 0), 6.0)))

    @Test
    fun finishedUntilDoneScreen() {
        val clock = FakeClock()
        val engine = TimerEngine(Routine.untilDone("Pull-ups", restSeconds = 60, sets = 4, getReadySeconds = 0), clock).also { it.start() }
        listOf(42.0, 38.0, 45.0, 31.0).forEachIndexed { i, work ->
            clock.advanceSeconds(work)
            engine.skip()
            if (i < 3) clock.advanceSeconds(60.0)
        }
        capture("finished-until-done", running(engine.state()))
    }

    @Test
    fun routineListScreen() = capture("list") {
        RoutineListScreen(
            routines = DefaultRoutines.all() + Routine.repeat("Legs 8 × 40 s / 20 s", 40, 20, 8) + Routine.repeat("", 30, 15, 6),
            snackbarHostState = SnackbarHostState(),
            onStart = {}, onEdit = {}, onDuplicate = {}, onDelete = {}, onReorder = {}, onNew = {}, onSettings = {},
        )
    }

    @Test
    fun editorScreen() = capture("editor") {
        RoutineEditorScreen(untilDone, onChange = {}, onDone = {})
    }

    @Test
    fun settingsScreen() = capture("settings") {
        SettingsScreen(Settings(), onChange = {}, onBack = {})
    }
}
