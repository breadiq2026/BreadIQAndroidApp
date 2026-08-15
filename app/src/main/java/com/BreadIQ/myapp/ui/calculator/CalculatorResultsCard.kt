package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.core.AutolyseTier
import com.BreadIQ.myapp.core.CalculatorFormatting
import com.BreadIQ.myapp.core.CostEstimator
import com.BreadIQ.myapp.core.FormulaCalculator
import com.BreadIQ.myapp.core.HapticImpactStyle
import com.BreadIQ.myapp.core.HapticNotificationType
import com.BreadIQ.myapp.core.Haptics
import com.BreadIQ.myapp.core.RawScheduledBakePlan
import com.BreadIQ.myapp.core.TemperatureFormatting
import com.BreadIQ.myapp.core.swiftRounded
import com.BreadIQ.myapp.model.BreadStyleDef
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.model.ProofTimeResult
import com.BreadIQ.myapp.model.TemperatureUnit
import com.BreadIQ.myapp.ui.components.BreadIQButton
import com.BreadIQ.myapp.ui.components.BreadIQButtonVariant
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.BreadIQCornerRadius
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.CalculatorUiState
import com.BreadIQ.myapp.viewmodel.CalculatorViewModel
import com.BreadIQ.myapp.viewmodel.autolyseGuidance
import com.BreadIQ.myapp.viewmodel.isBasicOrPremium
import com.BreadIQ.myapp.viewmodel.isPremium
import com.BreadIQ.myapp.viewmodel.isPretzel
import com.BreadIQ.myapp.viewmodel.prefInfo
import com.BreadIQ.myapp.viewmodel.selectedShape
import com.BreadIQ.myapp.viewmodel.sweetMeta

/**
 * Ported from the iOS app's `Screens/CalculatorScreen.swift` —
 * `cardCalculateResults` (Card 4) and the file's bottom "Result cards"
 * section (`ProofTimelineCard`/`BakingGuideCard`/`MixingGuideCard`).
 *
 * [onOpenNutrition]/[onOpenAutolyse] default to no-ops — `NutritionAnalysisScreen`/
 * `AutolyseGuidanceScreen` are their own porting step (not yet wired
 * into navigation); the buttons that launch them are real and rendered
 * here now so this file doesn't need to change shape again once that
 * step lands, only the call site.
 *
 * **Queue for Later, Start Now, Schedule Bake, and Share Recipe are all
 * wired for real now** — `BakeSessionEngine`/`BakeStepAssembler`
 * (bake-session-engine session), the Queue/CurrentBake/Schedule screens,
 * and `RecipeXLSXExporter` (XLSX export session) closed the last gaps.
 * Share Recipe's Premium gate is untouched — `userTier` stays hardcoded
 * to `BakeUserTier.FREE` until the RevenueCat session lands (a separate,
 * already-planned item), so today this button always shows the upgrade
 * prompt, exactly matching what a FREE-tier user sees on iOS right now.
 *
 * [onStartedBake] fires once [CalculatorViewModel.handleStartBake]
 * succeeds, carrying the new session id — the caller (`CalculatorScreen`
 * → `MainActivity`) is expected to navigate to Bake Detail (or Current
 * Bake) with it. [onScheduleBake] fires when "Schedule Bake" is tapped,
 * carrying the plan built by [CalculatorViewModel.buildBakePlan] — the
 * caller navigates to the Schedule screen with it (see the
 * `pendingSchedulePlan` handoff in `MainActivity.kt`, mirroring
 * `pendingRecipeId`'s pattern).
 */
@Composable
internal fun CardCalculateResults(
    state: CalculatorUiState,
    viewModel: CalculatorViewModel,
    onOpenNutrition: () -> Unit = {},
    onOpenAutolyse: () -> Unit = {},
    onStartedBake: (String) -> Unit = {},
    onScheduleBake: (RawScheduledBakePlan) -> Unit = {},
) {
    val context = LocalContext.current

    state.startedSessionId?.let { sessionId ->
        LaunchedEffect(sessionId) {
            Haptics.notification(context, HapticNotificationType.SUCCESS)
            onStartedBake(sessionId)
            viewModel.clearStartedSessionId()
        }
    }

    if (state.startError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.update { it.copy(startError = null) } },
            title = { Text("Couldn't Start Bake") },
            text = { Text(state.startError) },
            confirmButton = {
                TextButton(onClick = { viewModel.update { it.copy(startError = null) } }) { Text("OK") }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RecipeSummaryCard(state)
        BreadIQButton(
            label = if (state.loading) "Calculating…" else "Calculate Formula",
            onClick = {
                viewModel.calculate()
                Haptics.impact(context, HapticImpactStyle.LIGHT)
            },
            variant = BreadIQButtonVariant.PRIMARY, loading = state.loading, fullWidth = true,
        )

        val formulaResult = state.formulaResult
        if (formulaResult != null) {
            FormulaResultView(
                result = formulaResult, yeastType = state.yeastType,
                sweetenerMeta = state.sweetenerType?.let { FormulaCalculator.sweetenerMeta[it] },
                humidityMode = state.isHumidityMode, relativeHumidity = if (state.isHumidityMode) state.relativeHumidity.toInt() else null,
                suppressWaterRow = (state.selectedStyle.value == "brioche" || state.selectedStyle.value == "soft_roll") && state.liquidType == "milk",
                breadStyle = state.selectedStyle.value, pretzelBathType = state.pretzelBathType,
            )

            NutritionAnalysisButton(onClick = onOpenNutrition)
            val autolyse = state.autolyseGuidance
            if (autolyse.tier != AutolyseTier.STANDARD) {
                AutolyseGuidanceButton(mandatory = autolyse.tier == AutolyseTier.MANDATORY, onClick = onOpenAutolyse)
            }

            state.proofResult?.let { proofResult ->
                ProofTimelineCard(displayProofResult(state, proofResult))
            }

            if (state.isBasicOrPremium) {
                CostAnalysisCard(state, formulaResult)
            }

            BakingGuideCard(state.selectedStyle, state.temperatureUnit)
            MixingGuideCard(state.selectedStyle, state.proofResult?.bulkFermentMinutes)

            SaveRecipeCard(state, viewModel)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BreadIQButton(
                    label = "Queue for Later", variant = BreadIQButtonVariant.SECONDARY,
                    disabled = state.proofResult == null || state.startingBake,
                    onClick = {
                        // Fired optimistically at the same point the source
                        // fires it synchronously post-save — the Room write
                        // this triggers is a near-instant local SQLite
                        // insert, so the small timing gap versus waiting for
                        // it to actually land isn't perceptible.
                        viewModel.handleQueueBake()
                        Haptics.notification(context, HapticNotificationType.SUCCESS)
                    },
                    modifier = Modifier.weight(1f),
                )
                BreadIQButton(
                    label = if (state.startingBake) "Starting…" else "Start Now", variant = BreadIQButtonVariant.ORANGE,
                    disabled = state.proofResult == null || state.startingBake, loading = state.startingBake,
                    onClick = { viewModel.handleStartBake() },
                    modifier = Modifier.weight(1f),
                )
            }
            BreadIQButton(
                label = "Schedule Bake", variant = BreadIQButtonVariant.SECONDARY,
                disabled = state.proofResult == null || state.startingBake, fullWidth = true,
                onClick = {
                    viewModel.buildBakePlan()?.let { plan ->
                        Haptics.impact(context, HapticImpactStyle.LIGHT)
                        onScheduleBake(plan)
                    }
                },
            )
            BreadIQButton(
                label = if (state.isPremium) "Share Recipe" else "🔒 Share Recipe — Premium",
                variant = BreadIQButtonVariant.SECONDARY, disabled = false, fullWidth = true,
                onClick = { viewModel.shareRecipe() },
            )
        }
    }
}

/**
 * `isPretzel`'s "Rest & Alkaline Bath" description override — the
 * source builds a modified copy of `proofResult` just for the on-screen
 * `ProofTimeline` display (the version fed to `BakeStepAssembler` when a
 * bake actually starts uses its own, separately-verified pretzel bath
 * text — out of scope this session, see [CardCalculateResults]'s own
 * doc comment on "Start Now").
 */
private fun displayProofResult(state: CalculatorUiState, result: ProofTimeResult): ProofTimeResult {
    if (!state.isPretzel) return result
    val dip = if (state.selectedShapeValue in setOf("pretzel_sticks_6pk", "pretzel_slider_6pk")) "15–20 sec" else "20–25 sec"
    val bathDesc = if (state.pretzelBathType == "lye")
        "Rest shaped pretzels uncovered for 15–20 min — gluten relaxes, surface dries slightly for better bath adhesion. Lye bath: Dissolve 30–40g food-grade lye per 1000g cold water (always add lye to water, never the reverse). Wear rubber gloves and eye protection. Dip each pretzel $dip. Transfer to parchment. Score the thick arch once, apply coarse pretzel salt. Lye neutralizes completely during baking. Bake at 400–425°F for 10–14 min to deep mahogany. Internal temp 190–195°F."
    else
        "Rest shaped pretzels uncovered for 15–20 min — gluten relaxes, surface dries slightly for better bath adhesion. Baked baking soda bath: Spread baking soda on a foil-lined sheet pan and bake at 250°F for 1 hour before use — this significantly increases alkalinity. Short on time? Unbaked baking soda works but produces a milder result. Dissolve 50g baked baking soda (or unbaked) plus 1 tbsp barley malt syrup per 1000g water. Dip each pretzel $dip. Transfer to parchment. Score the arch once, apply coarse pretzel salt. Bake at 400°F for 10–14 min to deep mahogany. Internal temp 190–195°F."
    return result.copy(stages = result.stages.map { stage -> if (stage.name == "Rest & Alkaline Bath") stage.copy(description = bathDesc) else stage })
}

@Composable
private fun RecipeSummaryCard(state: CalculatorUiState) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcSectionLabel("Recipe Summary")
            FlowChipGroup {
                SummaryChip(state.selectedStyle.label, colors.primary)
                state.selectedShape?.let { SummaryChip(it.label, null) }
                val n = state.numLoaves.toInt()
                SummaryChip("$n ${if (n == 1) "loaf" else "loaves"}", null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryStat("H", "${state.hydration.toInt()}%")
                SummaryStat("Yeast", String.format("%.1f%%", state.yeast))
                SummaryStat("Salt", String.format("%.1f%%", state.salt))
                if (state.fat > 0) SummaryStat("Fat", "${state.fat.toInt()}%")
            }
            if (state.isSpeedRun || state.useColdRetard || state.usePrefermant || state.isHumidityMode) {
                FlowChipGroup {
                    if (state.isSpeedRun) SummaryChip("⚡ SpeedRun™", Color(0xFF92400E), Color(0xFFFEF3C7))
                    if (state.useColdRetard) SummaryChip("❄ Cold Ferment ${state.coldRetardHours.toInt()}h", Color(0xFF1D4ED8), Color(0xFFEFF6FF))
                    if (state.usePrefermant) state.prefInfo?.let { SummaryChip(it.label, null) }
                    if (state.isHumidityMode) SummaryChip("💧 ${state.relativeHumidity.toInt()}% RH", Color(0xFF0369A1), Color(0xFFF0F9FF))
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String, color: Color?, background: Color? = null) {
    val colors = LocalBreadIQColors.current
    Text(
        text = text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color ?: colors.foreground,
        modifier = Modifier
            .clip(CircleShape)
            .background(background ?: (color?.copy(alpha = 0.1f) ?: colors.muted))
            .border(1.dp, (color ?: colors.border).copy(alpha = if (color != null) 0.4f else 1f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun RowScope.SummaryStat(label: String, value: String) {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.muted)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp),
    ) {
        Text(label, fontSize = 10.sp, color = colors.mutedForeground)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
    }
}

@Composable
private fun NutritionAnalysisButton(onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("🥗", fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Nutritional Analysis", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                Text("Estimated calories & macros for this bake", fontSize = 11.sp, color = colors.mutedForeground)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AutolyseGuidanceButton(mandatory: Boolean, onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("🌾", fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Autolyse Guidance", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                Text(
                    if (mandatory) "Mandatory for this blend — mixing & timing details" else "Recommended for this blend — mixing & timing details",
                    fontSize = 11.sp, color = colors.mutedForeground,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CostAnalysisCard(state: CalculatorUiState, formula: FormulaResult) {
    val colors = LocalBreadIQColors.current
    // `IngredientPriceOverride`'s local, DB-backed custom prices — same
    // source as `IngredientCostsScreen` would use, no network call
    // needed (unlike the source's `GET /api/ingredient-costs`).
    val customPrices = if (state.isPremium) state.customIngredientPrices else emptyMap()
    val cost = CostEstimator.calcBatchCost(formula, state.yeastType, state.sweetenerType, state.flourBlend, customPrices, state.serverReferencePrices)
    val perPiece = CostEstimator.costPerPiece(cost, state.selectedShapeValue, state.numLoaves.toInt())
    val piecesPerUnit = CostEstimator.piecesPerUnit(state.selectedShapeValue)
    val pieceLabel = if (state.selectedShapeValue.startsWith("pizza_")) "pizza" else if (piecesPerUnit > 1) "piece" else "loaf"
    val lineItems = buildList {
        add("Flour" to cost.flourCost)
        add("Fat / Oil" to cost.fatCost)
        add("Salt" to cost.saltCost)
        add("Yeast" to cost.yeastCost)
        if (cost.sweetenerCost > 0) add((state.sweetMeta?.label ?: "Sweetener") to cost.sweetenerCost)
        if (cost.maltCost > 0) add("Diastatic Malt" to cost.maltCost)
        if (cost.eggsCost > 0) add("Eggs" to cost.eggsCost)
        if (cost.milkCost > 0) add("Whole Milk" to cost.milkCost)
        if (cost.butterCost > 0) add("Butter" to cost.butterCost)
    }.filter { it.second > 0 }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estimated Ingredient Cost", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(String.format("$%.2f", cost.total), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                    Text("total batch", fontSize = 11.sp, color = colors.mutedForeground)
                }
                Box(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(String.format("$%.2f", perPiece), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Text("/ $pieceLabel", fontSize = 11.sp, color = colors.mutedForeground)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            lineItems.forEach { (label, amount) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(label, fontSize = 12.sp, color = colors.mutedForeground, modifier = Modifier.weight(1f))
                    Text(String.format("$%.2f", amount), fontSize = 12.sp, color = colors.mutedForeground)
                }
            }
            Text(
                if (state.isPremium) "Based on reference commodity prices. Customize in Ingredient Library."
                else "Based on reference commodity prices. Upgrade to Premium to set your own ingredient costs.",
                fontSize = 11.sp, color = colors.mutedForeground,
            )
        }
    }
}

@Composable
private fun SaveRecipeCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SAVE RECIPE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground)
                if (!state.isBasicOrPremium) {
                    Text(
                        "🔒 Basic+", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        modifier = Modifier.clip(CircleShape).background(Color(0xFFF97316)).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            if (!state.isBasicOrPremium) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.muted)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                ) {
                    Text("🔒", fontSize = 22.sp)
                    Text("Recipe saving requires Basic or Premium", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = colors.foreground)
                    Text("Save up to 10 recipes with Basic, or 50 with Premium.", fontSize = 13.sp, textAlign = TextAlign.Center, color = colors.mutedForeground)
                    BreadIQButton(
                        label = "Upgrade to Save Recipes",
                        onClick = { viewModel.showUpgradeAlert("Save Recipes", "Save up to 10 recipes on Basic or 50 on Premium — including flour blends, fermentation settings, and your full proof timeline.") },
                        variant = BreadIQButtonVariant.ORANGE, fullWidth = true,
                    )
                }
            } else {
                BasicTextField(
                    value = state.recipeName,
                    onValueChange = { v -> viewModel.update { it.copy(recipeName = v, savedId = null) } },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = colors.foreground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.muted)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { innerTextField ->
                        if (state.recipeName.isEmpty()) {
                            Text("Recipe name (e.g. Sunday Baguettes)", fontSize = 14.sp, color = colors.mutedForeground)
                        }
                        innerTextField()
                    },
                )
                BreadIQButton(
                    label = if (state.saving) "Saving…" else if (state.savedId != null) "Saved ✓" else "Save Recipe",
                    onClick = {
                        viewModel.handleSaveRecipe()
                        Haptics.notification(context, HapticNotificationType.SUCCESS)
                    },
                    variant = if (state.savedId != null) BreadIQButtonVariant.SECONDARY else BreadIQButtonVariant.PRIMARY,
                    disabled = state.recipeName.trim().isEmpty() || state.saving || state.savedId != null,
                    loading = state.saving, fullWidth = true,
                )
                if (state.loadedFromRecipeId != null && state.savedId == null && state.formulaResult != null) {
                    BreadIQButton(
                        label = if (state.saving) "Updating…" else "Update Original",
                        onClick = {
                            viewModel.handleUpdateRecipe()
                            Haptics.notification(context, HapticNotificationType.SUCCESS)
                        },
                        variant = BreadIQButtonVariant.SECONDARY, disabled = state.saving, loading = state.saving, fullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProofTimelineCard(result: ProofTimeResult) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("PROOF TIMELINE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = colors.primary)
            Text(
                "Total: ${CalculatorFormatting.formatTime(result.totalMinutes)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = colors.foreground, modifier = Modifier.padding(bottom = 8.dp),
            )
            result.stages.forEachIndexed { i, stage ->
                Row(
                    verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (i == 0) colors.primary else colors.border),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                            Text(stage.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground, modifier = Modifier.weight(1f))
                            Text(CalculatorFormatting.formatTime(stage.durationMinutes), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                        Text(stage.description, fontSize = 11.sp, color = colors.mutedForeground)
                    }
                }
            }
            if (result.notes.isNotEmpty()) {
                Text(
                    result.notes, fontSize = 11.sp, color = colors.mutedForeground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.muted)
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                )
            }
        }
    }
}

/**
 * The source draws its accent border via `.overlay(...)` directly on
 * top of [Card]'s own default border. Compose's [Card] atom doesn't
 * expose an override for its internal border, so this wraps it in an
 * outer [Box] carrying the accent border at the same shape/bounds —
 * visually coincident with (rather than literally drawn on top of)
 * Card's own border, the closest equivalent without changing [Card]'s
 * own signature for two one-off callers.
 */
@Composable
private fun AccentBorderedCard(accent: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BreadIQCornerRadius.dp))
            .border(1.dp, accent, RoundedCornerShape(BreadIQCornerRadius.dp)),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun BakingGuideCard(style: BreadStyleDef, temperatureUnit: TemperatureUnit) {
    val colors = LocalBreadIQColors.current
    AccentBorderedCard(accent = colors.orange.copy(alpha = 0.3f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🌡 Baking Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.orange)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("OVEN TEMP", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground)
                    Text(TemperatureFormatting.displayRange(style.ovenTempF.low, style.ovenTempF.high, temperatureUnit), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
                }
                if (style.internalTempF.low > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("INTERNAL DONE", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground)
                        Text(TemperatureFormatting.displayRange(style.internalTempF.low, style.internalTempF.high, temperatureUnit), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
                    }
                }
            }
            Text(style.crustNote, fontSize = 11.sp, color = colors.mutedForeground)
        }
    }
}

@Composable
private fun MixingGuideCard(style: BreadStyleDef, bulkFermentMinutes: Int?) {
    val colors = LocalBreadIQColors.current
    val intervalMin = if (bulkFermentMinutes != null && style.stretchFoldSets > 0)
        (bulkFermentMinutes.toDouble() / style.stretchFoldSets).swiftRounded().toInt()
    else null

    AccentBorderedCard(accent = colors.primary.copy(alpha = 0.3f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚙ Mixing Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("SPEED", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground)
                    Text(style.mixingSpeed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("TIME", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground)
                    Text(style.mixingTime, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
                }
                if (style.hasStretchFold) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            if (style.value in setOf("artisan", "ciabatta")) "COIL FOLD SETS" else "S&F SETS",
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground,
                        )
                        Text(
                            "${style.stretchFoldSets}${intervalMin?.let { " × $it min" } ?: ""}",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground,
                        )
                    }
                }
            }
            Text(style.mixingNote, fontSize = 11.sp, color = colors.mutedForeground)
            if (style.hasDegassing && style.degassingNote.isNotEmpty()) {
                val text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Degas: ") }
                    append(style.degassingNote)
                }
                Text(
                    text, fontSize = 11.sp, color = colors.foreground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.muted)
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                )
            }
        }
    }
}
