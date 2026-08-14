package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.core.CostEstimator
import com.BreadIQ.myapp.core.NutrientTotals
import com.BreadIQ.myapp.core.NutritionCalculator
import com.BreadIQ.myapp.core.swiftRounded
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `Screens/NutritionAnalysisScreen.swift`.
 *
 * Nav-pushed (not sheet-presented — see [AutolyseGuidanceScreen]'s own
 * doc comment for why), **user-selectable** nutritional analysis for the
 * calculated formula — never shown automatically. New product surface,
 * not a port of anything in the original Expo app.
 *
 * Computes its own [com.BreadIQ.myapp.core.BatchNutrition] from the raw
 * inputs it's handed rather than requiring the caller to pre-compute it
 * — same "derive inside the screen from a passed-in [FormulaResult]"
 * shape the Cost Analysis card already uses for
 * [CostEstimator.calcBatchCost].
 */
@Composable
fun NutritionAnalysisScreen(
    result: FormulaResult,
    styleValue: String,
    styleLabel: String,
    yeastType: String,
    sweetenerType: String?,
    blend: List<FlourBlendEntry>,
    shapeValue: String,
    shapeLabel: String,
    numLoaves: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBreadIQColors.current
    val nutrition = NutritionCalculator.calcBatchNutrition(result, styleValue, yeastType, sweetenerType, blend, shapeValue, numLoaves)
    val piecesPerLoaf = CostEstimator.piecesPerUnit(shapeValue)
    val totalPieces = piecesPerLoaf * numLoaves

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        NutritionHeader(onDismiss = onDismiss)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisclaimerCard()
            RecipeChipRow(styleLabel, shapeLabel)

            val perPiece = nutrition.perPiece
            val perServing = nutrition.perUSDAServing
            if (perPiece != null) {
                BreakdownCard(
                    title = "Per Piece",
                    subtitle = "$totalPieces pieces total ($numLoaves × $piecesPerLoaf)",
                    totals = perPiece, highlighted = true,
                )
            } else if (perServing != null) {
                BreakdownCard(
                    title = "Per USDA Serving (50g)",
                    subtitle = "FDA reference serving for bread — not a slice, since slice thickness can't be verified",
                    totals = perServing, highlighted = true,
                )
            }

            BreakdownCard(title = "Per 100g Baked", subtitle = null, totals = nutrition.per100gBaked, highlighted = false)
            BreakdownCard(title = "Whole Batch Total", subtitle = "$numLoaves × $shapeLabel", totals = nutrition.totals, highlighted = false)

            BakeLossCard(nutrition.rawDoughWeight, nutrition.bakeLossPercent, nutrition.finishedWeight, styleLabel)
        }
    }
}

@Composable
private fun NutritionHeader(onDismiss: () -> Unit) {
    val colors = LocalBreadIQColors.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 14.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Nutritional Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                Text("Estimated from ingredient formula", fontSize = 11.sp, color = colors.mutedForeground)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Cancel, contentDescription = "Close", tint = colors.mutedForeground, modifier = Modifier.height(22.dp))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/**
 * See [AutolyseGuidanceScreen]'s `ExplainerCard` for why the source's
 * outer `.background(amber)` on top of `Card(...)` is dead code (fully
 * obscured by [Card]'s own opaque background) and not reproduced here —
 * this screen's `disclaimerCard` uses the exact same pattern.
 */
@Composable
private fun DisclaimerCard() {
    Card(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("ⓘ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
            Text(
                "These are estimates based on USDA ingredient listings, not lab-verified nutrition facts. Actual values vary with ingredient brands, bake results, and portioning.",
                fontSize = 12.sp, color = Color(0xFF92400E), modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RecipeChipRow(styleLabel: String, shapeLabel: String) {
    val colors = LocalBreadIQColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            styleLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.primary,
            modifier = Modifier.clip(CircleShape).background(colors.primary.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            shapeLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.mutedForeground,
            modifier = Modifier.clip(CircleShape).background(colors.muted).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun BreakdownCard(title: String, subtitle: String?, totals: NutrientTotals, highlighted: Boolean) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (highlighted) colors.primary else colors.foreground)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = colors.mutedForeground)
            }
            val cells = listOf(
                "Calories" to NutritionFormatting.formatKcal(totals.caloriesKcal),
                "Protein" to NutritionFormatting.formatGrams(totals.proteinG),
                "Total Fat" to NutritionFormatting.formatGrams(totals.totalFatG),
                "Carbs" to NutritionFormatting.formatGrams(totals.carbohydratesG),
                "Fiber" to NutritionFormatting.formatGrams(totals.fiberG),
                "Sat. Fat" to NutritionFormatting.formatGrams(totals.saturatedFatG),
            )
            cells.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) -> StatBox(label, value) }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatBox(label: String, value: String) {
    val colors = LocalBreadIQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.muted)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp),
    ) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
        Text(label, fontSize = 10.sp, color = colors.mutedForeground)
    }
}

@Composable
private fun BakeLossCard(rawDoughWeight: Double, bakeLossPercent: Double, finishedWeight: Double, styleLabel: String) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("How this is calculated", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            Text(
                "Total nutrients come from your formula's actual ingredient weights and don't change during baking — only water leaves the dough. " +
                    "Finished weight is estimated as raw dough weight (${NutritionFormatting.formatGrams(rawDoughWeight)}) reduced by a typical " +
                    "${(bakeLossPercent * 100).swiftRounded().toInt()}% bake loss for $styleLabel, giving an estimated baked weight of " +
                    "${NutritionFormatting.formatGrams(finishedWeight)}. This concentrates — not creates — nutrients per gram, the same reason a " +
                    "slice of bread has more calories per gram than the same weight of raw dough.",
                fontSize = 11.sp, color = colors.mutedForeground,
            )
        }
    }
}

/** Small formatting helpers, kept local to this feature the same way [com.BreadIQ.myapp.core.CalculatorFormatting] is kept local to Calculator. */
private object NutritionFormatting {
    fun formatKcal(value: Double): String = value.swiftRounded().toInt().toString()
    fun formatGrams(value: Double): String = String.format("%.1fg", value)
}
