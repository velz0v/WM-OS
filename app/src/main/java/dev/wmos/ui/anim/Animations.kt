package dev.wmos.ui.anim

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Scales the element down while pressed and springs back on release.
 * Pass the same [MutableInteractionSource] that also drives the ripple of the clickable.
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.96f): Modifier =
    composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "pressScale",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

/** Creates a shared interaction source for press effects applied through [pressScale]. */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Gentle infinite pulse for attention elements (empty-state icons, hint dots). */
fun Modifier.softPulse(minScale: Float = 1f, maxScale: Float = 1.05f, durationMs: Int = 2000): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "softPulse")
        val scale by transition.animateFloat(
            initialValue = minScale,
            targetValue = maxScale,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "softPulseScale",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

/** Draws a slow highlight sweep over the content — used by skeleton placeholders. */
fun Modifier.shimmerSweep(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    drawWithContent {
        drawContent()
        val band = size.width / 2f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                start = Offset(progress * size.width - band, 0f),
                end = Offset(progress * size.width - band + band, size.height),
            ),
        )
    }
}
