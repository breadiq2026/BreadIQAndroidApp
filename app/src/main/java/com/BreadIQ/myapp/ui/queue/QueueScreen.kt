package com.BreadIQ.myapp.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.HapticNotificationType
import com.BreadIQ.myapp.core.Haptics
import com.BreadIQ.myapp.core.QueueFormatting
import com.BreadIQ.myapp.core.TemperatureFormatting
import com.BreadIQ.myapp.model.BakeUserTier
import com.BreadIQ.myapp.model.QueuedBake
import com.BreadIQ.myapp.model.TemperatureUnit
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.QueueViewModel
import com.BreadIQ.myapp.viewmodel.QueueViewModelFactory
import com.BreadIQ.myapp.viewmodel.activeBakeCount
import com.BreadIQ.myapp.viewmodel.maxQueuedBakes

/**
 * Ported from the iOS app's `Screens/QueueScreen.swift`.
 *
 * [onStartedBake] fires once a queued bake successfully becomes a live
 * [com.BreadIQ.myapp.model.BakeSession] — the caller (`MainActivity`)
 * navigates to Bake Detail with the new session id.
 */
@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = viewModel(factory = QueueViewModelFactory(LocalContext.current)),
    onStartedBake: (sessionId: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current
    var bakePendingRemoval by remember { mutableStateOf<QueuedBake?>(null) }

    LaunchedEffect(state.startedSessionId) {
        val id = state.startedSessionId ?: return@LaunchedEffect
        Haptics.notification(context, HapticNotificationType.SUCCESS)
        onStartedBake(id)
        viewModel.clearStartedSessionId()
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        QueueHeader(count = state.queue.size, maxQueuedBakes = state.maxQueuedBakes)

        if (state.queue.isEmpty() && state.userTier != BakeUserTier.FREE) {
            QueueEmptyState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.userTier == BakeUserTier.FREE) {
                    item(key = "gate") { GateCard() }
                }
                if (state.activeBakeCount > 0) {
                    item(key = "banner") { ContextBanner(state.activeBakeCount) }
                }
                items(state.queue, key = { it.id }) { bake ->
                    QueueCard(
                        bake = bake,
                        starting = state.startingId == bake.id,
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        onStartNow = { viewModel.startNow(bake) },
                        onRemove = { bakePendingRemoval = bake },
                    )
                }
                item(key = "bottom-padding") { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    bakePendingRemoval?.let { bake ->
        AlertDialog(
            onDismissRequest = { bakePendingRemoval = null },
            title = { Text("Remove \"${bake.name}\" from your queue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(bake)
                    bakePendingRemoval = null
                }) { Text("Remove", color = colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { bakePendingRemoval = null }) { Text("Cancel") }
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
private fun QueueHeader(count: Int, maxQueuedBakes: Int) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Bake Queue", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            if (count > 0) {
                Text("$count of $maxQueuedBakes slots used", fontSize = 12.sp, color = colors.mutedForeground)
            }
        }
        if (count > 0) {
            Text(
                "$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.primary)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun GateCard() {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Queue requires Basic or Premium", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
                Text("Plan your bakes in advance. Configure tonight, bake in the morning.", fontSize = 12.sp, color = colors.mutedForeground)
            }
        }
    }
}

@Composable
private fun ContextBanner(activeBakeCount: Int) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.orangeLight)
            .border(1.dp, colors.orange, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = colors.orange, modifier = Modifier.size(13.dp))
        Text(
            "$activeBakeCount bake${if (activeBakeCount > 1) "s" else ""} currently running",
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFC2410C),
        )
    }
}

@Composable
private fun QueueEmptyState() {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 80.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp).clip(CircleShape).background(colors.muted)) {
            Icon(Icons.Filled.Inbox, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(32.dp))
        }
        Text("No bakes queued", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.mutedForeground)) { append("After you calculate a formula, tap ") }
            withStyle(SpanStyle(color = colors.mutedForeground, fontWeight = FontWeight.SemiBold)) { append("Queue for Later") }
            withStyle(SpanStyle(color = colors.mutedForeground)) { append(" to stage it here. One tap starts the timer whenever you're ready.") }
        }
        Text(text, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueCard(
    bake: QueuedBake,
    starting: Boolean,
    temperatureUnit: TemperatureUnit,
    onStartNow: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    val totalMinutes = bake.steps.sumOf { it.durationMinutes }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(bake.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.foreground, maxLines = 1, modifier = Modifier.weight(1f))
                Text(QueueFormatting.timeAgo(bake.createdAt), fontSize = 11.sp, color = colors.mutedForeground)
            }
            Text("${bake.style} · ${bake.config.shapeName}", fontSize = 12.sp, color = colors.mutedForeground, modifier = Modifier.padding(bottom = 4.dp))
            Text(
                QueueFormatting.capitalizeWords(QueueFormatting.formatFlourBlend(bake.config.flourBlend)),
                fontSize = 11.sp, color = colors.mutedForeground, modifier = Modifier.padding(bottom = 8.dp),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                queueCardTags(bake).forEach { tag ->
                    Text(
                        tag, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.muted)
                            .border(1.dp, colors.border, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (bake.ovenTempF > 0) {
                    Text(
                        "${TemperatureFormatting.display(bake.ovenTempF, temperatureUnit)} oven", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = Color(0xFFC2410C),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFFFF4EE))
                            .border(1.dp, Color(0xFFF97316), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Box(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)) {
                StatBlock(QueueFormatting.formatTotalTime(totalMinutes), "total time", Modifier.weight(1f))
                Box(modifier = Modifier.width(0.5.dp).height(30.dp).background(colors.border))
                StatBlock("${bake.steps.size}", "steps", Modifier.weight(1f))
                Box(modifier = Modifier.width(0.5.dp).height(30.dp).background(colors.border))
                StatBlock(bake.steps.firstOrNull()?.label ?: "—", "starts with", Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, colors.border, RoundedCornerShape(8.dp)).clickable(onClick = onRemove),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = colors.mutedForeground, modifier = Modifier.size(15.dp))
                }
                BreadIQButton(
                    label = if (starting) "Starting…" else "Start Now",
                    onClick = onStartNow,
                    variant = BreadIQButtonVariant.ORANGE,
                    loading = starting,
                    fullWidth = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** `tags` — port of `QueueCard`'s own computed property. */
private fun queueCardTags(bake: QueuedBake): List<String> {
    val config = bake.config
    val result = mutableListOf(
        "${config.numLoaves} ${if (config.numLoaves == 1) "loaf" else "loaves"}",
        "${formatNumber(config.hydration)}% H",
    )
    if (config.isSpeedRun) result.add("SpeedRun™")
    if (config.usePrefermant) result.add(config.prefermentType)
    val sweetenerType = config.sweetenerType
    if (!sweetenerType.isNullOrEmpty()) result.add(QueueFormatting.replaceFirstUnderscore(sweetenerType))
    return result.map { QueueFormatting.capitalizeWords(it) }
}

/** Trims a trailing `.0`/`.00…`, matching JS's default `Number` → `String` coercion — kept local to this screen the same way every other screen keeps its own copy. */
private fun formatNumber(n: Double): String {
    if (n == n.toLong().toDouble()) return n.toLong().toString()
    var s = String.format("%.4f", n)
    while (s.endsWith("0")) s = s.dropLast(1)
    if (s.endsWith(".")) s = s.dropLast(1)
    return s
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.foreground, maxLines = 1)
        Text(label, fontSize = 10.sp, color = colors.mutedForeground)
    }
}
