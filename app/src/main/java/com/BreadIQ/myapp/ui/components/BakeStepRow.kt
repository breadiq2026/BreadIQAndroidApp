package com.BreadIQ.myapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.model.BakeStep
import com.BreadIQ.myapp.model.StepStatus
import com.BreadIQ.myapp.ui.theme.BreadIQCornerRadius
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Ported from the iOS app's `UI/BakeStepRow.swift`. **Unwired in the
 * source itself, and still unwired here** — grepping both codebases,
 * this component has no real call site anywhere (`BakeDetailScreen`
 * builds its own step-timeline row inline instead). Ported anyway,
 * matching the iOS port's own choice to build it faithfully despite
 * that — its `elapsed`/pause-correctness handling is real, verified
 * logic worth having on hand even without a current caller.
 *
 * Self-ticking every second (`LaunchedEffect` + `delay`) while active
 * and not paused, replacing the source's `TimelineView(.periodic(by: 1))`.
 */
@Composable
fun BakeStepRow(
    step: BakeStep,
    index: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    pausedAt: Instant? = null,
) {
    val colors = LocalBreadIQColors.current
    val (iconColor, icon) = when (step.status) {
        StepStatus.COMPLETED -> colors.success to Icons.Filled.CheckCircle
        StepStatus.ACTIVE -> colors.orange to Icons.Filled.Schedule
        StepStatus.PENDING, StepStatus.SKIPPED -> colors.border to Icons.Filled.Circle
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BreadIQCornerRadius.dp))
            .background(if (isActive) colors.orangeLight else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.width(28.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${index + 1}. ${step.label}", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (step.status == StepStatus.PENDING) colors.mutedForeground else colors.foreground,
            )
            if (step.description.isNotEmpty()) {
                Text(step.description, fontSize = 12.sp, color = colors.mutedForeground)
            }
        }

        TimeView(step, isActive, pausedAt)
    }
}

@Composable
private fun TimeView(step: BakeStep, isActive: Boolean, pausedAt: Instant?) {
    val colors = LocalBreadIQColors.current
    val scheduledEndAt = step.scheduledEndAt

    if (isActive && scheduledEndAt != null) {
        if (pausedAt != null) {
            Text(formatTime(Duration.between(pausedAt, scheduledEndAt)), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.orange, modifier = Modifier.width(44.dp))
        } else {
            var now by remember { mutableStateOf(Instant.now()) }
            LaunchedEffect(scheduledEndAt) {
                while (true) {
                    now = Instant.now()
                    delay(1000)
                }
            }
            Text(formatTime(Duration.between(now, scheduledEndAt)), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.orange, modifier = Modifier.width(44.dp))
        }
    } else {
        Text("${step.durationMinutes}m", fontSize = 13.sp, color = colors.mutedForeground, modifier = Modifier.width(44.dp))
    }
}

/** `Math.max(0, Math.ceil(ms / 1000))` from the source's `formatTime`. */
fun formatTime(remaining: Duration): String {
    val totalSeconds = max(0, Math.ceil(remaining.toMillis() / 1000.0).toInt())
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
