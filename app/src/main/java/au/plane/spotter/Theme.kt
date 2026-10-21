package au.plane.spotter

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Sky = Color(0xFF1E88E5)
val DeepSky = Color(0xFF0B3D91)
val Sunset = Color(0xFFFF8F00)
val SpottedGreen = Color(0xFF2E7D32)

private val Light = lightColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = DeepSky,
    secondary = Sunset,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF4E2600),
    tertiary = SpottedGreen,
    background = Color(0xFFF4F8FC),
    surface = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF002F5E),
    primaryContainer = Color(0xFF0B3D91),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF3E2000),
    secondaryContainer = Color(0xFF6B3A00),
    onSecondaryContainer = Color(0xFFFFE0B2),
    tertiary = Color(0xFF81C784),
    background = Color(0xFF0E1621),
    surface = Color(0xFF16202C),
)

@Composable
fun PlaneSpotterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
