package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.BreadIQ.myapp.core.AutolyseGuidance
import com.BreadIQ.myapp.core.AutolyseTier
import com.BreadIQ.myapp.core.swiftRounded
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `Screens/AutolyseGuidanceScreen.swift`.
 *
 * Nav-pushed (not sheet-presented, unlike the iOS source's `.sheet` —
 * this port uses Compose Navigation for detail screens, matching how
 * the rest of this app's screen-to-screen navigation already works),
 * **user-selectable** autolyse/mixing guidance for blends over 30%
 * combined whole wheat + rye — never shown automatically, same pattern
 * as [NutritionAnalysisScreen]. New product surface, not a port of
 * anything in the original Expo app.
 *
 * Takes a pre-computed [AutolyseGuidance] rather than re-deriving it,
 * so this screen always reflects exactly what [com.BreadIQ.myapp.core.ProofTimeCalculator]
 * actually used for the current formula.
 */
@Composable
fun AutolyseGuidanceScreen(guidance: AutolyseGuidance, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        AutolyseGuidanceHeader(onDismiss = onDismiss)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExplainerCard(guidance.explainer)
            BlendChipRow(guidance)
            MixingCard(guidance)
            BulkCard(guidance)
            ColdRetardCard(guidance)
            if (guidance.pokeTestGuidance.isNotEmpty()) {
                PokeTestCard(guidance.pokeTestGuidance)
            }
        }
    }
}

@Composable
private fun AutolyseGuidanceHeader(onDismiss: () -> Unit) {
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
                Text("Autolyse Guidance", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                Text("For high whole-wheat & rye blends", fontSize = 11.sp, color = colors.mutedForeground)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Cancel, contentDescription = "Close", tint = colors.mutedForeground, modifier = Modifier.width(22.dp).height(22.dp))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/**
 * The source wraps this in `Card(...).background(Color(hex:
 * "#fef3c7")).clipShape(...)` — but [Card]'s own body already paints an
 * opaque `BreadIQColors.card` background as part of the view being
 * modified, and SwiftUI's `.background(_:)` places its content BEHIND
 * that (already-opaque) view, not on top of it. The amber fill can
 * never actually show through anywhere — confirmed dead code in the
 * source, not a real design intent, so it's not reproduced here; this
 * renders as a plain [Card] with its normal background.
 */
@Composable
private fun ExplainerCard(explainer: String) {
    Card(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("ⓘ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
            Text(explainer, fontSize = 12.sp, color = Color(0xFF92400E), modifier = Modifier.weight(1f))
        }
    }
}

private enum class ChipTone { PRIMARY, MUTED }

@Composable
private fun GuidanceChip(text: String, tone: ChipTone) {
    val colors = LocalBreadIQColors.current
    Text(
        text = text, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        color = if (tone == ChipTone.PRIMARY) colors.primary else colors.mutedForeground,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (tone == ChipTone.PRIMARY) colors.primary.copy(alpha = 0.1f) else colors.muted)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun BlendChipRow(guidance: AutolyseGuidance) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GuidanceChip("${guidance.combinedPercent.swiftRounded().toInt()}% combined", ChipTone.PRIMARY)
        if (guidance.wholeWheatPercent > 0) {
            GuidanceChip("${guidance.wholeWheatPercent.swiftRounded().toInt()}% whole wheat", ChipTone.MUTED)
        }
        if (guidance.ryePercent > 0) {
            GuidanceChip("${guidance.ryePercent.swiftRounded().toInt()}% rye", ChipTone.MUTED)
        }
        GuidanceChip(if (guidance.tier == AutolyseTier.MANDATORY) "Mandatory" else "Recommended", ChipTone.MUTED)
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    val colors = LocalBreadIQColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = colors.mutedForeground, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.foreground, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MixingCard(guidance: AutolyseGuidance) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Autolyse & Mixing", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            LabeledRow("Autolyse", "${guidance.autolyseDurationMinutes} min${if (guidance.autolyseRequired) " — required" else ""}")
            LabeledRow("Hydration range", guidance.hydrationRangeLabel)
            LabeledRow("Mixing style", guidance.mixingStyleLabel)
            LabeledRow("Knead", guidance.kneadTimeLabel)
        }
    }
}

@Composable
private fun BulkCard(guidance: AutolyseGuidance) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bulk Fermentation", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            LabeledRow("Volume target", guidance.bulkVolumeTargetLabel)
            guidance.foldScheduleLabel?.let { LabeledRow("Folds", it) }
            val fasterPct = ((1 - guidance.bulkTimeFactor) * 100).swiftRounded().toInt()
            Text(
                "Bulk time in your bake card is already adjusted for this blend — it runs $fasterPct% faster than a standard bread-flour dough would, since autolyse front-loads gluten development.",
                fontSize = 11.sp, color = colors.mutedForeground,
            )
        }
    }
}

@Composable
private fun ColdRetardCard(guidance: AutolyseGuidance) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Cold Retard", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.foreground, modifier = Modifier.weight(1f))
                Text(guidance.coldRetardStrengthLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
            }
            Text(guidance.coldRetardGuidance, fontSize = 11.sp, color = colors.mutedForeground)
        }
    }
}

@Composable
private fun PokeTestCard(pokeTestGuidance: String) {
    val colors = LocalBreadIQColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Reading Doneness", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            Text(pokeTestGuidance, fontSize = 11.sp, color = colors.mutedForeground)
        }
    }
}
