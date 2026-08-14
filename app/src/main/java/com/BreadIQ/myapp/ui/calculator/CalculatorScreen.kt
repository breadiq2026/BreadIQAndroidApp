package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.HapticImpactStyle
import com.BreadIQ.myapp.core.HapticNotificationType
import com.BreadIQ.myapp.core.Haptics
import com.BreadIQ.myapp.model.calculatorCardTitles
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.CalculatorViewModel
import com.BreadIQ.myapp.viewmodel.CalculatorViewModelFactory

/**
 * Ported from the iOS app's `Screens/CalculatorScreen.swift` — the
 * 5-card formula wizard's outer shell (header, card-content switch,
 * footer). Per-card content lives in `CalculatorCards.kt`/
 * `CalculatorResultsCard.kt` (next commits); this file owns navigation
 * chrome only, matching the source's own `body`/`header`/`footer` split.
 *
 * **Import is rendered as a visibly disabled affordance, not omitted** —
 * per direct instruction: `ImportModal`/`RecipeScanner` (CameraX + ML
 * Kit) are a deferred platform-integration item. Settings is likewise
 * disabled here — no `SettingsScreen` exists on this port yet (a
 * separate, unstarted sequence item), so the gear button would have
 * nowhere real to navigate; shown dimmed rather than wired to a no-op,
 * so it doesn't read as a working control that silently does nothing.
 */
@Composable
fun CalculatorScreen(
    modifier: Modifier = Modifier,
    viewModel: CalculatorViewModel = viewModel(factory = CalculatorViewModelFactory(LocalContext.current)),
    onOpenNutrition: () -> Unit = {},
    onOpenAutolyse: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        CalculatorHeader(
            cardIndex = state.cardIndex,
            onCardIndexChange = viewModel::goToCard,
            onResetClick = { viewModel.update { it.copy(showResetConfirm = true) } },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.cardIndex) {
                0 -> CardStyleShapeBatch(state = state, viewModel = viewModel)
                1 -> CardFlourAndFormula(state = state, viewModel = viewModel)
                2 -> CardFermentation(state = state, viewModel = viewModel)
                3 -> CardEnvironment(state = state, viewModel = viewModel)
                else -> CardCalculateResults(state = state, viewModel = viewModel, onOpenNutrition = onOpenNutrition, onOpenAutolyse = onOpenAutolyse)
            }
        }

        CalculatorFooter(
            cardIndex = state.cardIndex,
            loading = state.loading,
            onBackClick = viewModel::previousCard,
            onNextClick = viewModel::nextCard,
            onCalculateClick = {
                viewModel.calculate()
                Haptics.impact(context, HapticImpactStyle.LIGHT)
            },
        )
    }

    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.update { it.copy(showResetConfirm = false) } },
            title = { Text("Start Over?") },
            text = { Text("This clears every field back to default across all cards. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { it.copy(showResetConfirm = false) }
                    viewModel.resetToDefaults()
                }) { Text("Start Over", color = colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.update { it.copy(showResetConfirm = false) } }) { Text("Cancel") }
            },
        )
    }

    state.queueError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.update { it.copy(queueError = null) } },
            title = { Text("Can't Queue Bake") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.update { it.copy(queueError = null) } }) { Text("OK") }
            },
        )
    }

    if (state.queueSuccessShown) {
        AlertDialog(
            onDismissRequest = { viewModel.update { it.copy(queueSuccessShown = false) } },
            title = { Text("Added to Queue") },
            text = { Text("Your bake is waiting in the Queue tab. One tap starts the timer whenever you're ready.") },
            confirmButton = {
                TextButton(onClick = { viewModel.update { it.copy(queueSuccessShown = false) } }) { Text("OK") }
            },
        )
    }

    val upgradeTitle = state.upgradePromptTitle
    val upgradeBody = state.upgradePromptBody
    if (upgradeTitle != null) {
        AlertDialog(
            onDismissRequest = { viewModel.update { it.copy(upgradePromptTitle = null, upgradePromptBody = null) } },
            title = { Text(upgradeTitle) },
            text = { Text(upgradeBody ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.update { it.copy(upgradePromptTitle = null, upgradePromptBody = null) } }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun CalculatorHeader(
    cardIndex: Int,
    onCardIndexChange: (Int) -> Unit,
    onResetClick: () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row {
                Text("Bread", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                Text("IQ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Deferred: needs RecipeScanner (CameraX + ML Kit) / ImportModal — see this file's own doc comment.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .alpha(0.5f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEEF2FF))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = Color(0xFF1B3A8C), modifier = Modifier.size(12.dp))
                    Text("Import", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B3A8C))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.muted.copy(alpha = 0.6f))
                        .clickable(onClick = onResetClick),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Start Over", tint = colors.mutedForeground, modifier = Modifier.size(14.dp))
                }
                // Deferred: no SettingsScreen port exists yet — see this file's own doc comment.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .alpha(0.5f)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.muted.copy(alpha = 0.6f)),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = colors.mutedForeground, modifier = Modifier.size(14.dp))
                }
            }
        }

        Text(calculatorCardTitles[cardIndex], fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            calculatorCardTitles.indices.forEach { i ->
                val active = i == cardIndex
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (active) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.primary else colors.border)
                        .clickable { onCardIndexChange(i) },
                )
            }
        }
    }
}

@Composable
private fun CalculatorFooter(
    cardIndex: Int,
    loading: Boolean,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
    onCalculateClick: () -> Unit,
) {
    val colors = LocalBreadIQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .alpha(if (cardIndex > 0) 1f else 0.35f)
                .clickable(enabled = cardIndex > 0, onClick = onBackClick),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Text("Back", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }

        Spacer(modifier = Modifier.weight(1f))

        if (cardIndex < calculatorCardTitles.size - 1) {
            BreadIQButton(label = "Next", onClick = onNextClick, variant = BreadIQButtonVariant.PRIMARY)
        } else {
            BreadIQButton(
                label = if (loading) "Calculating…" else "Calculate",
                onClick = onCalculateClick,
                variant = BreadIQButtonVariant.PRIMARY,
                loading = loading,
            )
        }
    }
}
