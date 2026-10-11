package it.sansoale.uscite.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import it.sansoale.uscite.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B3F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F1BC),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF8A5A2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF2F6FB5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4E3FF),
    onTertiaryContainer = Color(0xFF001C3A),
    background = Color(0xFFF8FAF3),
    onBackground = Color(0xFF191D18),
    surface = Color(0xFFF8FAF3),
    onSurface = Color(0xFF191D18),
    surfaceVariant = Color(0xFFDDE5D9),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717970),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF97D5A1),
    onPrimary = Color(0xFF003917),
    primaryContainer = Color(0xFF125229),
    onPrimaryContainer = Color(0xFFB2F1BC),
    secondary = Color(0xFFFFB778),
    onSecondary = Color(0xFF4C2700),
    secondaryContainer = Color(0xFF6D4316),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFFA5C8FF),
    onTertiary = Color(0xFF00315E),
    tertiaryContainer = Color(0xFF0B4884),
    onTertiaryContainer = Color(0xFFD4E3FF),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE1E3DC),
    surface = Color(0xFF11140F),
    onSurface = Color(0xFFE1E3DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    outline = Color(0xFF8B9389),
)

@Composable
fun UsciteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
