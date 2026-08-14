package com.BreadIQ.myapp.ui.currentbake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.HapticImpactStyle
import com.BreadIQ.myapp.core.HapticNotificationType
import com.BreadIQ.myapp.core.Haptics
import com.BreadIQ.myapp.core.RawScheduledBakePlan
import com.BreadIQ.myapp.core.ScheduledBakePlanner
import com.BreadIQ.myapp.model.BakeSession
import com.BreadIQ.myapp.model.ScheduledBake
import com.BreadIQ.myapp.ui.components.BakeCard
import com.BreadIQ.myapp.ui.components.ScheduledBakeCard
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.CurrentBakeViewModel
import com.BreadIQ.myapp.viewmodel.CurrentBakeViewModelFactory
import com.BreadIQ.myapp.viewmodel.active
import com.BreadIQ.myapp.viewmodel.combinedCount
import com.BreadIQ.myapp.viewmodel.completed
import com.BreadIQ.myapp.viewmodel.isEmptyState
import com.BreadIQ.myapp.viewmodel.tierLabel

/**
 * Ported from the iOS app's `Screens/CurrentBakeScreen.swift`.
 *
 * [onOpenBakeDetail] restores the source's `router.push('/bake/${id}')`
 * (`BakeCard`'s own `onTap`, first wired for real here — it defaulted to
 * a no-op when that component was built). [onReschedule] hands the
 * caller (`MainActivity`) a freshly-rebuilt [RawScheduledBakePlan] for
 * the Schedule screen, matching this port's pending-value nav-handoff
 * pattern (`pendingRecipeId`/`pendingSchedulePlan`) rather than a
 * SwiftUI-style `.sheet(item:)` binding.
 */
@Composable
fun CurrentBakeScreen(
    modifier: Modifier = Modifier,
    viewModel: CurrentBakeViewModel = viewModel(factory = CurrentBakeViewModelFactory(LocalContext.current)),
    onOpenBakeDetail: (sessionId: String) -> Unit = {},
    onReschedule: (RawScheduledBakePlan) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current
    var bakePendingRemoval by remember { mutableStateOf<ScheduledBake?>(null) }

    LaunchedEffect(state.startedSessionId) {
        val id = state.startedSessionId ?: return@LaunchedEffect
        Haptics.notification(context, HapticNotificationType.SUCCESS)
        onOpenBakeDetail(id)
        viewModel.clearStartedSessionId()
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        CurrentBakeHeader(tierLabel = state.tierLabel, combinedCount = state.combinedCount, hasActive = state.active.isNotEmpty(), hasScheduled = state.scheduledBakes.isNotEmpty())

        if (state.isEmptyState) {
            CurrentBakeEmptyState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.scheduledBakes.isNotEmpty()) {
                    item(key = "scheduled-header") { SectionHeader("Scheduled") }
                    items(state.scheduledBakes, key = { "sched-${it.id}" }) { bake ->
                        ScheduledBakeCard(
                            bake = bake,
                            onStartNow = { viewModel.startScheduled(bake) },
                            onRemove = { bakePendingRemoval = bake },
                            onReschedule = { onReschedule(ScheduledBakePlanner.reschedulePlan(bake)) },
                            starting = state.startingId == bake.id,
                        )
                    }
                }
                if (state.active.isNotEmpty()) {
                    item(key = "active-header") { SectionHeader("In Progress") }
                    items(state.active, key = { "active-${it.id}" }) { session ->
                        SessionCard(session, viewModel, onOpenBakeDetail, context)
                    }
                }
                if (state.completed.isNotEmpty()) {
                    item(key = "completed-header") { SectionHeader("Completed") }
                    items(state.completed, key = { "done-${it.id}" }) { session ->
                        SessionCard(session, viewModel, onOpenBakeDetail, context)
                    }
                }
                item(key = "bottom-padding") { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    bakePendingRemoval?.let { bake ->
        AlertDialog(
            onDismissRequest = { bakePendingRemoval = null },
            title = { Text("Cancel \"${bake.name}\"?") },
            text = { Text("Push notifications will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeScheduled(bake)
                    bakePendingRemoval = null
                }) { Text("Cancel Bake", color = colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { bakePendingRemoval = null }) { Text("Keep It") }
            },
        )
    }

    state.startError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearStartError() },
            title = { Text("Can't Start Bake") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStartError() }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun SessionCard(
    session: BakeSession,
    viewModel: CurrentBakeViewModel,
    onOpenBakeDetail: (String) -> Unit,
    context: android.content.Context,
) {
    BakeCard(
        session = session,
        onTap = { onOpenBakeDetail(session.id) },
        onPauseResume = { viewModel.togglePause(session) },
        onAbandon = {
            viewModel.abandon(session)
            Haptics.impact(context, HapticImpactStyle.HEAVY)
        },
    )
}

@Composable
private fun CurrentBakeHeader(tierLabel: String?, combinedCount: Int, hasActive: Boolean, hasScheduled: Boolean) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Current Bake", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            if (tierLabel != null && combinedCount > 0) {
                Text(tierLabel, fontSize = 12.sp, color = colors.mutedForeground)
            }
        }
        if (hasActive || hasScheduled) {
            Text(
                "$combinedCount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (!hasActive) colors.primary else colors.orange)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = LocalBreadIQColors.current
    Text(
        title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = colors.mutedForeground,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun CurrentBakeEmptyState() {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = 100.dp),
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.border, modifier = Modifier.size(52.dp))
        Text("No Active Bakes", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.mutedForeground)) { append("Calculate a formula and tap ") }
            withStyle(SpanStyle(color = colors.foreground, fontWeight = FontWeight.SemiBold)) { append("Start Bake Timer") }
            withStyle(SpanStyle(color = colors.mutedForeground)) { append(" to track your bake step by step, or ") }
            withStyle(SpanStyle(color = colors.foreground, fontWeight = FontWeight.SemiBold)) { append("Schedule Bake") }
            withStyle(SpanStyle(color = colors.mutedForeground)) { append(" to plan ahead.") }
        }
        Text(text, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
