package com.example.hotspotportal.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

/** Live state. 5.1:1 against white, so the label on it clears AA. */
val Go = Color(0xFF1B7F3B)

val PanelTint = Color(0xFFE6F4FB)
val Mist = Color(0xFFF6FAFD)
val White = Color(0xFFFFFFFF)
val Danger = Color(0xFFC62828)

/** How far a panel's shadow block sits from the panel. Shared so it reads as one system. */
val Lift = 5.dp

/**
 * A slanted rectangle - the shonen-UI panel shape.
 *
 * Compose's `graphicsLayer` scope has no `skewX` at this version, so the lean is
 * a real Shape instead of a transform. That is better anyway: the fill, the
 * border and the offset shadow are then all built from one outline and cannot
 * drift apart.
 */
class Parallelogram(private val lean: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val dx = size.width * lean
        return Outline.Generic(
            Path().apply {
                moveTo(dx, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - dx, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}

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
    shape: Shape = RectangleShape,
): Modifier = this
    .drawBehind {
        val o = lift.toPx()
        // The shadow is the same shape, offset - otherwise a non-rectangular
        // panel would sit on a square block and the mismatch would read as a bug.
        translate(o, o) {
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Generic -> drawPath(outline.path, stroke)
                is Outline.Rectangle -> drawRect(stroke, size = size)
                // Every radius in this app is 0, so a rounded outline cannot
                // actually occur. Fall back to the bounding rect rather than
                // dragging in a corner-radius call that would never run.
                else -> drawRect(stroke, size = size)
            }
        }
    }
    .background(fill, shape)
    .border(BorderStroke(strokeWidth, stroke), shape)

/**
 * A label with Japanese as the primary voice and English underneath, small.
 *
 * Used for every short label in the app so the Japanese reads first and the
 * English is still there as a gloss. Long sentences are not routed through this -
 * stacking a two-line sentence under its own translation is just clutter.
 */
@Composable
fun LabelText(
    jp: String,
    en: String,
    modifier: Modifier = Modifier,
    jpStyle: TextStyle = MaterialTheme.typography.titleSmall,
    enStyle: TextStyle = MaterialTheme.typography.labelSmall,
    jpColor: Color = MaterialTheme.colorScheme.onSurface,
    enColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(text = jp, style = jpStyle, color = jpColor, maxLines = maxLines)
        Text(text = en, style = enStyle, color = enColor, maxLines = maxLines)
    }
}

/**
 * The shared press interaction: a hard offset block behind, and a face that
 * slides down-right over it when held.
 *
 * Shared by [BrutalButton] and [BrutalAction] because it is the one interaction
 * the whole design rests on. The outer box never moves and reserves the lift as
 * extra height, so the face can travel without clipping.
 */
@Composable
private fun BrutalPressable(
    fill: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    height: Dp,
    lift: Dp,
    enabled: Boolean,
    contentAlignment: Alignment,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(height + lift)
            .drawBehind {
                val o = lift.toPx()
                drawRect(color = Ink, topLeft = Offset(o, o), size = size)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = if (pressed) lift else 0.dp, y = if (pressed) lift else 0.dp)
                .background(if (enabled) fill else fill.copy(alpha = 0.35f))
                .border(BorderStroke(2.dp, ink), RectangleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = contentAlignment,
            content = { content() },
        )
    }
}

/** A primary action: full-width chunky block, Japanese over English. */
@Composable
fun BrutalButton(
    jp: String,
    en: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    height: Dp = 64.dp,
    enabled: Boolean = true,
) {
    BrutalPressable(
        fill = fill,
        ink = Ink,
        onClick = onClick,
        modifier = modifier,
        height = height,
        lift = Lift,
        enabled = enabled,
        contentAlignment = Alignment.Center,
    ) {
        LabelText(
            jp = jp,
            en = en,
            jpStyle = MaterialTheme.typography.titleMedium,
            enStyle = MaterialTheme.typography.labelMedium,
            jpColor = contentColor,
            enColor = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * A secondary action: the same press behaviour, sized for a row of them.
 *
 * Replaces the M3 TextButton, which renders in default Material blue with no
 * border and was the loudest leftover against the hard-edged panels.
 */
@Composable
fun BrutalAction(
    jp: String,
    en: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    BrutalPressable(
        fill = fill,
        ink = Ink,
        onClick = onClick,
        modifier = modifier,
        height = 46.dp,
        lift = 3.dp,
        enabled = enabled,
        contentAlignment = Alignment.Center,
    ) {
        LabelText(
            jp = jp,
            en = en,
            jpStyle = MaterialTheme.typography.labelMedium,
            enStyle = MaterialTheme.typography.labelSmall,
            jpColor = contentColor,
            enColor = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * A square toggle that replaces the M3 Switch.
 *
 * The stock Switch is a rounded pill, and it was the last thing in the app
 * breaking the all-sharp rule. The track is a hard-bordered block and the thumb
 * is a square that slides between the two ends.
 */
@Composable
fun BrutalSwitch(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .width(SWITCH_W)
            .height(SWITCH_H + 2.dp)
            .drawBehind {
                val o = 2.dp.toPx()
                drawRect(color = Ink, topLeft = Offset(o, o), size = size)
            },
    ) {
        Box(
            modifier = Modifier
                .width(SWITCH_W)
                .height(SWITCH_H)
                .offset(
                    x = if (pressed) 2.dp else 0.dp,
                    y = if (pressed) 2.dp else 0.dp,
                )
                .background(if (checked) PopLemon else White)
                .border(BorderStroke(2.dp, Ink), RectangleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .offset(x = if (checked) SWITCH_W - SWITCH_THUMB - 6.dp else 0.dp)
                    .size(SWITCH_THUMB)
                    .background(if (checked) Ink else PanelTint)
                    .border(BorderStroke(2.dp, Ink), RectangleShape),
            )
        }
    }
}

private val SWITCH_W = 54.dp
private val SWITCH_H = 30.dp
private val SWITCH_THUMB = 22.dp
