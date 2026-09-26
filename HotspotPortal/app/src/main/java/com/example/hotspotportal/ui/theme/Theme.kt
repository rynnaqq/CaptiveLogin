package com.example.hotspotportal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Rimuru Portal's palette.
 *
 * Ocean blue carries every action, dark navy marks accents and headings, and
 * surfaces stay white so the artwork on the sign-in page is the only thing with
 * colour on it.
 *
 * Material You dynamic colour is deliberately NOT used. On Android 12+ it would
 * repaint the whole app from the user's wallpaper and the branding would depend
 * on which wallpaper happens to be set.
 */
private val OceanBlue = Color(0xFF0077B6)
private val Navy = Color(0xFF023E8A)
private val OceanDeep = Color(0xFF005A8C)
private val OceanTint = Color(0xFFE6F4FB)
private val Mist = Color(0xFFF6FAFD)
private val White = Color(0xFFFFFFFF)
private val Danger = Color(0xFFC62828)

private val RimuruColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = White,
    primaryContainer = OceanTint,
    onPrimaryContainer = Navy,
    secondary = Navy,
    onSecondary = White,
    secondaryContainer = OceanTint,
    onSecondaryContainer = Navy,
    tertiary = OceanDeep,
    onTertiary = White,
    background = White,
    onBackground = Navy,
    surface = White,
    onSurface = Navy,
    surfaceVariant = Mist,
    onSurfaceVariant = Navy,
    outline = OceanBlue,
    outlineVariant = OceanTint,
    error = Danger,
    onError = White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF7F1D1D),
    // M3 derives the container roles from a purple baseline when they are left
    // unset, which is what tinted every card and the navigation bar lavender.
    surfaceBright = White,
    surfaceDim = Mist,
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF3F9FD),
    surfaceContainer = Color(0xFFEBF4FA),
    surfaceContainerHigh = Color(0xFFE2EFF8),
    surfaceContainerHighest = Color(0xFFD9E9F5),
    inverseSurface = Navy,
    inverseOnSurface = White,
    scrim = Color(0x99023E8A),
)

/** Softer than the M3 default: the cards read as panels rather than boxes. */
private val RimuruShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RimuruPortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RimuruColors,
        shapes = RimuruShapes,
        content = content,
    )
}
