package com.BreadIQ.myapp.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.BreadIQ.myapp.core.FormulaCalculator
import com.BreadIQ.myapp.core.swiftRounded
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.model.PrefermentResult
import com.BreadIQ.myapp.ui.components.Card
import com.BreadIQ.myapp.ui.theme.LocalBreadIQColors

/**
 * Ported from the iOS app's `UI/FormulaResultView.swift`, itself a port
 * of `components/FormulaResult.tsx`.
 *
 * **Design call carried over from the iOS port: a single [result]
 * parameter, not the source's four (`result`/`preferment`/
 * `flourBreakdown`/`bakerPercentages`).** The source's own call site
 * always passes the exact same object's own fields re-threaded as
 * separate props for no reason tied to this codebase — [FormulaResult]
 * already nests all three, so splitting them back out here would just
 * reproduce React prop-drilling in a language that doesn't need it.
 */
@Composable
fun FormulaResultView(
    result: FormulaResult,
    modifier: Modifier = Modifier,
    yeastType: String = "instant",
    sweetenerMeta: FormulaCalculator.SweetenerMeta? = null,
    humidityMode: Boolean = false,
    relativeHumidity: Int? = null,
    suppressWaterRow: Boolean = false,
    breadStyle: String? = null,
    pretzelBathType: String = "baked_baking_soda",
) {
    val yeastFactor = FormulaResultFormatting.yeastFactors[yeastType] ?: 1.0
    val yeastLabel = FormulaResultFormatting.yeastLabels[yeastType] ?: "Instant"

    // `!!(result.eggWeight ?? result.milkWeight ?? result.butterWeight)` in
    // the source. FormulaCalculator only ever produces these three fields
    // as null or a strictly-positive computed weight (never present-but-
    // zero), so a plain "is any of the three non-null" check here is
    // behaviorally identical to JS's nullish-coalescing-then-truthy-check.
    val isBrioche = result.eggWeight != null || result.milkWeight != null || result.butterWeight != null

    val rhDirection = if (humidityMode && relativeHumidity != null)
        FormulaResultFormatting.humidityDirection(relativeHumidity.toDouble())
    else
        HumidityDirection.NEUTRAL

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MainCard(
            result = result,
            yeastType = yeastType,
            yeastFactor = yeastFactor,
            yeastLabel = yeastLabel,
            sweetenerMeta = sweetenerMeta,
            suppressWaterRow = suppressWaterRow,
            rhDirection = rhDirection,
            relativeHumidity = relativeHumidity,
            isBrioche = isBrioche,
        )
        if (breadStyle == "bagel") {
            BagelBoilCard()
        }
        if (breadStyle == "pretzel" && pretzelBathType == "lye") {
            PretzelLyeCard()
        }
        if (breadStyle == "pretzel" && pretzelBathType == "baked_baking_soda") {
            PretzelBakingSodaCard()
        }
        result.preferment?.let { preferment ->
            PrefermentCard(preferment, yeastType, yeastFactor, yeastLabel)
            FinalMixCard(preferment, yeastType, yeastFactor, yeastLabel)
        }
    }
}

private enum class HumidityDirection { HIGH, LOW, NEUTRAL }

/**
 * Static tables / pure formatting — source-verified against
 * `FormulaResult.tsx`, cross-checked for mobile/web drift against the
 * byte-identical duplicate in `bread-lab/src/pages/calculator.tsx`.
 */
private object FormulaResultFormatting {
    val yeastFactors: Map<String, Double> = mapOf("instant" to 1.0, "active_dry" to 1.25, "fresh" to 3.0)
    val yeastLabels: Map<String, String> = mapOf("instant" to "Instant", "active_dry" to "Active Dry", "fresh" to "Fresh/Cake")

    /** Trims a trailing `.0`/`.00…`, matching JS's default `Number` → `String` coercion (`${n}`). */
    fun formatNumber(n: Double): String {
        if (n == n.swiftRounded()) return n.toLong().toString()
        var s = String.format("%.4f", n)
        while (s.endsWith("0")) s = s.dropLast(1)
        if (s.endsWith(".")) s = s.dropLast(1)
        return s
    }

    /** `g()` — round to 1 decimal, then format. */
    fun formatGrams(n: Double): String {
        val rounded = (n * 10).swiftRounded() / 10
        return "${formatNumber(rounded)}g"
    }

    fun formatFixed(n: Double, places: Int): String = String.format("%.${places}f", n)

    /** `getHumidityDirection()`. */
    fun humidityDirection(rh: Double): HumidityDirection = when {
        rh >= 65 -> HumidityDirection.HIGH
        rh <= 35 -> HumidityDirection.LOW
        else -> HumidityDirection.NEUTRAL
    }

    /** `getYeastVolumeApprox()`. */
    fun yeastVolumeApprox(grams: Double, yeastType: String): String? {
        if (yeastType == "fresh") return null
        val r = grams.swiftRounded().toInt()
        if (r < 1 || r > 4) return null
        val instant = listOf("¼ tsp", "½ tsp", "¾ tsp", "1 tsp")
        val activeDry = listOf("just under ¼ tsp", "just under ½ tsp", "just under ¾ tsp", "just under 1 tsp")
        return (if (yeastType == "instant") instant else activeDry)[r - 1]
    }

    /**
     * `preferment.type.charAt(0).toUpperCase() + preferment.type.slice(1).replace("_", " ")`
     * — capitalizes the first letter and replaces only the FIRST
     * underscore (JS `String.replace` with a plain-string pattern, not a
     * regex/`replaceAll`).
     */
    fun prefermentTypeLabel(type: String): String {
        if (type.isEmpty()) return type
        val capitalized = type[0].uppercaseChar() + type.substring(1)
        val idx = capitalized.indexOf('_')
        return if (idx >= 0) capitalized.replaceRange(idx, idx + 1, " ") else capitalized
    }
}

// MARK: - Main formula card

@Composable
private fun MainCard(
    result: FormulaResult,
    yeastType: String,
    yeastFactor: Double,
    yeastLabel: String,
    sweetenerMeta: FormulaCalculator.SweetenerMeta?,
    suppressWaterRow: Boolean,
    rhDirection: HumidityDirection,
    relativeHumidity: Int?,
    isBrioche: Boolean,
) {
    Card(padding = 14.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Formula Results")

            val ehp = result.effectiveHydrationPercent
            if (isBrioche && ehp != null) {
                NoteBox(
                    boldPrefix = "Effective hydration: ${FormulaResultFormatting.formatFixed(ehp, 1)}%",
                    regularSuffix = " — eggs (75% water) and milk contribute liquid. Free water fills the remainder.",
                )
            }

            FlourRows(result)
            SectionDivider()

            if (!suppressWaterRow) {
                WaterRow(result, sweetenerMeta, isBrioche)
                if (rhDirection != HumidityDirection.NEUTRAL && relativeHumidity != null) {
                    HumidityAdvisoryView(isHigh = rhDirection == HumidityDirection.HIGH, rh = relativeHumidity)
                }
            }

            FormulaRow(
                label = "Salt",
                sublabel = "Standard 2%",
                weight = FormulaResultFormatting.formatGrams(result.saltWeight),
                pct = "${FormulaResultFormatting.formatFixed(result.bakerPercentages.salt, 1)}%",
            )

            FormulaRow(
                label = "$yeastLabel Yeast",
                sublabel = when (yeastType) {
                    "instant" -> "Mix directly into flour"
                    "active_dry" -> "Bloom in warm water (105°F) 5–10 min first"
                    else -> "Crumble into flour or dissolve in warm water"
                },
                weight = FormulaResultFormatting.formatGrams(result.yeastWeight * yeastFactor),
                pct = "${FormulaResultFormatting.formatFixed(result.bakerPercentages.yeast * yeastFactor, 2)}%",
            )
            YeastVolumeNote(result.yeastWeight * yeastFactor, yeastType, yeastLabel)

            if (result.fatWeight > 0) {
                FormulaRow(
                    label = "Fat", sublabel = "e.g. olive oil",
                    weight = FormulaResultFormatting.formatGrams(result.fatWeight),
                    pct = "${FormulaResultFormatting.formatFixed(result.bakerPercentages.fat, 1)}%",
                )
            }

            val malt = result.maltWeight
            if (malt != null && malt > 0) {
                FormulaRow(
                    label = "Diastatic Malt Powder",
                    sublabel = "Add with dry ingredients",
                    weight = FormulaResultFormatting.formatGrams(malt),
                    pct = FormulaCalculator.truthy(result.bakerPercentages.malt)?.let { "${FormulaResultFormatting.formatFixed(it, 2)}%" },
                )
            }

            val sweetenerWeight = result.sweetenerWeight
            if (sweetenerWeight != null && sweetenerWeight > 0) {
                FormulaRow(
                    label = sweetenerMeta?.label ?: "Sweetener",
                    sublabel = if ((sweetenerMeta?.waterContent ?: 0.0) > 0)
                        "Liquid — contains ${(sweetenerMeta!!.waterContent * 100).swiftRounded().toInt()}% water; fold in gently"
                    else
                        "Add with dry ingredients",
                    weight = FormulaResultFormatting.formatGrams(sweetenerWeight),
                    pct = FormulaCalculator.truthy(result.bakerPercentages.sweetener)?.let { "${FormulaResultFormatting.formatFixed(it, 1)}%" },
                )
            }

            val eggWeight = result.eggWeight
            if (eggWeight != null && eggWeight > 0) {
                FormulaRow(
                    label = "Eggs",
                    sublabel = result.eggCount?.let { "~${FormulaResultFormatting.formatNumber(it)} large eggs (50g each) — room temp" } ?: "Enriched ingredient",
                    weight = FormulaResultFormatting.formatGrams(eggWeight),
                    pct = result.bakerPercentages.eggs?.let { "${FormulaResultFormatting.formatFixed(it, 1)}%" },
                )
            }

            val milkWeight = result.milkWeight
            if (milkWeight != null && milkWeight > 0) {
                FormulaRow(
                    label = result.dairyDisplayName ?: "Whole Milk",
                    sublabel = "Warm to 68–72°F. Final dough only.",
                    weight = FormulaResultFormatting.formatGrams(milkWeight),
                    pct = result.bakerPercentages.milk?.let { "${FormulaResultFormatting.formatFixed(it, 1)}%" },
                )
            }

            val butterWeight = result.butterWeight
            if (butterWeight != null && butterWeight > 0) {
                FormulaRow(
                    label = "Butter",
                    sublabel = "Cold cubed — add after full gluten development",
                    weight = FormulaResultFormatting.formatGrams(butterWeight),
                    pct = result.bakerPercentages.butter?.let { "${FormulaResultFormatting.formatFixed(it, 1)}%" },
                )
            }

            SectionDivider()
            FormulaRow(label = "Total Dough Weight", weight = FormulaResultFormatting.formatGrams(result.totalDoughWeight), accent = true)

            NoteBox(
                boldPrefix = "Baker's percentages",
                regularSuffix = " — every ingredient expressed as a % of total flour weight. Flour is always 100%.",
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun FlourRows(result: FormulaResult) {
    val breakdown = result.flourBreakdown
    if (breakdown != null && breakdown.isNotEmpty()) {
        FormulaRow(
            label = "Flour (total)", sublabel = "All percentages vs. total flour",
            weight = FormulaResultFormatting.formatGrams(result.flourWeight), pct = "100%",
        )
        breakdown.forEach { fb ->
            val prefermentGrams = fb.prefermentGrams ?: 0.0
            val sublabel = if (prefermentGrams > 0)
                "${FormulaResultFormatting.formatGrams(prefermentGrams)} preferment + ${FormulaResultFormatting.formatGrams(fb.finalDoughGrams)} final"
            else
                "Final mix only"
            FormulaRow(
                label = fb.label, sublabel = sublabel,
                weight = FormulaResultFormatting.formatGrams(fb.grams),
                pct = "${FormulaResultFormatting.formatNumber(fb.percent)}%",
                indent = true,
            )
        }
    } else {
        FormulaRow(
            label = "Flour", sublabel = "100% — anchor weight",
            weight = FormulaResultFormatting.formatGrams(result.flourWeight), pct = "100%",
        )
    }
}

@Composable
private fun WaterRow(result: FormulaResult, sweetenerMeta: FormulaCalculator.SweetenerMeta?, isBrioche: Boolean) {
    val sww = result.sweetenerWaterWeight
    val sublabel = if (sww != null && sww > 0)
        "Free water — ${FormulaResultFormatting.formatGrams(sww)} from ${sweetenerMeta?.label ?: "sweetener"}"
    else if (isBrioche)
        "Free water (after egg & milk liquid)"
    else
        "${FormulaResultFormatting.formatFixed(result.bakerPercentages.water, 1)}%"

    FormulaRow(
        label = "Water", sublabel = sublabel,
        weight = FormulaResultFormatting.formatGrams(result.waterWeight),
        pct = "${FormulaResultFormatting.formatFixed(result.bakerPercentages.water, 1)}%",
    )
}

@Composable
private fun YeastVolumeNote(grams: Double, yeastType: String, yeastLabel: String) {
    val approx = FormulaResultFormatting.yeastVolumeApprox(grams, yeastType) ?: return
    val colors = LocalBreadIQColors.current
    Text(
        text = "Don't have a precision scale? ${FormulaResultFormatting.formatGrams(grams)} of $yeastLabel Yeast ≈ $approx",
        fontSize = 10.sp,
        fontStyle = FontStyle.Italic,
        color = colors.mutedForeground,
        modifier = Modifier.padding(top = 1.dp, bottom = 2.dp),
    )
}

// MARK: - Prep cards (bagel boil / pretzel bath — hardcoded absolute
// constants, not computed values; verified directly against the iOS
// source, which itself cross-checked the mobile calculator's own recipe-
// card text and the byte-identical web `bread-lab` bath description
// strings, with no drift found)

@Composable
private fun BagelBoilCard() {
    val colors = LocalBreadIQColors.current
    Card(padding = 14.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Boil Prep")
            Text(
                text = "Prepare before baking. Bring to a full rolling boil just before the boil step.",
                fontSize = 12.sp, color = colors.mutedForeground,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FormulaRow(label = "Water", sublabel = "1 gallon", weight = "3800g")
            FormulaRow(label = "Barley Malt Syrup", sublabel = "Adds color and malty depth", weight = "60g")
            FormulaRow(label = "Baking Soda", sublabel = "Raises alkalinity for crust set", weight = "12g")
        }
    }
}

private val LyeWarningColor = Color(0xFFC2410C)

@Composable
private fun PretzelLyeCard() {
    Card(padding = 14.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Alkaline Bath — Food-Grade Lye")
            Text(
                text = "⚠ Always add lye to water — never reverse. Wear rubber gloves and eye protection. Prepare before shaping. Lye neutralizes completely during baking.",
                fontSize = 12.sp, color = LyeWarningColor,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FormulaRow(label = "Cold Water", sublabel = "Always add lye to water", weight = "1000g")
            FormulaRow(label = "Food-Grade Lye", sublabel = "30–40g per 1000g water", weight = "30–40g")
        }
    }
}

@Composable
private fun PretzelBakingSodaCard() {
    val colors = LocalBreadIQColors.current
    Card(padding = 14.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Alkaline Bath — Baked Baking Soda")
            Text(
                text = "Bake baking soda on foil-lined sheet at 250°F for 1 hour first — significantly increases alkalinity. Prepare before shaping.",
                fontSize = 12.sp, color = colors.mutedForeground,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FormulaRow(label = "Water", sublabel = "Bring to a boil", weight = "1000g")
            FormulaRow(label = "Baked Baking Soda", sublabel = "Baked 1 hr at 250°F", weight = "50g")
            FormulaRow(label = "Barley Malt Syrup", sublabel = "Adds color and flavor", weight = "20g")
        }
    }
}

// MARK: - Preferment / final mix cards

@Composable
private fun PrefermentCard(preferment: PrefermentResult, yeastType: String, yeastFactor: Double, yeastLabel: String) {
    val colors = LocalBreadIQColors.current
    Card(padding = 14.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Step 1 — ${FormulaResultFormatting.prefermentTypeLabel(preferment.type)} Build")
            Text(
                text = "Mix and ferment 12–16 hours before bake day (levain: 4–8 hours).",
                fontSize = 12.sp, color = colors.mutedForeground,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FormulaRow(label = "Bread Flour", sublabel = "Pre-ferment flour", weight = FormulaResultFormatting.formatGrams(preferment.flourWeight))
            FormulaRow(
                label = "Water",
                sublabel = "${if (preferment.flourWeight > 0) (preferment.waterWeight / preferment.flourWeight * 100).swiftRounded().toInt() else 0}% hydration",
                weight = FormulaResultFormatting.formatGrams(preferment.waterWeight),
            )
            if (preferment.yeastWeight > 0) {
                FormulaRow(label = "$yeastLabel Yeast", sublabel = "Small amount", weight = FormulaResultFormatting.formatGrams(preferment.yeastWeight * yeastFactor))
                YeastVolumeNote(preferment.yeastWeight * yeastFactor, yeastType, yeastLabel)
            }
            if (preferment.saltWeight > 0) {
                FormulaRow(label = "Salt", sublabel = "Included (old dough only)", weight = FormulaResultFormatting.formatGrams(preferment.saltWeight))
            }
            SectionDivider()
            FormulaRow(label = "Total", weight = FormulaResultFormatting.formatGrams(preferment.totalWeight), accent = true)
        }
    }
}

@Composable
private fun FinalMixCard(preferment: PrefermentResult, yeastType: String, yeastFactor: Double, yeastLabel: String) {
    val finalMix = preferment.finalMix
    val colors = LocalBreadIQColors.current
    Card(padding = 14.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Step 2 — Final Mix")
            Text(
                text = "Add everything to the bowl on bake day, including the ripe pre-ferment.",
                fontSize = 12.sp, color = colors.mutedForeground,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FormulaRow(label = "Bread Flour", sublabel = "Remaining flour (rest in pre-ferment)", weight = FormulaResultFormatting.formatGrams(finalMix.flourWeight))
            FormulaRow(label = "Water", sublabel = "Remaining water", weight = FormulaResultFormatting.formatGrams(finalMix.waterWeight))
            FormulaRow(label = "Salt", sublabel = "Add after autolyse", weight = FormulaResultFormatting.formatGrams(finalMix.saltWeight))
            if (finalMix.fatWeight > 0) {
                FormulaRow(label = "Fat", sublabel = "Fold in after gluten develops", weight = FormulaResultFormatting.formatGrams(finalMix.fatWeight))
            }
            if (finalMix.yeastWeight > 0) {
                FormulaRow(label = "$yeastLabel Yeast", sublabel = "Add with other ingredients", weight = FormulaResultFormatting.formatGrams(finalMix.yeastWeight * yeastFactor))
                YeastVolumeNote(finalMix.yeastWeight * yeastFactor, yeastType, yeastLabel)
            }
            SectionDivider()
            FormulaRow(
                label = FormulaResultFormatting.prefermentTypeLabel(preferment.type),
                sublabel = "Add the whole ripe pre-ferment",
                weight = FormulaResultFormatting.formatGrams(finalMix.prefermentWeight),
                accent = true,
            )
        }
    }
}

// MARK: - Shared bits

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalBreadIQColors.current
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = colors.primary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun SectionDivider() {
    val colors = LocalBreadIQColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(colors.border),
    )
}

/**
 * Content sets its own per-segment font weight/color via [buildAnnotatedString]
 * (a semibold lead-in + regular remainder), matching the source's `Text`
 * concatenation of a bold-styled prefix with a plain-styled suffix.
 */
@Composable
private fun NoteBox(boldPrefix: String, regularSuffix: String, modifier: Modifier = Modifier) {
    val colors = LocalBreadIQColors.current
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = colors.foreground)) {
            append(boldPrefix)
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, color = colors.mutedForeground)) {
            append(regularSuffix)
        }
    }
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.muted)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(8.dp),
    )
}

/** Port of `FormulaResult.tsx`'s local `Row` component, renamed to avoid ambiguity with Compose's own `Row`. */
@Composable
private fun FormulaRow(
    label: String,
    weight: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    pct: String? = null,
    accent: Boolean = false,
    indent: Boolean = false,
) {
    val colors = LocalBreadIQColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (indent) 12.dp else 0.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (accent) colors.primary else colors.foreground)
            if (sublabel != null) {
                Text(sublabel, fontSize = 10.sp, color = colors.mutedForeground)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(weight, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (accent) colors.primary else colors.foreground)
            if (pct != null) {
                Text(pct, fontSize = 10.sp, color = colors.mutedForeground)
            }
        }
    }
}

/**
 * Port of `FormulaResult.tsx`'s local `HumidityAdvisory` component.
 * [isHigh] is a plain `Boolean`, not a 3-state direction — the only call
 * site already gates on "not neutral" before rendering this at all, so a
 * 2-state boolean avoids an unreachable branch here.
 */
@Composable
private fun HumidityAdvisoryView(isHigh: Boolean, rh: Int) {
    var expanded by remember { mutableStateOf(false) }

    val background = Color(0xFFFFF7ED)
    val border = Color(0xFFFED7AA)
    val headlineColor = Color(0xFFC2410C)
    val detailColor = Color(0xFF9A3412)

    val headline = if (isHigh)
        "High humidity detected ($rh% RH) — water weight reduced"
    else
        "Low humidity detected ($rh% RH) — water weight increased"

    val detail = if (isHigh)
        "High humidity means your flour is already carrying moisture from the air. BreadIQ has reduced your water weight accordingly. Your baker's percentages reflect the intended formula — your gram weights reflect your actual environment."
    else
        "Low humidity means your flour is drier than usual. BreadIQ has increased your water weight accordingly. Your baker's percentages reflect the intended formula — your gram weights reflect your actual environment."

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = headlineColor, modifier = Modifier.size(12.dp).padding(top = 1.dp))
            Text(headline, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = headlineColor, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null, tint = headlineColor, modifier = Modifier.size(12.dp),
            )
        }
        if (expanded) {
            Text(detail, fontSize = 11.sp, color = detailColor)
        }
    }
}
