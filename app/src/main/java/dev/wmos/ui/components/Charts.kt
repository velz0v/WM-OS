package dev.wmos.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.wmos.ui.theme.Lime

/**
 * Circular progress ring with a spring-free eased growth from 0 to [progress].
 * A short label can be drawn inside the ring by the caller.
 */
@Composable
fun AnimatedProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    ringColor: Color = Lime,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 8.dp,
    content: @Composable () -> Unit = {},
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (started) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "progressRing",
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            if (trackColor != Color.Transparent) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * Weekly bar chart. Bars grow from the bottom with a small per-bar delay,
 * the maximum (or [highlightIndex]) is filled with the accent colour.
 */
@Composable
fun AnimatedBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = Color.Transparent,
    highlightColor: Color = Lime,
    highlightIndex: Int = -1,
    chartHeight: Dp = 96.dp,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val resolvedBar = if (barColor == Color.Transparent) MaterialTheme.colorScheme.surfaceContainerHighest else barColor
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            val target = (value / max).coerceIn(0.04f, 1f)
            val animated by animateFloatAsState(
                targetValue = if (started) target else 0f,
                animationSpec = tween(
                    durationMillis = 620,
                    delayMillis = index * 60,
                    easing = FastOutSlowInEasing,
                ),
                label = "bar$index",
            )
            val color = if (index == highlightIndex) highlightColor else resolvedBar
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                ) {
                    val barWidth = this.size.width
                    val barHeight = this.size.height * animated
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(0f, this.size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = labels.getOrElse(index) { "" },
                style = MaterialTheme.typography.labelSmall,
                color = if (index == highlightIndex) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Linear animated progress bar with rounded caps and a soft gradient fill.
 */
@Composable
fun AnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    barColor: Color = Lime,
    trackColor: Color = Color.Transparent,
    height: Dp = 10.dp,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (started) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progressBar",
    )
    val resolvedTrack = if (trackColor == Color.Transparent) MaterialTheme.colorScheme.surfaceContainerHighest else trackColor
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val cap = this.size.height / 2f
        drawRoundRect(
            color = resolvedTrack,
            size = Size(this.size.width, this.size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cap, cap),
        )
        if (animated > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.7f), barColor)),
                size = Size(this.size.width * animated, this.size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cap, cap),
            )
        }
    }
}

/**
 * Smooth area chart (used for at-a-glance trends). An area grows bottom-up;
 * polyline is soft-transparent, the current point is highlighted.
 */
@Composable
fun AnimatedAreaChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Lime,
    fillColor: Color = Color.Transparent,
    chartHeight: Dp = 72.dp,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "areaChart",
    )
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val min = (values.minOrNull() ?: 0f).coerceAtMost(0f)
    val resolvedFill = if (fillColor == Color.Transparent) lineColor.copy(alpha = 0.18f) else fillColor
    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight)) {
        if (values.size < 2) return@Canvas
        val step = this.size.width / (values.size - 1)

        fun yFor(v: Float): Float {
            val norm = ((v - min) / (max - min).coerceAtLeast(1f)).coerceIn(0f, 1f)
            return this.size.height - norm * this.size.height * 0.9f - this.size.height * 0.05f
        }
        val path = Path()
        val fillPath = Path()
        values.forEachIndexed { index, v ->
            val x = step * index
            val y = yFor(v) + (this.size.height - yFor(v)) * (1f - animated)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, this.size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(this.size.width, this.size.height)
        fillPath.close()
        drawPath(fillPath, color = resolvedFill, style = Fill)
        drawPath(
            path,
            brush = Brush.horizontalGradient(listOf(lineColor.copy(alpha = 0.5f), lineColor)),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
