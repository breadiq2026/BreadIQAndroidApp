package com.BreadIQ.myapp.ui.bakedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.BakeDetailFormatting
import com.BreadIQ.myapp.core.HapticImpactStyle
import com.BreadIQ.myapp.core.HapticNotificationType
import com.BreadIQ.myapp.core.Haptics
import com.BreadIQ.myapp.core.TemperatureFormatting
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.BakeStatus
import com.BreadIQ.myapp.model.BakeStep
import com.BreadIQ.myapp.model.StepStatus
import com.BreadIQ.myapp.model.TemperatureUnit
import com.BreadIQ.myapp.ui.components.BakeProgressArc
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.BakeDetailViewModel
import com.BreadIQ.myapp.viewmodel.BakeDetailViewModelFactory
import kotlinx.coroutines.delay
import java.time.Instant

private val doneColor = Color(0xFF16A34A)
private val criticalColor = Color(0xFFEF4444)
private val warningColor = Color(0xFFD97706)

/**
 * Ported from the iOS app's `Screens/BakeDetailScreen.swift` — the live
 * active-bake tracker, the highest-risk screen in the app per the iOS
 * port's own roadmap note.
 *
 * Self-ticking every second (`LaunchedEffect` + `delay`, replacing
 * `TimelineView(.periodic(from:by: 1))`). `doneColor`/`criticalColor`/
 * `warningColor` are deliberately fixed, non-theme-adaptive literals —
 * matching the source's own `private static let` hex constants, kept
 * separate from `LocalBreadIQColors`'s adaptive tokens on purpose (this
 * screen's own source-verified choice, not an oversight).
 */
@Composable
fun BakeDetailScreen(
    sessionId: String,
    modifier: Modifier = Modifier,
    viewModel: BakeDetailViewModel = viewModel(factory = BakeDetailViewModelFactory(LocalContext.current, sessionId)),
    onDismiss: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    var now by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(sessionId) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }

    LaunchedEffect(state.dismissed) {
        if (state.dismissed) onDismiss()
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        val session = state.session
        when {
            session == null -> NotFoundView(onDismiss)
            session.status == BakeStatus.COMPLETED -> CompletionView(session, now, onDismiss)
            else -> ActiveView(session, now, viewModel, onDismiss)
        }
    }
}

@Composable
private fun NotFoundView(onDismiss: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = colors.border, modifier = Modifier.size(40.dp))
        Text("Bake not found", fontSize = 16.sp, color = colors.mutedForeground)
        Text("Go Back", color = colors.mutedForeground, modifier = Modifier.clickable(onClick = onDismiss))
    }
}

// MARK: - Completion

@Composable
private fun CompletionView(session: BakeSession, now: Instant, onDismiss: () -> Unit) {
    val colors = LocalBreadIQColors.current
    val elapsedMs = BakeDetailFormatting.elapsedMs(
        startedAt = session.startedAt, pausedDurationMs = session.pausedDurationMs, isComplete = true,
        lastStepActualEndAt = session.orderedSteps.lastOrNull()?.actualEndAt, isPaused = false, pausedAt = null, now = now,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = session.name, subtitle = null, onDismiss = onDismiss, onAbandonClick = null)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(top = 24.dp).size(96.dp).clip(CircleShape).background(doneColor.copy(alpha = 0.09f)),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = doneColor, modifier = Modifier.size(44.dp))
            }
            Text("Bake Complete", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            Text(session.name, fontSize = 14.sp, color = colors.mutedForeground)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.muted)
                    .padding(vertical = 14.dp),
            ) {
                CompletionStat(BakeDetailFormatting.formatElapsedShort(elapsedMs), "Total time", Modifier.weight(1f))
                Box(modifier = Modifier.width(0.5.dp).height(36.dp).background(colors.border))
                CompletionStat("${session.steps.size}", "Steps done", Modifier.weight(1f))
                if (session.ovenTempF > 0) {
                    Box(modifier = Modifier.width(0.5.dp).height(36.dp).background(colors.border))
                    CompletionStat(TemperatureFormatting.display(session.ovenTempF, TemperatureUnit.FAHRENHEIT), "Bake temp", Modifier.weight(1f))
                }
            }

            BreadIQButton(label = "Done", onClick = onDismiss, variant = BreadIQButtonVariant.PRIMARY, fullWidth = true)
        }
    }
}

@Composable
private fun CompletionStat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        Text(label, fontSize = 11.sp, color = colors.mutedForeground)
    }
}

// MARK: - Active / Paused

@Composable
private fun ActiveView(session: BakeSession, now: Instant, viewModel: BakeDetailViewModel, onDismiss: () -> Unit) {
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current
    var showFullTimeline by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }

    val state by viewModel.uiState.collectAsState()

    val isActive = session.status == BakeStatus.ACTIVE
    val isPaused = session.status == BakeStatus.PAUSED
    val currentStep = session.orderedSteps.getOrNull(session.currentStepIndex)
    val nextStep = session.orderedSteps.getOrNull(session.currentStepIndex + 1)

    val stepRemainingMs = BakeDetailFormatting.stepRemainingMs(currentStep?.scheduledEndAt, isActive, isPaused, session.pausedAt, now)
    val stepExpired = BakeDetailFormatting.isStepExpired(stepRemainingMs)
    val elapsedMs = BakeDetailFormatting.elapsedMs(session.startedAt, session.pausedDurationMs, false, null, isPaused, session.pausedAt, now)

    val isNoTimer = currentStep?.noTimer == true
    val isManualStart = currentStep?.manualStart == true
    val timerNotStarted = isManualStart && currentStep?.scheduledEndAt == null

    val arcProgress = BakeDetailFormatting.arcProgress(currentStep != null, stepExpired, stepRemainingMs, currentStep?.durationMinutes ?: 0)
    val arcColorState = BakeDetailFormatting.arcColorState(stepExpired, isActive, stepRemainingMs)
    val arcColor = colorFor(arcColorState)

    val advanceLabel = BakeDetailFormatting.advanceLabel(stepExpired, nextStep?.label)
    val displayDescription = BakeDetailFormatting.displayDescription(stepExpired, nextStep?.label, nextStep?.description, currentStep?.description)
    val displayStepName = BakeDetailFormatting.displayStepName(stepExpired, currentStep?.label)
    val stepNameColor = colorForName(BakeDetailFormatting.stepNameColorState(stepExpired, isActive))

    LaunchedEffect(now) {
        val tick = viewModel.handleTick(stepRemainingMs, isActive, stepExpired, currentStep?.id)
        tick.impact?.let { Haptics.impact(context, it) }
        if (tick.warning) Haptics.notification(context, HapticNotificationType.WARNING)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = session.name,
            subtitle = if (isPaused) "Paused" else if (stepExpired) "Ready for next step" else "In Progress",
            subtitleColor = if (isPaused) colors.mutedForeground else if (stepExpired) doneColor else colors.orange,
            onDismiss = onDismiss,
            onAbandonClick = { showAbandonConfirm = true },
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
                BakeProgressArc(progress = arcProgress, size = 210.dp, color = arcColor, trackColor = colors.border)
                ArcCenterOverlay(stepExpired, isPaused, stepRemainingMs, isNoTimer, timerNotStarted, currentStep)
            }
            Text("Step ${session.currentStepIndex + 1} of ${session.steps.size}", fontSize = 12.sp, color = colors.mutedForeground)
            Text(displayStepName, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = stepNameColor, textAlign = TextAlign.Center, maxLines = 2)

            if (displayDescription.isNotEmpty()) {
                Column(modifier = Modifier.heightIn(max = 130.dp).verticalScroll(rememberScrollState())) {
                    Text(displayDescription, fontSize = 13.sp, color = colors.mutedForeground, textAlign = TextAlign.Center)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                session.orderedSteps.forEachIndexed { i, step ->
                    val isCurrent = i == session.currentStepIndex
                    val isDone = step.status == StepStatus.COMPLETED
                    val dotSize = if (isCurrent) 10.dp else 6.dp
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(if (isDone) doneColor else if (isCurrent) colors.orange else colors.border),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(top = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFullTimeline = !showFullTimeline }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (showFullTimeline) 8.dp else 0.dp),
            ) {
                Text(
                    "JOURNEY · ${BakeDetailFormatting.formatElapsedShort(elapsedMs)} elapsed", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.mutedForeground, modifier = Modifier.weight(1f),
                )
                Icon(
                    if (showFullTimeline) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp),
                )
            }
            if (showFullTimeline) {
                Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    session.orderedSteps.forEachIndexed { i, step ->
                        TimelineRow(step, i, session, isActive, stepRemainingMs, arcColor)
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f))

        Footer(
            session = session, currentStep = currentStep, isActive = isActive, isPaused = isPaused,
            stepExpired = stepExpired, isNoTimer = isNoTimer, timerNotStarted = timerNotStarted,
            advanceLabel = advanceLabel, now = now, viewModel = viewModel, context = context,
        )
    }

    if (showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = { showAbandonConfirm = false },
            title = { Text("Abandon Bake") },
            text = { Text("Stop tracking this bake? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showAbandonConfirm = false
                    viewModel.abandon()
                    Haptics.impact(context, HapticImpactStyle.HEAVY)
                }) { Text("Abandon", color = colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonConfirm = false }) { Text("Keep Going") }
            },
        )
    }

    val earlyLabel = state.earlyCompletionLabel
    if (earlyLabel != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEarlyCompletionConfirm() },
            title = { Text("Complete Early?") },
            text = { Text("Your $earlyLabel has ${state.earlyCompletionRemainingText ?: ""} remaining. Complete early?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmEarlyCompletion(now)
                    Haptics.notification(context, HapticNotificationType.SUCCESS)
                }) { Text("Complete Early") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEarlyCompletionConfirm() }) { Text("Keep Going") }
            },
        )
    }
}

// MARK: - Top bar

@Composable
private fun TopBar(title: String, subtitle: String?, onDismiss: () -> Unit, onAbandonClick: (() -> Unit)?, subtitleColor: Color? = null) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 10.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).clickable(onClick = onDismiss)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.foreground, modifier = Modifier.size(20.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground, maxLines = 1)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = subtitleColor ?: colors.mutedForeground)
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                if (onAbandonClick != null) {
                    Icon(
                        Icons.Filled.Close, contentDescription = "Abandon", tint = colors.destructive,
                        modifier = Modifier.size(18.dp).clickable(onClick = onAbandonClick),
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
    }
}

// MARK: - Arc center overlay

@Composable
private fun ArcCenterOverlay(stepExpired: Boolean, isPaused: Boolean, stepRemainingMs: Double?, isNoTimer: Boolean, timerNotStarted: Boolean, currentStep: BakeStep?) {
    val colors = LocalBreadIQColors.current
    when {
        stepExpired -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = doneColor, modifier = Modifier.size(36.dp))
            Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = doneColor)
        }
        isPaused -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Pause, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(32.dp))
            Text("Paused", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground)
        }
        stepRemainingMs != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                BakeDetailFormatting.formatMs(stepRemainingMs), fontSize = 36.sp, fontWeight = FontWeight.Bold,
                color = if (stepRemainingMs <= 10_000) criticalColor else if (stepRemainingMs <= 30_000) warningColor else colors.foreground,
            )
            Text("remaining", fontSize = 11.sp, color = colors.mutedForeground)
        }
        isNoTimer -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.CheckBox, contentDescription = null, tint = colors.primary, modifier = Modifier.size(34.dp))
            Text("Ready", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                currentStep?.let { BakeDetailFormatting.formatMs(it.durationMinutes * 60_000.0) } ?: "—",
                fontSize = 36.sp, fontWeight = FontWeight.Bold, color = colors.foreground,
            )
            if (timerNotStarted) {
                Text("tap to start", fontSize = 11.sp, color = colors.mutedForeground)
            }
        }
    }
}

// MARK: - Timeline row

@Composable
private fun TimelineRow(step: BakeStep, index: Int, session: BakeSession, isActive: Boolean, stepRemainingMs: Double?, arcColor: Color) {
    val colors = LocalBreadIQColors.current
    val isCurrent = index == session.currentStepIndex
    val isDone = step.status == StepStatus.COMPLETED
    val isUpcoming = step.status == StepStatus.PENDING

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) colors.orangeLight else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
            when {
                isDone -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = doneColor, modifier = Modifier.size(15.dp))
                isCurrent -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(arcColor))
                else -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Transparent))
            }
        }
        Text(
            step.label, fontSize = 13.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isDone || !isCurrent) colors.mutedForeground else colors.foreground,
            maxLines = 1, modifier = Modifier.weight(1f).alpha(if (isUpcoming) 0.55f else 1f),
        )
        Text(
            BakeDetailFormatting.timelineRightText(isDone, isCurrent, isActive, stepRemainingMs, step.noTimer, step.durationMinutes),
            fontSize = 12.sp, fontWeight = if (isCurrent && isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent && isActive && stepRemainingMs != null) arcColor else colors.mutedForeground,
        )
    }
}

// MARK: - Footer

@Composable
private fun Footer(
    session: BakeSession, currentStep: BakeStep?, isActive: Boolean, isPaused: Boolean, stepExpired: Boolean,
    isNoTimer: Boolean, timerNotStarted: Boolean, advanceLabel: String, now: Instant,
    viewModel: BakeDetailViewModel, context: android.content.Context,
) {
    val colors = LocalBreadIQColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 12.dp)) {
            when {
                timerNotStarted -> BreadIQButton(
                    label = "Start Timer",
                    onClick = {
                        Haptics.impact(context, HapticImpactStyle.MEDIUM)
                        viewModel.startStepTimer()
                    },
                    variant = BreadIQButtonVariant.ORANGE, fullWidth = true,
                )
                isNoTimer -> BreadIQButton(
                    label = "Mark Complete",
                    onClick = { viewModel.handleAdvance(now) },
                    variant = BreadIQButtonVariant.ORANGE, fullWidth = true,
                )
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        BreadIQButton(
                            label = if (isPaused) "Resume" else "Pause",
                            onClick = { viewModel.togglePauseResume() },
                            variant = BreadIQButtonVariant.SECONDARY, modifier = Modifier.weight(1f),
                        )
                        if (isActive || stepExpired) {
                            BreadIQButton(
                                label = advanceLabel,
                                onClick = { viewModel.handleAdvance(now) },
                                variant = if (stepExpired) BreadIQButtonVariant.ORANGE else BreadIQButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (currentStep?.label == "Bulk Fermentation" && isActive && !stepExpired) {
                        Text(
                            "+ Needs More Time", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable {
                                    Haptics.impact(context, HapticImpactStyle.LIGHT)
                                    viewModel.extendStep(30)
                                },
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Color mapping

@Composable
private fun colorFor(state: BakeDetailFormatting.ArcColorState): Color {
    val colors = LocalBreadIQColors.current
    return when (state) {
        BakeDetailFormatting.ArcColorState.DONE -> doneColor
        BakeDetailFormatting.ArcColorState.TRACK -> colors.border
        BakeDetailFormatting.ArcColorState.CRITICAL -> criticalColor
        BakeDetailFormatting.ArcColorState.WARNING -> warningColor
        BakeDetailFormatting.ArcColorState.NORMAL -> colors.orange
    }
}

@Composable
private fun colorForName(state: BakeDetailFormatting.StepNameColorState): Color {
    val colors = LocalBreadIQColors.current
    return when (state) {
        BakeDetailFormatting.StepNameColorState.DONE -> doneColor
        BakeDetailFormatting.StepNameColorState.ACTIVE -> colors.foreground
        BakeDetailFormatting.StepNameColorState.MUTED -> colors.mutedForeground
    }
}
