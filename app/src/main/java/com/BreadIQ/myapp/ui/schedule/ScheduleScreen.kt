package com.BreadIQ.myapp.ui.schedule

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.HapticImpactStyle
import com.BreadIQ.myapp.core.Haptics
import com.BreadIQ.myapp.core.RawScheduledBakePlan
import com.BreadIQ.myapp.core.ScheduleModalFormatting
import com.BreadIQ.myapp.core.ScheduledBakePlanner
import com.BreadIQ.myapp.model.ScheduledBake
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.ScheduleViewModel
import com.BreadIQ.myapp.viewmodel.ScheduleViewModelFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Ported from the iOS app's `Screens/ScheduleModal.swift` — the
 * scheduling flow Calculator's "Schedule Bake" button (and Current
 * Bake's "Reschedule" action) opens.
 *
 * **Picker UI, adapted for Android rather than transliterated.** The
 * source uses SwiftUI's single inline graphical `DatePicker` (date +
 * time in one always-visible widget). Compose Material3 has no single
 * combined-inline equivalent — `DatePicker`/`TimePicker` are two
 * separate composables — so this screen shows a compact summary card
 * that opens a dialog containing both when tapped, confirmed with one
 * "Done" tap. Same end result (pick a specific date + time within the
 * valid window), different platform-idiomatic interaction shape.
 *
 * **`minDate`/`maxDate` picker-range restriction is NOT enforced on the
 * picker widget itself** — matching the iOS source's own explicitly
 * flagged scope note (`ScheduledBakePlanner`'s doc comment: "NOT ported
 * here... worth flagging for that future session"). The picker lets the
 * user pick any date/time; [ScheduledBakePlanner.isTooSoon]/`isTooFar`
 * still validate live and gate the Schedule button exactly as before —
 * the authoritative gate was never the picker's own range in the first
 * place.
 *
 * **The post-schedule confirmation now matches the source's real
 * two-button shape** (`CalculatorScreen.swift`'s `scheduledConfirmation`
 * alert): "No Thanks" dismisses immediately; "Add to Calendar" calls
 * [com.BreadIQ.myapp.core.CalendarEventScheduler.addBakeEvent] via
 * [ScheduleViewModel.addToCalendar]. The `READ_CALENDAR`/`WRITE_CALENDAR`
 * runtime permission is requested here, lazily, the moment the user taps
 * "Add to Calendar" — not eagerly at app launch the way `POST_NOTIFICATIONS`/
 * `SCHEDULE_EXACT_ALARM` are — see [com.BreadIQ.myapp.core.CalendarEventScheduler]'s
 * own doc comment for why this call site can afford to be lazy where
 * that one couldn't.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    plan: RawScheduledBakePlan,
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModelFactory(LocalContext.current, plan)),
    onCancel: () -> Unit = {},
    onScheduled: (ScheduledBake) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current

    var targetDate by remember { mutableStateOf(viewModel.defaultTargetFinishTime) }
    var showPicker by remember { mutableStateOf(false) }

    val now = Instant.now()
    val startTimeValue = ScheduledBakePlanner.startTime(targetDate, viewModel.totalStepMinutes)
    val tooSoon = ScheduledBakePlanner.isTooSoon(targetDate, viewModel.totalStepMinutes, now)
    val tooFar = ScheduledBakePlanner.isTooFar(targetDate, now)
    val isValid = !tooSoon && !tooFar

    Column(modifier = modifier.fillMaxWidth().background(colors.background)) {
        Header(onCancel)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 8.dp),
        ) {
            Prompt()
            PickerCard(targetDate, onClick = { showPicker = true })
            SummaryBanner(isValid, tooSoon, startTimeValue, targetDate, plan.style)
            ResultCard(targetDate, startTimeValue, isValid, tooSoon, viewModel.totalStepMinutes)
            state.error?.let { ErrorBox(it) }
        }
        Footer(
            submitting = state.submitting, isValid = isValid, onCancel = onCancel,
            onSchedule = {
                Haptics.impact(context, HapticImpactStyle.MEDIUM)
                viewModel.schedule(targetDate)
            },
        )
    }

    if (showPicker) {
        DateTimePickerDialog(
            initial = targetDate,
            onConfirm = { targetDate = it; showPicker = false },
            onDismiss = { showPicker = false },
        )
    }

    // Remembers the just-scheduled bake across the confirmation dialog
    // closing (state.scheduled goes back to null on "No Thanks" AND on
    // addToCalendar resolving, either successfully or not — see
    // ScheduleViewModel.addToCalendar's own doc comment) so the single
    // navigate-away LaunchedEffect below still has something to pass,
    // regardless of which of the three exit paths fired. Every dismiss
    // handler below only clears ViewModel state, never calls
    // onScheduled(...) itself — the `else` branch's LaunchedEffect is
    // the ONE place that fires it, so all three paths converge on
    // exactly one navigation call instead of racing to double-call it.
    var confirmationBake by remember { mutableStateOf<ScheduledBake?>(null) }
    LaunchedEffect(state.scheduled) {
        state.scheduled?.let { confirmationBake = it }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.READ_CALENDAR] == true && results[Manifest.permission.WRITE_CALENDAR] == true
        confirmationBake?.let { bake ->
            if (granted) {
                viewModel.addToCalendar(bake)
            } else {
                // Denied — same end state as "No Thanks", no error dialog
                // (matches addBakeEvent's own AccessDenied message only
                // firing when addBakeEvent is actually attempted).
                viewModel.dismissScheduledConfirmation()
            }
        }
    }

    confirmationBake?.let { bake ->
        when {
            state.scheduled != null -> {
                AlertDialog(
                    onDismissRequest = { if (!state.addingToCalendar) viewModel.dismissScheduledConfirmation() },
                    title = { Text("Bake Scheduled") },
                    text = { Text(ScheduleModalFormatting.scheduledConfirmationMessage(bake.name, bake.startTime, bake.targetFinishTime)) },
                    dismissButton = {
                        TextButton(
                            enabled = !state.addingToCalendar,
                            onClick = { viewModel.dismissScheduledConfirmation() },
                        ) { Text("No Thanks") }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !state.addingToCalendar,
                            onClick = {
                                val hasAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                                if (hasAccess) {
                                    viewModel.addToCalendar(bake)
                                } else {
                                    calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                                }
                            },
                        ) { Text(if (state.addingToCalendar) "Adding…" else "Add to Calendar") }
                    },
                )
            }
            state.calendarError != null -> {
                val calendarError = state.calendarError.orEmpty()
                AlertDialog(
                    onDismissRequest = { viewModel.clearCalendarError() },
                    title = { Text("Couldn't Add to Calendar") },
                    text = { Text(calendarError) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearCalendarError() }) { Text("OK") }
                    },
                )
            }
            else -> {
                // Both scheduled and calendarError are null again — every
                // exit path above lands here exactly once; navigate away.
                LaunchedEffect(bake) { onScheduled(bake) }
            }
        }
    }
}

@Composable
private fun Header(onCancel: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(vertical = 16.dp)) {
            Text("Schedule Your Bake", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = colors.mutedForeground, modifier = Modifier.size(20.dp).clickable(onClick = onCancel))
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
    }
}

@Composable
private fun Prompt() {
    val colors = LocalBreadIQColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("When would you like your bread to be ready?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        Text("BreadIQ will calculate your start time from this finish window.", fontSize = 13.sp, color = colors.mutedForeground)
    }
}

@Composable
private fun PickerCard(targetDate: Instant, onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(ScheduleModalFormatting.formatDateTime(targetDate), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground, modifier = Modifier.weight(1f))
        Text("Change", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.primary)
    }
}

@Composable
private fun SummaryBanner(isValid: Boolean, tooSoon: Boolean, startTimeValue: Instant, targetDate: Instant, style: String) {
    val colors = LocalBreadIQColors.current
    if (isValid) {
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.foreground)) { append("Start your bake at ") }
            withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) { append(ScheduleModalFormatting.formatDateTime(startTimeValue)) }
            withStyle(SpanStyle(color = colors.foreground)) { append(" to have ") }
            withStyle(SpanStyle(color = colors.foreground, fontWeight = FontWeight.SemiBold)) { append(style.lowercase()) }
            withStyle(SpanStyle(color = colors.foreground)) { append(" ready by ") }
            withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) { append(ScheduleModalFormatting.formatDateTime(targetDate)) }
            withStyle(SpanStyle(color = colors.foreground)) { append(".") }
        }
        Text(
            text, fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.muted)
                .border(1.dp, colors.primary, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    } else if (tooSoon) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFFEF2F2))
                .border(1.dp, androidx.compose.ui.graphics.Color(0xFFFCA5A5), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFDC2626), modifier = Modifier.size(14.dp))
            Text(
                "There isn't enough time to start this bake for your target finish time. Choose a later finish time or start your bake now.",
                fontSize = 14.sp, color = androidx.compose.ui.graphics.Color(0xFFDC2626),
            )
        }
    }
}

@Composable
private fun ResultCard(targetDate: Instant, startTimeValue: Instant, isValid: Boolean, tooSoon: Boolean, totalStepMinutes: Int) {
    val colors = LocalBreadIQColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.card)
            .border(1.dp, if (isValid) colors.primary else colors.border, RoundedCornerShape(6.dp))
            .padding(14.dp),
    ) {
        ResultRow(Icons.Filled.CheckCircle, colors.muted, colors.primary, "Bread ready by") {
            Text(ScheduleModalFormatting.formatDateTime(targetDate), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
        ResultRow(
            Icons.Filled.PlayCircle,
            if (isValid) androidx.compose.ui.graphics.Color(0xFFFFF4EE) else colors.muted,
            if (isValid) androidx.compose.ui.graphics.Color(0xFFF97316) else colors.mutedForeground,
            "Start your bake at",
        ) {
            if (isValid) {
                Text(ScheduleModalFormatting.formatDateTime(startTimeValue), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            } else {
                Text(
                    if (tooSoon) "—" else "Max 7 days out", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = androidx.compose.ui.graphics.Color(0xFFDC2626),
                )
            }
        }
        if (isValid) {
            Text(
                "${ScheduleModalFormatting.formatDuration(totalStepMinutes)} active bake  ·  Includes ${ScheduledBakePlanner.coolingMinutes} min cooling time",
                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.muted)
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ResultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBackground: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    label: String,
    value: @Composable () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(iconBackground),
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(13.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 11.sp, color = colors.mutedForeground)
            value()
        }
    }
}

@Composable
private fun ErrorBox(message: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color(0xFFFEF2F2))
            .border(1.dp, androidx.compose.ui.graphics.Color(0xFFFCA5A5), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFDC2626), modifier = Modifier.size(14.dp))
        Text(message, fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFFDC2626))
    }
}

@Composable
private fun Footer(submitting: Boolean, isValid: Boolean, onCancel: () -> Unit, onSchedule: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 16.dp),
        ) {
            BreadIQButton(label = "Cancel", onClick = onCancel, variant = BreadIQButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
            BreadIQButton(
                label = if (submitting) "Scheduling…" else "Schedule Bake",
                onClick = onSchedule,
                variant = BreadIQButtonVariant.ORANGE,
                disabled = !isValid || submitting,
                loading = submitting,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(initial: Instant, onConfirm: (Instant) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val initialZdt = initial.atZone(zone)
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialZdt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(initialHour = initialZdt.hour, initialMinute = initialZdt.minute, is24Hour = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selectedMillis = dateState.selectedDateMillis ?: initial.toEpochMilli()
                val selectedDate = Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                val combined = selectedDate.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant()
                onConfirm(combined)
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = dateState, showModeToggle = false)
                TimePicker(state = timeState)
            }
        },
    )
}
