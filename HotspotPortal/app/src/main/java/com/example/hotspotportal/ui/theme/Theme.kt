package com.example.hotspotportal.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Rimuru Portal's palette: neobrutalist structure, anime-pop accents.
 *
 * Two vocabularies are fused here rather than layered. A die-cut anime sticker is
 * a colour block with a thick keyline; neobrutalism is a colour block with a thick
 * border and a hard offset shadow. Making the sticker keyline and the brutalist
 * border the same object is what keeps the two styles from fighting.
 *
 * Pink is THE accent and is deliberately rationed: it is 3.5:1 against white, so
 * it only ever carries large bold text, never a small label. Lemon carries small
 * text instead (13.8:1 against ink). Ocean blue stays the brand field.
 *
 * Material You dynamic colour is deliberately NOT used. On Android 12+ it would
 * repaint the whole app from the user's wallpaper and a palette this structural
 * cannot survive being derived from an arbitrary photo.
 */

/** Structure colour: every border, every shadow, every heading. Never pure black. */
val Ink = Color(0xFF0A0A0A)

val Ocean = Color(0xFF0077B6)
val Navy = Color(0xFF023E8A)
val OceanDeep = Color(0xFF005A8C)

/** The one accent. Large bold text on it, nothing else. */
val PopPink = Color(0xFFFF2D95)

/** Rare tertiary. Ink text on it, which is the only pairing that stays readable. */
val PopLemon = Color(0xFFFFD400)

val PanelTint = Color(0xFFE6F4FB)
val Mist = Color(0xFFF6FAFD)
val White = Color(0xFFFFFFFF)
val Danger = Color(0xFFC62828)

/** How far a panel's shadow block sits from the panel. Shared so it reads as one system. */
val Lift = 5.dp

private val RimuruColors = lightColorScheme(
    primary = Ocean,
    onPrimary = White,
    primaryContainer = PanelTint,
    onPrimaryContainer = Navy,
    secondary = Navy,
    onSecondary = White,
    secondaryContainer = PopLemon,
    onSecondaryContainer = Ink,
    tertiary = PopPink,
    onTertiary = White,
    background = White,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = PanelTint,
    onSurfaceVariant = Ink,
    // The single highest-leverage neobrutalist change: M3's default outline is a
    // translucent version of the primary, which reads as soft Material. Ink makes
    // every divider and field border a hard structural line.
    outline = Ink,
    outlineVariant = Ink,
    error = Danger,
    onError = White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF7F1D1D),
    surfaceBright = White,
    surfaceDim = PanelTint,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = Mist,
    surfaceContainerHigh = PanelTint,
    surfaceContainerHighest = Color(0xFFDCEBF7),
    inverseSurface = Ink,
    inverseOnSurface = White,
    scrim = Color(0x99000000),
)

/**
 * Every radius in the app is 0.
 *
 * One scale, no exceptions: rounded buttons on square cards is broken design, and
 * a die-cut sticker is angular anyway. Setting it here flattens every card, text
 * field, dialog and button at once.
 */
private val SharpShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

/**
 * Deliberately close to stock M3.
 *
 * An earlier pass pushed headings to Black/ExtraBold and added negative tracking
 * up to -1sp on top of all-caps labels. Combined with the chunky borders and
 * offset shadows that reads as aggressive rather than punchy - the structure
 * already carries the style, so the type only has to stay legible. Weights are
 * one notch above default and letterSpacing is left entirely alone.
 */
private val SharpType = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Normal),
        bodyMedium = base.bodyMedium.copy(fontWeight = FontWeight.Normal),
        bodySmall = base.bodySmall.copy(fontWeight = FontWeight.Normal),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun RimuruPortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RimuruColors,
        shapes = SharpShapes,
        typography = SharpType,
        content = content,
    )
}

/**
 * A neobrutalist panel: flat fill, hard ink border, solid block behind it.
 *
 * Compose shadows are always blurred, which is the exact opposite of the look, so
 * the "shadow" is a second rect drawn at a hard offset. Drawn behind the fill in
 * the same coordinate space, which means it only stays visible on the bottom and
 * right edges - the panel does not need to reserve space for it.
 */
@Composable
fun Modifier.brutalPanel(
    fill: Color = MaterialTheme.colorScheme.surface,
    stroke: Color = Ink,
    strokeWidth: Dp = 2.dp,
    lift: Dp = Lift,
): Modifier = this
    .drawBehind {
        val o = lift.toPx()
        drawRect(color = stroke, topLeft = Offset(o, o), size = size)
    }
    .background(fill)
    .border(BorderStroke(strokeWidth, stroke), RectangleShape)

/**
 * A chunky tappable block that sinks into its own shadow when pressed.
 *
 * The shadow lives on an outer box that never moves while the inner block slides
 * down-right by [Lift] and covers it - that is the whole effect, and it is why
 * the outer box reserves the lift as extra height.
 *
 * Used for primary actions only. Secondary actions stay as M3 buttons, which now
 * inherit 0dp corners from the theme.
 */
@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    height: Dp = 56.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(height + Lift)
            .drawBehind {
                val o = Lift.toPx()
                drawRect(color = Ink, topLeft = Offset(o, o), size = size)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = if (pressed) Lift else 0.dp, y = if (pressed) Lift else 0.dp)
                .background(if (enabled) fill else fill.copy(alpha = 0.35f))
                .border(BorderStroke(2.dp, Ink), RectangleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
