package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.core.AutolyseTier
import com.BreadIQ.myapp.core.TemperatureFormatting
import com.BreadIQ.myapp.core.swiftRounded
import com.BreadIQ.myapp.model.BreadStyleCatalog
import com.BreadIQ.myapp.model.calculatorFlourTypes
import com.BreadIQ.myapp.model.calculatorSweetenerTypes
import com.BreadIQ.myapp.model.calculatorYeastTypes
import com.BreadIQ.myapp.model.maltGuidance
import com.BreadIQ.myapp.model.prefermentTypes
import com.BreadIQ.myapp.model.BakeUserTier
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.flourBlendTemplates
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors
import com.BreadIQ.myapp.viewmodel.CalculatorUiState
import com.BreadIQ.myapp.viewmodel.CalculatorViewModel
import com.BreadIQ.myapp.viewmodel.autolyseGuidance
import com.BreadIQ.myapp.viewmodel.availableShapes
import com.BreadIQ.myapp.viewmodel.hydAdj
import com.BreadIQ.myapp.viewmodel.isBasicOrPremium
import com.BreadIQ.myapp.viewmodel.isPremium
import com.BreadIQ.myapp.viewmodel.isPretzel
import com.BreadIQ.myapp.viewmodel.maxFlour
import com.BreadIQ.myapp.viewmodel.prefInfo
import com.BreadIQ.myapp.viewmodel.selectedShape
import com.BreadIQ.myapp.viewmodel.sweetMeta
import com.BreadIQ.myapp.viewmodel.yeastMeta

/**
 * Ported from the iOS app's `Screens/CalculatorScreen.swift` —
 * `cardStyleShapeBatch` through `cardEnvironment` (Cards 0-3). Card 4
 * (`cardCalculateResults`) is its own porting step, see
 * `CalculatorResultsCard.kt`.
 */
@Composable
internal fun CardStyleShapeBatch(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CalcSectionLabel("Dough Style")
                FlowChipGroup {
                    BreadStyleCatalog.all.forEach { style ->
                        ChipButton(
                            label = style.label,
                            active = style.value == state.selectedStyle.value,
                            onClick = { viewModel.selectStyle(style) },
                        )
                    }
                }
                CalcDivider()
                Text(state.selectedStyle.hydrationNote, fontSize = 12.sp, color = colors.mutedForeground)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CalcSectionLabel("Shape / Format")
                CalcSelectMenu(
                    selectedLabel = state.selectedShape?.label ?: "Select…",
                    options = state.availableShapes.map { CalcSelectOption(it.value, it.label) },
                    onSelect = { v -> viewModel.update { it.copy(selectedShapeValue = v) } },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CalcSectionLabel("Batch Size")
                val batchLabel = when {
                    state.selectedStyle.value.startsWith("pizza_") -> "Pizzas"
                    state.selectedStyle.value == "focaccia" -> "Pans"
                    else -> "Loaves / Units"
                }
                CalcStepperRow(
                    label = "$batchLabel — ${state.numLoaves.toInt()}",
                    value = state.numLoaves,
                    onValueChange = { v -> viewModel.update { it.copy(numLoaves = v) } },
                    minValue = 1.0, maxValue = 20.0, step = 1.0,
                )
                if (state.selectedStyle.value == "baguette") {
                    CalcDivider()
                    CalcStepperRow(
                        label = "Baguette Width Modifier — ${baguetteModLabel(state.baguetteMod)}",
                        value = state.baguetteMod,
                        onValueChange = { v -> viewModel.update { it.copy(baguetteMod = v) } },
                        minValue = 0.75, maxValue = 1.25, step = 0.05, decimals = 2,
                        note = "0.75 = Ficelle (thin) · 1.0 = Classic · 1.25 = Plump/Deli",
                    )
                }
                if (state.selectedStyle.value == "focaccia") {
                    CalcDivider()
                    CalcStepperRow(
                        label = "Focaccia Batch Scale — ${focacciaScaleLabel(state.focacciaScale)}",
                        value = state.focacciaScale,
                        onValueChange = { v -> viewModel.update { it.copy(focacciaScale = v) } },
                        minValue = 0.8, maxValue = 1.2, step = 0.05, decimals = 2,
                        note = "0.8 = Thin & Crispy · 1.0 = Standard · 1.2 = Thick & Pillowy",
                    )
                }
            }
        }
    }
}

private fun baguetteModLabel(mod: Double): String {
    if (mod <= 0.75) return "Ficelle (−25%)"
    if (mod >= 1.25) return "Plump/Deli (+25%)"
    if (mod == 1.0) return "Classic"
    val pct = ((mod - 1) * 100).swiftRounded().toInt()
    return "${if (pct > 0) "+" else ""}$pct%"
}

private fun focacciaScaleLabel(scale: Double): String {
    if (scale <= 0.8) return "Thin & Crispy (−20%)"
    if (scale >= 1.2) return "Thick & Pillowy (+20%)"
    if (scale == 1.0) return "Standard"
    val pct = ((scale - 1) * 100).swiftRounded().toInt()
    return "${if (pct > 0) "+" else ""}$pct%"
}

// MARK: - Card 1: Flour blend / hydration / salt / fat / yeast / sweetener

@Composable
internal fun CardFlourAndFormula(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlourBlendCard(state, viewModel)
        if (state.selectedStyle.value != "brioche") FatCard(state, viewModel)
        if (state.selectedStyle.value == "soft_roll") {
            LiquidToggleCard(
                title = "Final Dough Liquid", options = listOf("water", "milk"),
                liquidType = state.liquidType, onSelect = { v -> viewModel.update { it.copy(liquidType = v) } },
            )
        }
        if (state.selectedStyle.value == "brioche") BriocheEnrichedCard(state, viewModel)
        if (state.isPretzel) PretzelBathTypeCard(state, viewModel)
        SweetenerCard(state, viewModel)
        AdvancedFormulaSection(state, viewModel)
    }
}

@Composable
private fun FlourBlendCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    val hydAdj = state.hydAdj
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CalcSectionLabel("Flour Blend")
                Box(modifier = Modifier.weight(1f))
                if (hydAdj != 0.0) {
                    val positive = hydAdj > 0
                    Text(
                        text = "${if (positive) "+" else ""}${hydAdj.toInt()}% hydration",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (positive) Color(0xFF1D4ED8) else Color(0xFFC2410C),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (positive) Color(0xFFDBEAFE) else Color(0xFFFFEDD5))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            FlowChipGroup {
                flourBlendTemplates.forEach { template ->
                    val active = state.flourBlend.size == template.blend.size &&
                        state.flourBlend.zip(template.blend).all { (a, b) -> a.type == b.type && a.percent == b.percent }
                    ChipButton(label = template.label, active = active, onClick = { viewModel.applyFlourTemplate(template) })
                }
            }

            state.flourBlend.forEachIndexed { idx, flour ->
                FlourRow(state = state, viewModel = viewModel, idx = idx, flour = flour)
            }

            if (state.flourBlend.size < 5) {
                val maxFlour = state.maxFlour
                val atLimit = state.flourBlend.size >= maxFlour
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (atLimit) Color(0xFFFCD34D) else colors.border, RoundedCornerShape(8.dp))
                        .clickable {
                            if (atLimit) {
                                if (state.userTier == BakeUserTier.FREE) {
                                    viewModel.showUpgradeAlert("Multi-Grain Blends", "Blending flour types unlocks complex flavor and texture. Upgrade to Basic to blend up to 2 grain types per recipe.")
                                } else {
                                    viewModel.showUpgradeAlert("2-Flour Limit Reached", "You've reached the 2-flour limit. Upgrade to Premium to blend up to 5 grains for complex heritage bakes.")
                                }
                            } else {
                                viewModel.addFlour()
                            }
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Icon2(if (atLimit) Icons.Filled.Lock else Icons.Filled.Add, tint = if (atLimit) Color(0xFFD97706) else colors.mutedForeground)
                    val tierLabel = if (state.userTier == BakeUserTier.FREE) "Basic" else "Premium"
                    Text(
                        text = if (atLimit) "Add flour — $tierLabel (${state.flourBlend.size}/$maxFlour)" else "Add flour (${state.flourBlend.size}/$maxFlour)",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (atLimit) Color(0xFFD97706) else colors.mutedForeground,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlourRow(state: CalculatorUiState, viewModel: CalculatorViewModel, idx: Int, flour: FlourBlendEntry) {
    val colors = LocalBreadIQColors.current
    val meta = calculatorFlourTypes.firstOrNull { it.value == flour.type }
    val usedTypes = state.flourBlend.filterIndexed { i, _ -> i != idx }.map { it.type }.toSet()
    val secSum = state.flourBlend.filterIndexed { i, _ -> i != 0 && i != idx }.sumOf { it.percent }
    val sliderMax = maxOf(1.0, 99 - secSum)

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.muted.copy(alpha = 0.4f))
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (idx == 0) "PRIMARY" else "FLOUR ${idx + 1}",
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = if (idx == 0) colors.primary else colors.mutedForeground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (idx == 0) colors.primary.copy(alpha = 0.12f) else colors.muted)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(meta?.label ?: flour.type, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
            }
            Box(modifier = Modifier.weight(1f))
            Text("${flour.percent.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            if (idx > 0) {
                Box(modifier = Modifier.padding(start = 6.dp).clickable { viewModel.removeFlour(idx) }) {
                    Icon2(Icons.Filled.Close, tint = colors.mutedForeground)
                }
            }
        }

        CalcSelectMenu(
            selectedLabel = meta?.label ?: flour.type,
            options = calculatorFlourTypes.map { CalcSelectOption(it.value, it.label, if (usedTypes.contains(it.value)) "Already in blend" else null) },
            onSelect = { v -> viewModel.updateFlourType(idx, v) },
        )

        if (idx > 0) {
            CalcStepperRow(
                label = "${meta?.label ?: flour.type} % — ${flour.percent.toInt()}%",
                value = flour.percent,
                onValueChange = { v -> viewModel.updateFlourPercent(idx, v) },
                minValue = 1.0, maxValue = sliderMax, step = 1.0,
            )
        }
        if (meta != null) {
            Text(meta.note, fontSize = 11.sp, color = colors.mutedForeground)
        }
        if (flour.type == "all_purpose" && state.selectedStyle.value in setOf("baguette", "artisan", "ciabatta", "country")) {
            Text(
                "⚠ All-purpose flour works here but bread flour will produce better structure and chew for this dough type.",
                fontSize = 11.sp, color = Color(0xFFF97316),
            )
        }
    }
}

@Composable
private fun AdvancedFormulaSection(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.muted.copy(alpha = 0.4f))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .clickable { viewModel.update { it.copy(showAdvancedFormula = !it.showAdvancedFormula) } }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon2(Icons.Filled.Tune, tint = colors.mutedForeground)
            Text(
                text = if (state.showAdvancedFormula) "Hide Advanced (Hydration, Yeast & Salt)" else "Advanced — Hydration, Yeast & Salt %",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground,
                modifier = Modifier.weight(1f),
            )
            Icon2(if (state.showAdvancedFormula) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, tint = colors.mutedForeground)
        }

        if (state.showAdvancedFormula) {
            HydrationCard(state, viewModel)
            YeastCard(state, viewModel)
            SaltCard(state, viewModel)
        }
    }
}

@Composable
private fun HydrationCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val style = state.selectedStyle
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcSectionLabel("Hydration")
            CalcStepperRow(
                label = "Hydration — ${state.hydration.toInt()}%",
                value = state.hydration,
                onValueChange = { v -> viewModel.update { it.copy(hydration = v) } },
                minValue = 50.0, maxValue = 100.0, step = 1.0,
                note = "Traditional ${style.label}: ${style.hydrationRange.low.toInt()}–${style.hydrationRange.high.toInt()}%",
            )
            if (state.hydration < style.hydrationRange.low - 5) {
                CalcInfoBox("⚠ Critical — Hydration too low (${state.hydration.toInt()}%). Dough will be very stiff — dense, brick-like crumb. Increase to at least ${(style.hydrationRange.low - 5).toInt()}%.", CalcInfoVariant.ERROR)
            }
            if (state.hydration > style.hydrationRange.high + 5) {
                CalcInfoBox("⚠ Critical — Over-hydrated (${state.hydration.toInt()}%). Dough will spread flat with no oven spring. Reduce below ${(style.hydrationRange.high + 5).toInt()}%.", CalcInfoVariant.ERROR)
            }
            if (state.hydration >= style.hydrationRange.low - 5 && state.hydration < style.hydrationRange.low) {
                CalcInfoBox("Pushing lower hydration limits — below ${style.hydrationRange.low.toInt()}% the dough stiffens. Workable with proper technique.", CalcInfoVariant.WARNING)
            }
            if (state.hydration > style.hydrationRange.high && state.hydration <= style.hydrationRange.high + 5) {
                CalcInfoBox("Pushing upper hydration limits — above ${style.hydrationRange.high.toInt()}% the dough is significantly stickier and harder to handle.", CalcInfoVariant.WARNING)
            }
        }
    }
}

@Composable
private fun YeastCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    val yeastMeta = state.yeastMeta
    val style = state.selectedStyle
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcSectionLabel("Yeast")
            Text("Type", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground)
            CalcChipRow(
                options = calculatorYeastTypes.map { CalcChipOption(it.value, it.label) },
                selected = state.yeastType,
                onSelect = { v -> viewModel.update { it.copy(yeastType = v) } },
            )
            Text(yeastMeta.note, fontSize = 12.sp, color = colors.mutedForeground)
            CalcDivider()
            CalcStepperRow(
                label = "${yeastMeta.label} Yeast",
                value = state.yeast,
                onValueChange = { v -> viewModel.update { it.copy(yeast = v) } },
                minValue = 0.1, maxValue = 2.0, step = 0.1, decimals = 1,
                note = "Ideal for ${style.label}: ${String.format("%.1f", style.yeastIdeal * yeastMeta.factor)}% · Range: ${String.format("%.1f", style.yeastRange.low * yeastMeta.factor)}–${String.format("%.1f", style.yeastRange.high * yeastMeta.factor)}%",
                displayText = String.format("%.1f%%", state.yeast * yeastMeta.factor),
            )
        }
    }
}

@Composable
private fun SaltCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcSectionLabel("Salt")
            CalcStepperRow(
                label = "Salt — ${String.format("%.1f", state.salt)}%",
                value = state.salt,
                onValueChange = { v -> viewModel.update { it.copy(salt = v) } },
                minValue = 1.5, maxValue = 3.5, step = 0.1, decimals = 1,
                note = "Standard professional target is 2%. Range 1.7–2.4% is acceptable.",
            )
            if (state.isPretzel) {
                Text(
                    "Pretzel dough salt (${String.format("%.1f", state.salt)}%) is intentionally lower than standard lean doughs. Coarse exterior salt applied after the alkaline bath provides the finishing seasoning — the two work together to build the characteristic pretzel flavour profile.",
                    fontSize = 11.sp, color = colors.mutedForeground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.navyLight)
                        .padding(8.dp),
                )
            }
            if (state.salt < 1.7 && !state.isPretzel) {
                CalcInfoBox("Low salt (${String.format("%.1f", state.salt)}%) — may taste bland and will affect gluten development. Fermentation runs faster.", CalcInfoVariant.WARNING)
            }
            if (state.salt > 2.4 && state.salt < 3.0) {
                CalcInfoBox("High salt (${String.format("%.1f", state.salt)}%) — inhibits yeast development. Proofing timeline extended.", CalcInfoVariant.WARNING)
            }
            if (state.salt >= 3.0) {
                CalcInfoBox("⚠ Critical salt level (${String.format("%.1f", state.salt)}%) — significantly inhibits yeast, risks over-salted bread. Not recommended.", CalcInfoVariant.ERROR)
            }
        }
    }
}

@Composable
private fun FatCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    val style = state.selectedStyle
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcSectionLabel("Fat (Baker's %)")
            CalcStepperRow(
                label = "Fat — ${state.fat.toInt()}%",
                value = state.fat,
                onValueChange = { v -> viewModel.update { it.copy(fat = v) } },
                minValue = 0.0, maxValue = 20.0, step = 1.0,
                note = "Olive oil by default — can sub lard or other fat types.",
            )
            val ideal = style.fatIdeal
            if (ideal != null && state.fat != ideal) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.navyLight)
                        .padding(8.dp),
                ) {
                    Text("Suggested for ${style.label}: ${ideal.toInt()}%", fontSize = 12.sp, color = colors.primary, modifier = Modifier.weight(1f))
                    Text(
                        "Apply", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
                        modifier = Modifier.clickable { viewModel.update { it.copy(fat = ideal) } },
                    )
                }
            }
            if (style.value != "ciabatta" && state.fat > 10) {
                CalcInfoBox("⚠ Critical — Fat exceeds 10%. Significantly weakens gluten network. Adjusted mixing and fermentation techniques required.", CalcInfoVariant.ERROR)
            }
            if (style.value == "ciabatta" && state.fat > 7) {
                CalcInfoBox("⚠ Critical — Fat exceeds 7% for ciabatta. Open crumb structure cannot form. Reduce to 5% or below.", CalcInfoVariant.ERROR)
            }
        }
    }
}

@Composable
private fun LiquidToggleCard(title: String, options: List<String>, liquidType: String, onSelect: (String) -> Unit) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcSectionLabel(title)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                options.forEach { opt ->
                    val active = liquidType == opt
                    Text(
                        text = if (opt == "water") "Water" else "Whole Milk",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else colors.foreground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) colors.primary else colors.navyLight)
                            .border(1.dp, if (active) colors.primary else colors.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onSelect(opt) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            Text(
                if (liquidType == "milk")
                    "Milk replaces water as the primary liquid. Milk-based soft rolls have a richer crumb, enhanced browning, and a softer crust."
                else
                    "Water-based soft rolls have a lighter crumb and neutral flavor — the standard baseline.",
                fontSize = 12.sp, color = colors.mutedForeground,
            )
            Text(
                "Pre-ferment stages always use water. This toggle applies to the final dough mix only.",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD97706),
            )
        }
    }
}

@Composable
private fun BriocheEnrichedCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcSectionLabel("Enriched Ingredients")
            CalcInfoBox("Adjust to taste — these set the richness level. Egg moisture and milk count toward effective hydration; free water fills the remainder.", CalcInfoVariant.NEUTRAL)
            Text("FINAL DOUGH LIQUID", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.mutedForeground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("milk", "water").forEach { opt ->
                    val active = state.liquidType == opt
                    Text(
                        text = if (opt == "milk") "Whole Milk" else "Water",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else colors.foreground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) colors.primary else colors.navyLight)
                            .border(1.dp, if (active) colors.primary else colors.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { viewModel.update { it.copy(liquidType = opt) } }
                            .padding(vertical = 9.dp),
                    )
                }
            }
            Text(
                if (state.liquidType == "milk")
                    "Milk-based brioche is richer with a more tender crumb and enhanced browning — the professional standard."
                else
                    "Water-based brioche produces a slightly crisper crust and cleaner flavor. Eggs still enrich the crumb.",
                fontSize = 12.sp, color = colors.mutedForeground,
            )
            Text(
                "Pre-ferment stages always use water. This toggle applies to the final dough mix only.",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD97706),
            )

            CalcStepperRow(
                label = "Eggs — ${state.eggsPercent.toInt()}%",
                value = state.eggsPercent, onValueChange = { v -> viewModel.update { it.copy(eggsPercent = v) } },
                minValue = 20.0, maxValue = 60.0, step = 1.0,
                note = "Baker's % — 40% is classic brioche (≈ 4 large eggs per 500g flour).",
            )
            if (state.liquidType == "milk") {
                CalcStepperRow(
                    label = "${state.dairyDisplayName ?: "Whole Milk"} — ${state.milkPercent.toInt()}%",
                    value = state.milkPercent, onValueChange = { v -> viewModel.update { it.copy(milkPercent = v) } },
                    minValue = 5.0, maxValue = 40.0, step = 1.0,
                    note = "Baker's % — 21% adds richness and browning. Final dough only.",
                )
            }
            CalcStepperRow(
                label = "Butter — ${state.butterPercent.toInt()}%",
                value = state.butterPercent, onValueChange = { v -> viewModel.update { it.copy(butterPercent = v) } },
                minValue = 20.0, maxValue = 80.0, step = 1.0,
                note = "Baker's % — 60% is classic French brioche. Add cold-cubed after full gluten development.",
            )
        }
    }
}

@Composable
private fun PretzelBathTypeCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcSectionLabel("Alkaline Bath Method")
            Text(
                "Lye gives the deepest color and most authentic crust. Baked baking soda + barley malt is the approachable home option.",
                fontSize = 12.sp, color = colors.mutedForeground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("baked_baking_soda" to "Baked Baking Soda + Malt", "lye" to "Food-Grade Lye").forEach { (value, label) ->
                    val active = state.pretzelBathType == value
                    Text(
                        text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (active) Color.White else colors.foreground,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) colors.primary else colors.navyLight)
                            .border(1.dp, if (active) colors.primary else colors.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { viewModel.update { it.copy(pretzelBathType = value) } }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SweetenerCard(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcSectionLabel(if (state.selectedStyle.value == "soft_roll") "Sweetener" else "Sweetener (optional)")
            FlowChipGroup {
                if (state.selectedStyle.value != "soft_roll") {
                    val active = state.sweetenerType == null
                    Text(
                        text = "None", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (active) Color.White else colors.mutedForeground,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) colors.primary else Color.Transparent)
                            .border(1.dp, if (active) colors.primary else colors.border, CircleShape)
                            .clickable { viewModel.update { it.copy(sweetenerType = null) } }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                calculatorSweetenerTypes.forEach { s ->
                    val active = state.sweetenerType == s.value
                    Text(
                        text = s.label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (active) Color.White else colors.mutedForeground,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) colors.primary else Color.Transparent)
                            .border(1.dp, if (active) colors.primary else colors.border, CircleShape)
                            .clickable { viewModel.update { it.copy(sweetenerType = s.value) } }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (state.sweetenerType != null) {
                CalcStepperRow(
                    label = "Amount — ${String.format("%.1f", state.sweetenerPct)}%",
                    value = state.sweetenerPct, onValueChange = { v -> viewModel.update { it.copy(sweetenerPct = v) } },
                    minValue = 0.5, maxValue = 15.0, step = 0.1, decimals = 1,
                    note = if (state.selectedStyle.value == "soft_roll") "3–5% recommended for soft rolls" else "Baker's percentage",
                )
                state.sweetMeta?.let { CalcInfoBox(it.note, CalcInfoVariant.NEUTRAL) }
            }
        }
    }
}

// MARK: - Card 2: Fermentation

@Composable
internal fun CardFermentation(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CalcSectionLabel("Diastatic Malt Powder")
                    Box(modifier = Modifier.weight(1f))
                    PremiumBadge()
                }
                if (state.isPremium) {
                    CalcStepperRow(
                        label = "DMP — ${if (state.maltPct == 0.0) "Off" else String.format("%.2f%%", state.maltPct)}",
                        value = state.maltPct, onValueChange = { v -> viewModel.update { it.copy(maltPct = v) } },
                        minValue = 0.0, maxValue = 1.0, step = 0.05, decimals = 2,
                        note = "0.1–1.0% flour weight. Boosts crust browning, fermentation, and flavor. Set to 0 to skip.",
                    )
                    val guidance = if (state.maltPct > 0) maltGuidance[state.selectedStyle.value] else null
                    if (guidance != null) {
                        CalcInfoBox("Suggested for ${state.selectedStyle.label}: ${guidance.range} (ideal ${guidance.ideal})", CalcInfoVariant.NEUTRAL)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            viewModel.showUpgradeAlert("Diastatic Malt Powder", "Boost crust color, improve fermentation, and deepen flavor. Available on Premium.")
                        },
                    ) {
                        Icon2(Icons.Filled.Lock, tint = Color(0xFFD97706))
                        Text("Locked — tap to unlock with Premium", fontSize = 13.sp, color = Color(0xFFD97706))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CalcSectionLabel("Pre-ferment")
                CalcToggleRow(
                    label = "Use Pre-ferment", description = "Poolish or biga — adds complexity and extensibility.",
                    value = state.usePrefermant,
                    onChange = { v ->
                        if (!state.isBasicOrPremium) {
                            viewModel.showUpgradeAlert("Pre-ferments", "Unlock poolish and biga pre-ferment builds by upgrading to Basic or Premium.")
                        } else {
                            viewModel.update { it.copy(usePrefermant = v) }
                            if (v) viewModel.handlePrefermentTypeChange(state.prefermentType)
                        }
                    },
                )
                if (state.usePrefermant) {
                    CalcSelectMenu(
                        label = "Type",
                        selectedLabel = state.prefInfo?.label ?: "Poolish",
                        options = listOf(
                            CalcSelectOption("poolish", "Poolish", prefermentTypes["poolish"]?.hydrationLabel),
                            CalcSelectOption("biga", "Biga", prefermentTypes["biga"]?.hydrationLabel),
                        ),
                        onSelect = { v -> viewModel.handlePrefermentTypeChange(v) },
                    )
                    state.prefInfo?.let { CalcInfoBox(it.description, CalcInfoVariant.NEUTRAL) }
                    CalcStepperRow(
                        label = "Flour Allocation — ${state.prefermentFlourPct.toInt()}%",
                        value = state.prefermentFlourPct, onValueChange = { v -> viewModel.update { it.copy(prefermentFlourPct = v) } },
                        minValue = 10.0, maxValue = 60.0, step = 5.0, note = "% of total flour in pre-ferment",
                    )
                    CalcStepperRow(
                        label = "Hydration — ${state.prefermentHydration.toInt()}%",
                        value = state.prefermentHydration, onValueChange = { v -> viewModel.update { it.copy(prefermentHydration = v) } },
                        minValue = 40.0, maxValue = 130.0, step = 5.0, note = "Water as % of pre-ferment flour",
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CalcSectionLabel("Fermentation Speed")
                CalcToggleRow(
                    label = "⚡ SpeedRun™ Mode",
                    description = "Doubles yeast, raises water temp to 88°F for a same-day bake. Disables pre-ferment and cold retard.",
                    value = state.isSpeedRun, premium = true,
                    onChange = { v ->
                        if (!state.isPremium) {
                            viewModel.showUpgradeAlert("SpeedRun™", "Need it faster? Upgrade to Premium to unlock programmable urgency.")
                        } else {
                            viewModel.handleSpeedRunToggle(v)
                        }
                    },
                )
                if (state.isSpeedRun && state.selectedStyle.value == "brioche") {
                    Text(
                        "SpeedRun™ on brioche will produce a same-day result but sacrifices some of the flavor complexity that extended fermentation develops. For best results, consider a standard or cold ferment timeline.",
                        fontSize = 12.sp, color = Color(0xFF0EA5E9),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    )
                }
                CalcDivider()
                CalcToggleRow(
                    label = "❄ Cold Ferment", description = "Ferment shaped dough in the refrigerator (37–39°F) for 8–24 hours for deeper flavor.",
                    value = state.useColdRetard, premium = true,
                    onChange = { v ->
                        if (!state.isPremium) {
                            viewModel.showUpgradeAlert("Cold Ferment", "Cold fermentation builds deeper flavor and better gluten structure. Available on Premium.")
                        } else {
                            viewModel.update { it.copy(useColdRetard = v) }
                        }
                    },
                )
                val guidance = state.autolyseGuidance
                if (guidance.tier != AutolyseTier.STANDARD && !state.useColdRetard) {
                    CalcInfoBox("🌾 Cold Ferment — ${guidance.coldRetardStrengthLabel}: ${guidance.coldRetardGuidance}", CalcInfoVariant.WARNING)
                }
                if (state.useColdRetard) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF6FF))
                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        CalcStepperRow(
                            label = "Cold Ferment Duration — ${state.coldRetardHours.toInt()}h",
                            value = state.coldRetardHours, onValueChange = { v -> viewModel.update { it.copy(coldRetardHours = v) } },
                            minValue = 1.0, maxValue = 24.0, step = 1.0, note = "12–16h is the sweet spot for most bakers.",
                        )
                        CalcDivider()
                        Text("FRIDGE TEMP PRESET", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                        CalcChipRow(
                            options = listOf(
                                CalcChipOption("36", "${TemperatureFormatting.display(36.0, state.temperatureUnit)} Cold"),
                                CalcChipOption("38", "${TemperatureFormatting.display(38.0, state.temperatureUnit)} Standard"),
                                CalcChipOption("41", "${TemperatureFormatting.display(41.0, state.temperatureUnit)} Warm"),
                            ),
                            selected = state.coldRetardTempF.toInt().toString(),
                            onSelect = { v -> viewModel.update { it.copy(coldRetardTempF = v.toDoubleOrNull() ?: 38.0) } },
                        )
                        if (state.maltPct > 0 && state.coldRetardHours > 12) {
                            CalcInfoBox("⚠ Critical — Extended DMP exposure will degrade gluten structure. Reduce malt or shorten cold ferment to 12h or less.", CalcInfoVariant.ERROR)
                        } else if (state.maltPct > 0 && state.coldRetardHours > 8) {
                            CalcInfoBox("DMP Caution — Activity may outpace yeast consumption beyond 8h. Consider reducing malt to 0.2% for long retards.", CalcInfoVariant.WARNING)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumBadge() {
    Text(
        text = "Premium", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFFFEF3C7))
            .border(1.dp, Color(0xFFFCD34D), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// MARK: - Card 3: Environment

@Composable
internal fun CardEnvironment(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcSectionLabel("Bulk Fermentation Environment")
            CalcStepperRow(
                label = "Water Temp — ${TemperatureFormatting.display(state.waterTempF, state.temperatureUnit)}",
                value = state.waterTempF, onValueChange = { v -> viewModel.update { it.copy(waterTempF = v) } },
                minValue = 32.0, maxValue = 120.0, step = 1.0, unit = state.temperatureUnit.symbol,
                note = "${TemperatureFormatting.displayRange(75.0, 80.0, state.temperatureUnit)} for most doughs.",
                temperatureUnit = state.temperatureUnit,
            )
            CalcStepperRow(
                label = "Kitchen Temp — ${TemperatureFormatting.display(state.ambientTempF, state.temperatureUnit)}",
                value = state.ambientTempF, onValueChange = { v -> viewModel.update { it.copy(ambientTempF = v) } },
                minValue = 40.0, maxValue = 100.0, step = 1.0, unit = state.temperatureUnit.symbol,
                note = "Ambient during bulk.", temperatureUnit = state.temperatureUnit,
            )
            CalcDivider()
            CalcToggleRow(
                label = "💧 Humidity Adjustment", description = "Water weight and proof times adjust automatically to your environment.",
                value = state.isHumidityMode, premium = true,
                onChange = { v ->
                    if (!state.isPremium) {
                        viewModel.showUpgradeAlert("Humidity Adjustment", "Dial in water weight and proof times for your kitchen environment. Available on Premium.")
                    } else {
                        viewModel.update { it.copy(isHumidityMode = v) }
                    }
                },
            )
            if (state.isHumidityMode && state.isPremium) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF0F9FF))
                        .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    CalcStepperRow(
                        label = "Relative Humidity — ${state.relativeHumidity.toInt()}%",
                        value = state.relativeHumidity, onValueChange = { v -> viewModel.update { it.copy(relativeHumidity = v) } },
                        minValue = 5.0, maxValue = 100.0, step = 5.0, note = "Your kitchen's ambient humidity during mixing and fermentation.",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Arid", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0369A1))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2FE)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((state.relativeHumidity / 100).toFloat().coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0EA5E9)),
                            )
                        }
                        Text("Humid", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0369A1))
                    }
                    if (state.relativeHumidity >= 65) {
                        CalcInfoBox("High humidity — water weight reduced to preserve dough structure. Baker's percentages are unchanged.", CalcInfoVariant.BLUE)
                    }
                    if (state.relativeHumidity <= 35) {
                        CalcInfoBox(
                            if (state.relativeHumidity <= 15)
                                "Very dry air — water adjusted upward. Keep dough sealed; surface skin forms fast at this level."
                            else
                                "Dry air — water adjusted upward. Keep dough covered during fermentation.",
                            CalcInfoVariant.WARNING,
                        )
                    }
                }
            }
            CalcDivider()
            Text("FINAL PROOF AMBIENT TEMP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.mutedForeground)
            CalcStepperRow(
                label = "Final Proof Temp — ${TemperatureFormatting.display(state.finalProofTempF, state.temperatureUnit)}",
                value = state.finalProofTempF, onValueChange = { v -> viewModel.update { it.copy(finalProofTempF = v) } },
                minValue = 55.0, maxValue = 100.0, step = 1.0, unit = state.temperatureUnit.symbol,
                temperatureUnit = state.temperatureUnit,
            )
            FlowChipGroup {
                listOf("Cool" to 65.0, "Room" to 72.0, "Warm" to 78.0, "Oven light" to 82.0, "Proof box" to 90.0).forEach { (labelPrefix, value) ->
                    val label = "$labelPrefix ~${TemperatureFormatting.display(value, state.temperatureUnit)}"
                    val active = state.finalProofTempF == value
                    Text(
                        text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (active) Color.White else Color(0xFF92400E),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) Color(0xFFD97706) else Color(0xFFFEF3C7))
                            .border(1.dp, if (active) Color(0xFFD97706) else Color(0xFFFDE68A), CircleShape)
                            .clickable { viewModel.update { it.copy(finalProofTempF = value) } }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// MARK: - Card 4: Calculate / Results (own porting step — see CalculatorResultsCard.kt)

@Composable
internal fun CardCalculateResults(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Card 5 of 5 — Calculate & Results — coming in the next commit.", color = colors.mutedForeground)
    }
}

// MARK: - Small local helpers

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChipGroup(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = { content() })
}

@Composable
private fun ChipButton(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Text(
        text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        color = if (active) Color.White else colors.foreground,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) colors.primary else colors.muted)
            .border(1.dp, if (active) colors.primary else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun Icon2(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
}
