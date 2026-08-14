package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BakerPercentages
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.FlourBreakdownEntry
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.model.PrefermentFinalMix
import com.BreadIQ.myapp.model.PrefermentResult
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Swift's `Double.rounded()` default mode is round-half-away-from-zero,
 * not `kotlin.math.round()`'s round-half-to-even ("banker's rounding") —
 * every domain value these calculators round (money, percentages,
 * durations) is non-negative, so the two only disagree on exact `.5`
 * boundaries, but this is implemented for the general case anyway rather
 * than relying on that. Used everywhere the iOS source calls `.rounded()`.
 */
internal fun Double.swiftRounded(): Double = if (this >= 0) floor(this + 0.5) else ceil(this - 0.5)

/**
 * Ported from the iOS app's `Core/FormulaCalculator.swift`.
 *
 * Input parameters for [FormulaCalculator.calculate]. Mirrors
 * `calcFormula()`'s destructured input type exactly
 * (`api-server/src/routes/calculator.ts`, per the iOS port's own
 * line-by-line verification against that function signature), with one
 * deliberate omission carried over from that port: `targetDoughWeight`
 * is validated by the wider request schema but never actually read by
 * `calcFormula()` — a dead field on the wire, left out here rather than
 * ported as a no-op.
 */
data class FormulaInput(
    val loafStyle: String,
    val numLoaves: Int,
    val hydrationPercent: Double,
    val fatPercent: Double,
    val saltPercent: Double? = null,
    val yeastPercent: Double,
    val usePrefermant: Boolean,
    val prefermentType: String? = null,
    val prefermentFlourPercent: Double? = null,
    val prefermentHydration: Double? = null,
    val diastaticMaltPercent: Double? = null,
    val flourBlend: List<FlourBlendEntry>? = null,
    val sweetenerType: String? = null,
    val sweetenerPercent: Double? = null,
    /** Multiplies the per-loaf-style base flour weight before the 3% batch-loss buffer. `null` behaves as `1.0` (no change). */
    val sizeModifier: Double? = null,
    /** `null` means "no humidity adjustment" — there is no separate on/off flag; the caller simply decides whether to pass this field at all. */
    val relativeHumidity: Int? = null,
    val eggsPercent: Double? = null,
    val milkPercent: Double? = null,
    /** See [FormulaResult.dairyDisplayName]'s own doc comment — pure pass-through, never read by any calculation below. */
    val dairyDisplayName: String? = null,
    val butterPercent: Double? = null,
    val yeastType: String? = null,
)

/**
 * Port of `calcFormula()` (`api-server/src/routes/calculator.ts`), the
 * server's canonical baker's-percentage formula engine, via the iOS
 * port's own line-by-line verification against that source. A plain
 * object namespace (not a class with instance state) — this is pure
 * static logic, matching the source's free function.
 */
object FormulaCalculator {

    const val defaultSaltPercent: Double = 2.0

    data class SweetenerMeta(val label: String, val waterContent: Double)

    /** `SWEETENER_META` — 4 entries. */
    val sweetenerMeta: Map<String, SweetenerMeta> = mapOf(
        "granulated_sugar" to SweetenerMeta("Granulated Sugar", 0.0),
        "honey" to SweetenerMeta("Honey", 0.17),
        "barley_malt" to SweetenerMeta("Barley Malt Syrup", 0.25),
        "molasses" to SweetenerMeta("Molasses", 0.22),
    )

    /**
     * `FLOUR_META` labels — only `label` is used inside `calcFormula()`
     * (`absorptionAdj` belongs to client-side UI logic, never read by
     * this function).
     *
     * Only 7 entries. The request schema's `flourBlend.type` enum allows
     * an 8th value, `"all_purpose"`, with no entry here — a genuine,
     * pre-existing gap in the source, not a porting mistake. The source
     * falls back to the raw type string as its own label; [flourLabel]
     * replicates that exact fallback.
     */
    val flourLabels: Map<String, String> = mapOf(
        "bread" to "Bread Flour",
        "00" to "00 Flour",
        "semolina" to "Semolina (Fine Durum)",
        "whole_wheat" to "Whole Wheat",
        "rye" to "Dark Rye",
        "spelt" to "Spelt",
        "einkorn" to "Einkorn",
    )

    fun flourLabel(type: String): String = flourLabels[type] ?: type

    /**
     * `LOAF_FLOUR_WEIGHTS` — 49 entries, transcribed exactly.
     *
     * Deliberately kept independent from `LoafShapeCatalog` (46 entries)
     * rather than merged/derived from it. This table has 3 keys the
     * catalog doesn't: `pullman`, `rolls`, `sheet_pan` — the source's own
     * comment marks `rolls` "legacy key — kept for saved-recipe compat,"
     * i.e. old recipes can still reference shapes no longer offered in
     * the active picker UI. Reconciling the two tables would silently
     * break that backward-compat guarantee for any imported/older
     * recipe, so the duality is preserved as-is.
     */
    val loafFlourWeights: Map<String, Double> = mapOf(
        "baguette_6" to 84.0, "baguette_12" to 179.0, "baguette_15" to 254.0, "baguette_18" to 343.0,
        "baguette_rolls_15oz" to 329.0, "baguette_rolls_25oz" to 549.0,
        "boule" to 373.0, "boule_8_oval" to 259.0, "boule_10_round" to 546.0, "boule_10_oval" to 423.0,
        "batard" to 375.0,
        "pullman" to 510.0, "pullman_country" to 477.0,
        "rolls" to 300.0,
        "artisan_rolls_15oz" to 314.0, "artisan_rolls_25oz" to 525.0,
        "ciabatta_loaf" to 175.0, "ciabatta_panino_6" to 489.0,
        "ciabatta_rolls_15oz" to 297.0, "ciabatta_rolls_25oz" to 495.0,
        "focaccia_10x15" to 509.0, "focaccia_13x18" to 794.0, "focaccia_9x13" to 397.0, "focaccia_9round" to 240.0,
        "sheet_pan" to 450.0,
        "pizza_8in_ny" to 77.0, "pizza_12in_ny" to 169.0, "pizza_16in_ny" to 297.0,
        "pizza_8in_neo" to 72.0, "pizza_12in_neo" to 158.0, "pizza_16in_neo" to 277.0,
        "pullman_soft" to 510.0,
        "soft_rolls_15oz" to 314.0, "soft_rolls_25oz" to 523.0,
        "soft_hoagie_6in" to 436.0, "soft_hoagie_8in" to 579.0, "soft_burger_bun" to 339.0,
        "brioche_loaf_9in" to 400.0, "brioche_rolls_15oz" to 217.0, "brioche_rolls_25oz" to 362.0,
        "brioche_burger_bun_4oz" to 301.0,
        "bagel_std_6pk" to 430.0, "bagel_mini_6pk" to 206.0,
        "em_std_12pk" to 587.0, "em_large_12pk" to 803.0,
        "pretzel_std_6pk" to 664.0, "pretzel_small_6pk" to 323.0, "pretzel_sticks_6pk" to 152.0,
        "pretzel_slider_6pk" to 171.0,
    )

    // Helpers

    /** `r1()` — round to 1 decimal place. */
    fun r1(n: Double): Double = (n * 10).swiftRounded() / 10

    /**
     * Mirrors JavaScript truthiness for an optional number: `null` and
     * `0` are both "falsy." A handful of the source's `x ? f(x) : null`
     * ternaries hinge on this — e.g. `diastaticMaltPercent: 0` produces
     * `maltWeight: null`, not `0`.
     *
     * NOT used for `bakerPercentages.malt`/`.sweetener` in [calculate] —
     * those two use the source's plain nil-coalescing (zero preserved),
     * a different operator from the truthy ternaries used everywhere
     * else.
     */
    fun truthy(n: Double?): Double? {
        if (n == null || n == 0.0) return null
        return n
    }

    /** Mirrors JS truthiness for an optional string: `null` and `""` are both falsy. */
    fun truthy(s: String?): String? {
        if (s.isNullOrEmpty()) return null
        return s
    }

    /**
     * `interpolateHumidity()` — piecewise-linear interpolation over a
     * sorted `(x, y)` table, clamped at both ends. (Every table literal
     * in this file and [ProofTimeCalculator] is already ascending,
     * matching the source's inputs, so the source's defensive re-sort
     * has no observable effect here either.)
     */
    fun interpolateHumidity(rh: Double, table: List<Pair<Double, Double>>): Double {
        if (rh <= table[0].first) return table[0].second
        if (rh >= table[table.size - 1].first) return table[table.size - 1].second
        for (i in 0 until table.size - 1) {
            val (x0, y0) = table[i]
            val (x1, y1) = table[i + 1]
            if (rh in x0..x1) {
                val t = (rh - x0) / (x1 - x0)
                return y0 + t * (y1 - y0)
            }
        }
        return table[table.size - 1].second
    }

    /**
     * `humidityWaterFactor()` — multiplier applied to the free-water
     * weight. `null` RH (not provided) is neutral, same as the 36-64% band.
     */
    fun humidityWaterFactor(rh: Int?): Double {
        val rhD = rh?.toDouble() ?: return 1.0
        if (rhD >= 65) {
            val table = listOf(
                65.0 to 0.010, 70.0 to 0.020, 75.0 to 0.035, 80.0 to 0.050,
                85.0 to 0.065, 90.0 to 0.080, 95.0 to 0.095, 100.0 to 0.110,
            )
            return 1.0 - interpolateHumidity(rhD, table)
        }
        if (rhD <= 35) {
            val table = listOf(
                5.0 to 0.075, 10.0 to 0.060, 15.0 to 0.050, 20.0 to 0.040,
                25.0 to 0.030, 30.0 to 0.020, 35.0 to 0.010,
            )
            return 1.0 + interpolateHumidity(rhD, table)
        }
        return 1.0
    }

    // Main calculation

    /** Port of `calcFormula()`'s full body, in source order. */
    fun calculate(input: FormulaInput): FormulaResult {
        val saltPercent = input.saltPercent ?: defaultSaltPercent

        // Flour weight: anchor from the per-loaf-shape table (falling
        // back to 300g for an unrecognized style), times an optional
        // size modifier, times loaf count, plus a 3% buffer for
        // batch/mixing loss.
        val rawFlourPerLoaf = (loafFlourWeights[input.loafStyle] ?: 300.0) * (input.sizeModifier ?: 1.0)
        val flourWeight = r1(rawFlourPerLoaf * input.numLoaves * 1.03)

        // Sweetener: BOTH sweetenerType and sweetenerPercent must be
        // truthy (source: `sweetenerType && sweetenerPercent ? ... :
        // null`), not merely non-null.
        val sweetenerTypeTruthy = truthy(input.sweetenerType)
        val meta = sweetenerTypeTruthy?.let { sweetenerMeta[it] }
        val sweetenerWeight: Double? = if (sweetenerTypeTruthy != null && truthy(input.sweetenerPercent) != null)
            r1(flourWeight * input.sweetenerPercent!! / 100)
        else null
        val sweetenerWaterContrib: Double = if (truthy(sweetenerWeight) != null && meta != null)
            r1(sweetenerWeight!! * meta.waterContent)
        else 0.0

        // Eggs / milk / butter: explicit `> 0` numeric comparisons in
        // the source, not truthy checks on the input percent, so no
        // 0-vs-null ambiguity here.
        val eggsP = input.eggsPercent ?: 0.0
        val milkP = input.milkPercent ?: 0.0
        val butterP = input.butterPercent ?: 0.0

        val eggWeight: Double? = if (eggsP > 0) r1(flourWeight * eggsP / 100) else null
        val milkWeight: Double? = if (milkP > 0) r1(flourWeight * milkP / 100) else null
        // Butter is a separate enrichment fat from the base `fatPercent`
        // below (e.g. brioche adds butter on top of a base fat/oil
        // percentage) — kept fully independent, matching the source.
        val butterWeight: Double? = if (butterP > 0) r1(flourWeight * butterP / 100) else null

        // Liquid contributions from eggs (75%) and milk (87% — whole
        // milk water content) reduce free water.
        val eggHydration = eggWeight?.let { r1(it * 0.75) } ?: 0.0
        val milkHydration = milkWeight?.let { r1(it * 0.87) } ?: 0.0

        // Fresh yeast water contribution: fresh yeast is ~70% water by
        // weight, used at 3x the instant-equivalent weight, so the
        // water contribution = instantYeastWeight × 3.0 × 0.70.
        val freshYeastWaterContrib = if (input.yeastType == "fresh")
            r1(flourWeight * input.yeastPercent / 100 * 3.0 * 0.70)
        else 0.0

        // Free water = target hydration water minus egg/milk/sweetener/
        // fresh-yeast liquid contributions, rounded, THEN clamped to 0
        // (order matters: the source rounds before clamping).
        val targetWaterRaw = r1(
            flourWeight * input.hydrationPercent / 100 -
                eggHydration - milkHydration - sweetenerWaterContrib - freshYeastWaterContrib
        )
        val baseWaterWeight = max(0.0, targetWaterRaw)

        // Humidity adjustment silently modifies free water without
        // changing the displayed baker's percentage.
        val waterWeight = r1(baseWaterWeight * humidityWaterFactor(input.relativeHumidity))
        val fatWeight = r1(flourWeight * input.fatPercent / 100)
        val saltWeight = r1(flourWeight * saltPercent / 100)
        val yeastWeight = r1(flourWeight * input.yeastPercent / 100)
        val maltWeight: Double? = truthy(input.diastaticMaltPercent)?.let { r1(flourWeight * it / 100) }

        // Effective hydration only applies (non-null) when eggs or milk
        // are present — plain water-only doughs report null here.
        val hasEnrichedLiquids = eggsP > 0 || milkP > 0
        val effectiveHydrationPercent: Double? = if (hasEnrichedLiquids)
            r1((waterWeight + eggHydration + milkHydration + sweetenerWaterContrib) / flourWeight * 100)
        else null

        // Approximate egg count (50g per large egg).
        val eggCount: Double? = eggWeight?.let { r1(it / 50) }

        val totalDoughWeight = r1(
            flourWeight + waterWeight + fatWeight + saltWeight + yeastWeight +
                (maltWeight ?: 0.0) + (sweetenerWeight ?: 0.0) +
                (eggWeight ?: 0.0) + (milkWeight ?: 0.0) + (butterWeight ?: 0.0)
        )

        // Flour breakdown — per-flour gram weights for blended
        // formulas. Preferment rule: bread flour (or 00) absorbs
        // preferment first; specialty flours go into the final dough
        // only.
        val normalizedBlend: List<FlourBlendEntry> = if (!input.flourBlend.isNullOrEmpty())
            input.flourBlend
        else listOf(FlourBlendEntry(type = "bread", percent = 100.0))
        val isMultiFlour = normalizedBlend.size > 1

        // Preferment flour weight (bread-flour-only for preferments).
        var pfFlour = 0.0
        var pfWater = 0.0
        var pfSalt = 0.0
        var pfYeast = 0.0
        var preferment: PrefermentResult? = null

        val prefermentType = truthy(input.prefermentType)
        val prefermentFlourPercent = truthy(input.prefermentFlourPercent)
        val prefermentHydration = truthy(input.prefermentHydration)
        if (input.usePrefermant && prefermentType != null && prefermentFlourPercent != null && prefermentHydration != null) {
            pfFlour = r1(flourWeight * prefermentFlourPercent / 100)
            pfWater = r1(pfFlour * prefermentHydration / 100)

            when (prefermentType) {
                "biga" -> {
                    pfYeast = r1(pfFlour * 0.001)
                    pfSalt = 0.0
                }
                "levain" -> {
                    pfYeast = 0.0
                    pfSalt = 0.0
                }
                else -> { // "poolish"
                    pfYeast = r1(pfFlour * 0.001)
                    pfSalt = 0.0
                }
            }

            val pfTotal = r1(pfFlour + pfWater + pfYeast + pfSalt)

            val remainingFlour = r1(flourWeight - pfFlour)
            val remainingWater = max(0.0, r1(waterWeight - pfWater))
            val remainingSalt = r1(saltWeight - pfSalt)
            val remainingYeast = r1(yeastWeight - pfYeast)

            val finalMix = PrefermentFinalMix(
                flourWeight = remainingFlour,
                waterWeight = remainingWater,
                saltWeight = remainingSalt,
                fatWeight = fatWeight,
                yeastWeight = remainingYeast,
                prefermentWeight = pfTotal,
                sweetenerWeight = sweetenerWeight,
                eggWeight = eggWeight,
                milkWeight = milkWeight,
                butterWeight = butterWeight,
            )

            preferment = PrefermentResult(
                type = prefermentType,
                flourWeight = pfFlour,
                waterWeight = pfWater,
                saltWeight = pfSalt,
                yeastWeight = pfYeast,
                totalWeight = pfTotal,
                finalMix = finalMix,
            )
        }

        // Single pass over the blend IN ITS ORIGINAL ORDER — the source
        // does not sort bread/00 to the front, it just prefers them
        // when encountered while preferment flour remains unallocated.
        var remainingPfFlour = pfFlour
        val flourBreakdown = mutableListOf<FlourBreakdownEntry>()
        for (entry in normalizedBlend) {
            val grams = r1(flourWeight * entry.percent / 100)
            var prefermentGrams: Double? = null

            if (pfFlour > 0) {
                val prefersBreadFirst = entry.type == "bread" || entry.type == "00"
                if (prefersBreadFirst && remainingPfFlour > 0) {
                    prefermentGrams = r1(min(grams, remainingPfFlour))
                    remainingPfFlour = r1(max(0.0, remainingPfFlour - grams))
                } else {
                    prefermentGrams = null
                }
            }

            val finalDoughGrams = r1(grams - (prefermentGrams ?: 0.0))
            flourBreakdown.add(
                FlourBreakdownEntry(
                    type = entry.type,
                    label = flourLabel(entry.type),
                    percent = entry.percent,
                    grams = grams,
                    prefermentGrams = prefermentGrams,
                    finalDoughGrams = finalDoughGrams,
                )
            )
        }

        val bakerPercentages = BakerPercentages(
            water = input.hydrationPercent,
            salt = saltPercent,
            fat = input.fatPercent,
            yeast = input.yeastPercent,
            // `malt`/`sweetener` use plain nil-coalescing in the source
            // (an explicit 0 is preserved, not converted to null) — NOT
            // the truthy ternary used for the top-level `maltWeight`/
            // `sweetenerWeight` fields above.
            malt = input.diastaticMaltPercent,
            sweetener = input.sweetenerPercent,
            eggs = if (eggsP > 0) eggsP else null,
            milk = if (milkP > 0) milkP else null,
            butter = if (butterP > 0) butterP else null,
        )

        return FormulaResult(
            flourWeight = flourWeight,
            waterWeight = waterWeight,
            saltWeight = saltWeight,
            fatWeight = fatWeight,
            yeastWeight = yeastWeight,
            maltWeight = maltWeight,
            sweetenerWeight = sweetenerWeight,
            sweetenerWaterWeight = if (sweetenerWaterContrib > 0) r1(sweetenerWaterContrib) else null,
            eggWeight = eggWeight,
            eggCount = eggCount,
            milkWeight = milkWeight,
            butterWeight = butterWeight,
            dairyDisplayName = input.dairyDisplayName,
            effectiveHydrationPercent = effectiveHydrationPercent,
            totalDoughWeight = totalDoughWeight,
            flourBreakdown = if (isMultiFlour) flourBreakdown else null,
            bakerPercentages = bakerPercentages,
            preferment = preferment,
        )
    }
}
