package com.example.hotspotportal.ui.theme

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

private val Blue = Color(0xFF4A9EFF)
private val BlueDark = Color(0xFF1B6FC4)

private val DarkColors = darkColorScheme(
    primary = Blue,
    secondary = Color(0xFF3DDC97),
    error = Color(0xFFFF6B6B),
    background = Color(0xFF10131A),
    surface = Color(0xFF171B24),
)

private val LightColors = lightColorScheme(
    primary = BlueDark,
    secondary = Color(0xFF1E9E6A),
    error = Color(0xFFC62828),
)

@Composable
fun HotspotPortalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        // Material You on Android 12+, fixed scheme below.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
