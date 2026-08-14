package com.BreadIQ.myapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.BakeStep
import com.BreadIQ.myapp.model.StepStatus
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * Ported from the iOS app's `UI/BakeCard.swift`. Takes `onTap`/
 * `onPauseResume`/`onAbandon` as plain callbacks rather than reaching
 * into a shared bake store — matches this port's own established
 * pattern (`CalculatorViewModel` calling `BakeSessionEngine` directly at
 * the screen level, no intermediate context/store object).
 *
 * The abandon confirmation dialog lives IN this component, matching the
 * source's own placement (not pushed up to the caller). The haptic buzz
 * the source fires on confirm is intentionally NOT fired here — the
 * caller's own `onAbandon` fires it exactly once, avoiding the double-
 * fire the source itself has (see `Haptics.kt`'s own doc comment).
 */
@Composable
fun BakeCard(
    session: BakeSession,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onPauseResume: () -> Unit = {},
    onAbandon: () -> Unit = {},
) {
    val colors = LocalBreadIQColors.current
    var showAbandonConfirmation by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(session.id) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }

    val currentStep: BakeStep? = session.orderedSteps.getOrNull(session.currentStepIndex)
    val completedCount = session.steps.count { it.status == StepStatus.COMPLETED }
    val progress = if (session.steps.isEmpty()) 0.0 else completedCount.toDouble() / session.steps.size

    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onTap)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(session.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground, maxLines = 1)
                    Text(session.style.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = colors.mutedForeground)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(colors.muted).clickable(onClick = onPauseResume),
                    ) {
                        Icon(
                            if (session.status == BakeStatus.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp),
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(colors.muted).clickable { showAbandonConfirmation = true },
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Abandon", tint = colors.destructive, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.muted)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.primary),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                StatusIndicator(session = session, currentStep = currentStep)
                Box(modifier = Modifier.weight(1f))
                Text(
                    "$completedCount/${session.steps.size} steps · ${formatElapsed(session.startedAt, session.pausedDurationMs, now)}",
                    fontSize = 12.sp, color = colors.mutedForeground,
                )
            }
        }
    }

    if (showAbandonConfirmation) {
        AlertDialog(
            onDismissRequest = { showAbandonConfirmation = false },
            title = { Text("Stop tracking \"${session.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showAbandonConfirmation = false
                    onAbandon()
                }) { Text("Abandon", color = colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatusIndicator(session: BakeSession, currentStep: BakeStep?) {
    val colors = LocalBreadIQColors.current
    when {
        currentStep != null && session.status == BakeStatus.ACTIVE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.orange, modifier = Modifier.size(12.dp))
            Text(currentStep.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.orange)
        }
        session.status == BakeStatus.PAUSED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.PauseCircle, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(12.dp))
            Text("Paused", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground)
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(12.dp))
            Text("Complete", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.success)
        }
    }
}

/**
 * Ported as-is from the source's `formatElapsed`, including a real
 * quirk: unlike `BakeDetailScreen`'s own separate elapsed calc (which
 * freezes at `pausedAt` while paused), this one always measures against
 * live `now` and only ever subtracts PAST pause time via
 * `pausedDurationMs` — so elapsed keeps climbing while a session is
 * currently paused. Kept faithfully; not "fixed" without being asked.
 */
fun formatElapsed(startedAt: Instant, pausedDurationMs: Double, now: Instant = Instant.now()): String {
    val elapsedMs = Duration.between(startedAt, now).toMillis() - pausedDurationMs
    val totalMinutes = (elapsedMs / 60_000).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
