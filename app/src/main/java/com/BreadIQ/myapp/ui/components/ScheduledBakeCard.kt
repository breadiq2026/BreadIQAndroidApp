package com.BreadIQ.myapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ErrorOutline
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
import com.BreadIQ.myapp.core.ScheduledBakePlanner
import com.BreadIQ.myapp.model.ScheduledBake
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Ported from the iOS app's `UI/ScheduledBakeCard.swift`. `onStartNow`/
 * `onRemove`/`onReschedule` are all REQUESTS, not the actions
 * themselves — the caller (Current Bake screen) owns the confirmation
 * dialog and the actual Room delete/insert, since this card's own row
 * is driven by an observed query on the exact object those actions would
 * mutate (deleting the backing row out from under this card's own
 * confirmation dialog is a real crash class in the SwiftUI source this
 * port avoids by construction, not just by convention).
 *
 * Self-ticking every 30 seconds (`LaunchedEffect` + `delay`, replacing
 * the source's `TimelineView(.periodic(by: 30))`) — nearly every visual
 * element here (icon badge, time-block coloring, the meta-row tag, even
 * the Start Now button's variant) derives from live `now`, so the whole
 * card ticks together rather than threading `now` through piecemeal.
 */
@Composable
fun ScheduledBakeCard(
    bake: ScheduledBake,
    onStartNow: () -> Unit,
    onRemove: () -> Unit,
    onReschedule: () -> Unit,
    starting: Boolean,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(bake.id) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }

    val colors = LocalBreadIQColors.current
    val isPast = isPast(bake.startTime, now)
    val is30MinWarning = is30MinWarning(bake.startTime, now)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Header(bake, isPast, is30MinWarning)
            Box(modifier = Modifier.height(12.dp))
            TimeBlock(bake, isPast, is30MinWarning, now)
            Box(modifier = Modifier.height(10.dp))
            MetaRow(bake, isPast, now)
            Box(modifier = Modifier.height(12.dp))
            Actions(isPast, starting, onStartNow, onRemove, onReschedule)
        }
    }
}

fun isPast(startTime: Instant, now: Instant = Instant.now()): Boolean = !now.isBefore(startTime)

fun is30MinWarning(startTime: Instant, now: Instant = Instant.now()): Boolean =
    !isPast(startTime, now) && Duration.between(now, startTime).toMinutes() <= 30

private val dateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val weekdayMonthDayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

fun formatDateTime(instant: Instant, now: Instant = Instant.now()): String {
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val nowDate = now.atZone(zone).toLocalDate()
    val timeStr = dateTimeFormatter.format(instant.atZone(zone))
    return when (date) {
        nowDate -> "Today at $timeStr"
        nowDate.plusDays(1) -> "Tomorrow at $timeStr"
        else -> "${weekdayMonthDayFormatter.format(instant.atZone(zone))} at $timeStr"
    }
}

fun timeUntil(target: Instant, now: Instant = Instant.now()): String {
    val diffMinutes = Duration.between(now, target).toMinutes()
    if (diffMinutes <= 0) return "Now"
    if (diffMinutes < 60) return "${diffMinutes}m"
    val h = diffMinutes / 60
    val m = diffMinutes % 60
    return if (m > 0) "${h}h ${m}m" else "${h}h"
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m > 0) "${h}h ${m}m" else "${h}h"
}

@Composable
private fun Header(bake: ScheduledBake, isPast: Boolean, is30MinWarning: Boolean) {
    val colors = LocalBreadIQColors.current
    val (iconColor, iconBackground) = when {
        isPast -> colors.orange to colors.orangeLight
        is30MinWarning -> colors.warning to colors.warningBackground
        else -> colors.primary to colors.muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconBackground),
        ) {
            Icon(
                if (isPast) Icons.Filled.ErrorOutline else Icons.Filled.CalendarMonth,
                contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp),
            )
        }
        Column {
            Text(bake.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.foreground, maxLines = 1)
            Text(bake.style, fontSize = 12.sp, color = colors.mutedForeground)
        }
    }
}

@Composable
private fun TimeBlock(bake: ScheduledBake, isPast: Boolean, is30MinWarning: Boolean, now: Instant) {
    val colors = LocalBreadIQColors.current
    val startColor = when {
        isPast -> colors.orange
        is30MinWarning -> colors.warning
        else -> colors.mutedForeground
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(0.5.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("READY BY", fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, color = colors.mutedForeground, modifier = Modifier.weight(1f))
            Text(formatDateTime(bake.targetFinishTime, now), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Start bake", fontSize = 11.sp, color = colors.mutedForeground, modifier = Modifier.weight(1f))
            Text(
                formatDateTime(bake.startTime, now), fontSize = 13.sp,
                fontWeight = if (isPast || is30MinWarning) FontWeight.Bold else FontWeight.Normal,
                color = startColor,
            )
        }
    }
}

@Composable
private fun MetaRow(bake: ScheduledBake, isPast: Boolean, now: Instant) {
    val colors = LocalBreadIQColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.muted)
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(10.dp))
            Text(
                "${formatDuration(bake.totalStepMinutes)} + ${formatDuration(ScheduledBakePlanner.coolingMinutes)} cooling",
                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground,
            )
        }
        if (isPast) {
            Text(
                "Ready to start", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = androidx.compose.ui.graphics.Color(0xFFC2410C),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.orangeLight)
                    .border(1.dp, colors.orange, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        } else {
            Text(
                "Starts in ${timeUntil(bake.startTime, now)}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.muted)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun Actions(isPast: Boolean, starting: Boolean, onStartNow: () -> Unit, onRemove: () -> Unit, onReschedule: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, colors.border, RoundedCornerShape(8.dp)).clickable(onClick = onRemove),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = colors.mutedForeground, modifier = Modifier.size(15.dp))
        }
        // Only surfaced once overdue — a future-dated bake already has a
        // clear single next step (wait, or start early); once the
        // original start time has passed, "keep waiting" stops being a
        // real option and the user needs a way to pick a new time.
        if (isPast) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, colors.border, RoundedCornerShape(8.dp)).clickable(onClick = onReschedule),
            ) {
                Icon(Icons.Filled.EditCalendar, contentDescription = "Reschedule", tint = colors.mutedForeground, modifier = Modifier.size(15.dp))
            }
        }
        BreadIQButton(
            label = if (starting) "Starting…" else "Start Now",
            onClick = onStartNow,
            variant = if (isPast) BreadIQButtonVariant.ORANGE else BreadIQButtonVariant.SECONDARY,
            loading = starting,
            fullWidth = true,
            modifier = Modifier.weight(1f),
        )
    }
}
