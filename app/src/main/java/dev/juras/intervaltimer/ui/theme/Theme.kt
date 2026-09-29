package dev.juras.intervaltimer.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark and high contrast, always; no dynamic colour. Every colour the components read is set, so none falls back to the baseline purple. */
private val ColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    primaryContainer = BarGreen,
    onPrimaryContainer = Color.White,
    secondary = Accent,
    onSecondary = Color.Black,
    secondaryContainer = SurfaceRaised,
    onSecondaryContainer = Color.White,
    tertiary = Accent,
    background = Color.Black,
    onBackground = Color.White,
    surface = Surface,
    onSurface = Color.White,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Color.White,
    error = Danger,
    onError = Color.Black,
)

/** Material 3 Expressive: its spring motion and shapes, with our own colours and no dynamic colour. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IntervalTimerTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = ColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content,
    )
}
