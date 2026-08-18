package com.BreadIQ.myapp.ui.calculator

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.BreadIQ.myapp.core.HapticImpactStyle
import com.BreadIQ.myapp.core.HapticNotificationType
import com.BreadIQ.myapp.core.Haptics
import com.BreadIQ.myapp.core.RawScheduledBakePlan
import com.BreadIQ.myapp.data.TemperatureUnitStore
import com.BreadIQ.myapp.model.calculatorCardTitles
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.CalculatorViewModel
import com.BreadIQ.myapp.viewmodel.CalculatorViewModelFactory
import com.BreadIQ.myapp.viewmodel.SubscriptionViewModelFactory
import kotlinx.coroutines.launch

/**
 * Ported from the iOS app's `Screens/CalculatorScreen.swift` — the
 * 5-card formula wizard's outer shell (header, card-content switch,
 * footer). Per-card content lives in `CalculatorCards.kt`/
 * `CalculatorResultsCard.kt` (next commits); this file owns navigation
 * chrome only, matching the source's own `body`/`header`/`footer` split.
 *
 * **Import and Settings are both real now.** `ImportScreen`
 * (`ImportModal`/`ImportModalFormatting`'s port) and the new
 * `SettingsScreen` (Settings + Connect-a-Browser session) are both real
 * Compose Navigation routes, wired below — the gear icon that used to sit
 * dimmed with nowhere to navigate now opens Settings for real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    modifier: Modifier = Modifier,
    // The default here isn't the shared Activity-scoped SubscriptionViewModel/
    // TemperatureUnitStore MainActivity.kt's BreadIQApp() threads in
    // explicitly — it's a fallback only, for a caller that doesn't pass
    // `viewModel:` itself (nothing in this app actually relies on it;
    // every real call site passes an explicit `viewModel`).
    viewModel: CalculatorViewModel = viewModel(
        factory = CalculatorViewModelFactory(
            LocalContext.current,
            viewModel(factory = SubscriptionViewModelFactory(LocalContext.current)),
            TemperatureUnitStore(LocalContext.current.getSharedPreferences("breadiq_prefs", android.content.Context.MODE_PRIVATE)),
        ),
    ),
    onOpenNutrition: () -> Unit = {},
    onOpenAutolyse: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onStartedBake: (String) -> Unit = {},
    onScheduleBake: (RawScheduledBakePlan) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current

    // Chrome-extension companion's pending-imports inbox — polled on
    // first composition and every time this screen returns to the
    // foreground, matching RootView's own `.task` + `scenePhase ==
    // .active` pair (see CalculatorViewModel.refreshPendingStagedImports's
    // own doc comment). No existing "on resume" idiom elsewhere in this
    // codebase to match; LifecycleEventEffect is the standard Compose
    // pattern for it.
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.refreshPendingStagedImports() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        coroutineScope.launch { viewModel.refreshPendingStagedImports() }
    }
    var showPendingImportsList by remember { mutableStateOf(false) }

    // Full-screen replacement while a staged import is under review — the
    // direct analog of the source's own `.fullScreenCover`, which fully
    // obscures CalculatorScreen underneath rather than sitting as a sheet/
    // overlay on top of it. An early return here reproduces that same
    // "nothing else in this composable renders" shape.
    val importReview = state.importReview
    if (importReview != null) {
        val (payload, mapping) = importReview
        ImportReviewScreen(
            payload = payload,
            mapping = mapping,
            onContinue = { outcome ->
                viewModel.applyImportReviewOutcome(outcome, payload)
                Haptics.notification(context, HapticNotificationType.SUCCESS)
            },
            onDiscard = viewModel::clearImportReview,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        CalculatorHeader(
            cardIndex = state.cardIndex,
            onCardIndexChange = viewModel::goToCard,
            onResetClick = { viewModel.update { it.copy(showResetConfirm = true) } },
            onImportClick = onOpenImport,
            onSettingsClick = onOpenSettings,
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
            ImportStatusBanner(
                fetching = state.importFetching,
                error = state.importError,
                pendingCount = state.pendingStagedImports.size,
                onPendingClick = { showPendingImportsList = true },
            )

            when (state.cardIndex) {
                0 -> CardStyleShapeBatch(state = state, viewModel = viewModel)
                1 -> CardFlourAndFormula(state = state, viewModel = viewModel)
                2 -> CardFermentation(state = state, viewModel = viewModel)
                3 -> CardEnvironment(state = state, viewModel = viewModel)
                else -> CardCalculateResults(
                    state = state, viewModel = viewModel, onOpenNutrition = onOpenNutrition, onOpenAutolyse = onOpenAutolyse,
                    onStartedBake = onStartedBake, onScheduleBake = onScheduleBake,
                )
            }
        }

        CalculatorFooter(
            cardIndex = state.cardIndex,
            loading = state.loading,
            onBackClick = viewModel::previousCard,
            onNextClick = viewModel::nextCard,
            onCalculateClick = {
                // Fire-and-forget timing, matching this file's existing
                // precedent elsewhere (e.g. handleSaveRecipe's own
                // success haptic) — fired the moment the action is
                // called, not gated on calculate()'s async auto-save
                // actually finishing. Read isImportSession BEFORE
                // calling calculate(), since calculate() clears it back
                // to false as part of the same call.
                val wasImportSession = state.isImportSession
                viewModel.calculate()
                Haptics.impact(context, HapticImpactStyle.LIGHT)
                if (wasImportSession) Haptics.notification(context, HapticNotificationType.SUCCESS)
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
            // "See Plans" now routes to the real SubscriptionScreen —
            // no SubscriptionScreen existed yet when this alert was
            // first wired (it showed a single dismiss button then, per
            // this function's own now-outdated doc comment history);
            // matches the source's real two-button alert exactly
            // ("Maybe later" / "See Plans" → present(.subscription)).
            dismissButton = {
                TextButton(onClick = { viewModel.update { it.copy(upgradePromptTitle = null, upgradePromptBody = null) } }) { Text("Maybe later") }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { it.copy(upgradePromptTitle = null, upgradePromptBody = null) }
                    onOpenSubscription()
                }) { Text("See Plans") }
            },
        )
    }

    if (showPendingImportsList) {
        val sheetState = rememberModalBottomSheetState()
        PendingImportsListScreen(
            items = state.pendingStagedImports,
            sheetState = sheetState,
            onDismiss = { showPendingImportsList = false },
            onSelect = { token ->
                viewModel.selectStagedImport(token)
                showPendingImportsList = false
            },
        )
    }

    // "Share Recipe" — launches the system share chooser for the .xlsx
    // CalculatorViewModel.shareRecipe() just wrote to cache, then clears
    // the one-shot state field back to null, same fire-once shape as the
    // upgrade-prompt/startedSessionId handoffs elsewhere in this file.
    state.shareFileUri?.let { uri ->
        LaunchedEffect(uri) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Recipe"))
            viewModel.clearShareFileUri()
        }
    }
}

@Composable
private fun CalculatorHeader(
    cardIndex: Int,
    onCardIndexChange: (Int) -> Unit,
    onResetClick: () -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEEF2FF))
                        .clickable(onClick = onImportClick)
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.muted.copy(alpha = 0.6f))
                        .clickable(onClick = onSettingsClick),
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

/**
 * `importStatusBanner` — the fetching/error states (a loading row while
 * `importFetching`, an amber warning row with `importError`'s message
 * when set), plus the Chrome-extension companion's pending-imports count
 * row. Sits at the top of the scrollable card content, matching the
 * source's own placement, right before the per-card switch. Tapping the
 * pending-count row opens `PendingImportsListScreen`
 * (`CalculatorScreen`'s own `showPendingImportsList` sheet), matching the
 * source's `importStatusBanner` exactly — same banner slot, same three
 * independent rows (fetching/error mutually exclusive with each other,
 * the pending-count row independent of both, matching the source's own
 * separate `if` blocks rather than one shared `else`).
 */
@Composable
private fun ImportStatusBanner(fetching: Boolean, error: String?, pendingCount: Int, onPendingClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    if (fetching) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Loading imported recipe…", fontSize = 13.sp, color = colors.mutedForeground)
        }
    } else if (error != null) {
        Text(
            "⚠ $error", fontSize = 12.sp, color = Color(0xFF92400E),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFFBEB))
                .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                .padding(10.dp),
        )
    }
    if (pendingCount > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.navyLight)
                .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .clickable(onClick = onPendingClick)
                .padding(10.dp),
        ) {
            Icon(Icons.Filled.Inbox, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
            Text(
                "$pendingCount recipe${if (pendingCount == 1) "" else "s"} waiting from your browser",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.primary, modifier = Modifier.size(13.dp))
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
