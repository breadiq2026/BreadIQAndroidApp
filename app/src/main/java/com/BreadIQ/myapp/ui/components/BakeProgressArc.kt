package com.BreadIQ.myapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `UI/BakeProgressArc.swift`. The source hand-
 * rolls an SVG-style ring via `Circle().trim(from:to:)`; Compose's
 * `Canvas.drawArc` replaces that directly, no dash-offset arithmetic
 * needed either way.
 *
 * Default [color]/[trackColor] resolve through [LocalBreadIQColors]
 * (`accent`/`border`) rather than hardcoded hex literals, matching the
 * iOS port's own reasoning: the source's only real call site
 * (`BakeDetailScreen`) already overrides both with live values anyway,
 * so resolving the defaults through the token just keeps them consistent
 * with real usage instead of freezing them to one theme.
 */
@Composable
fun BakeProgressArc(
    progress: Double,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 14.dp,
    color: Color = LocalBreadIQColors.current.accent,
    trackColor: Color = LocalBreadIQColors.current.border,
) {
    val clamped = clampedProgress(progress).toFloat()

    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val inset = strokePx / 2
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx),
        )
        drawArc(
            brush = Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))),
            startAngle = -90f,
            sweepAngle = 360f * clamped,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}

/** `Math.max(0, Math.min(1, progress))` from the source. */
fun clampedProgress(progress: Double): Double = progress.coerceIn(0.0, 1.0)
