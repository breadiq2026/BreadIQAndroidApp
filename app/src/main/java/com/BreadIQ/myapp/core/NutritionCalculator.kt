package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.model.NutritionInfoCatalog

/**
 * Ported from the iOS app's `Core/NutritionCalculator.swift`.
 *
 * Estimated bake-loss (moisture loss during baking) by bread style,
 * expressed as a fraction of raw dough weight — e.g. `0.18` means an
 * 865g raw dough loaf is estimated to finish baked at
 * `865 * (1 - 0.18) = 709g`.
 *
 * **New — not a port of anything in the original Expo app.** No
 * source-app equivalent exists (that app has no nutrition feature at
 * all). Built on the iOS side per direct instruction alongside
 * `NutritionCalculator`, following that project's own explicit
 * architectural guidance on the point:
 *
 * - **Calories/macros don't disappear during baking — water does.**
 *   [NutritionCalculator.calcBatchTotals] sums nutrients from raw
 *   ingredient weights (a fixed, bake-invariant total), then
 *   [NutritionCalculator.calcBatchNutrition] uses THIS catalog only to
 *   estimate the finished loaf's WEIGHT, which is what changes nutrient
 *   *concentration* (kcal/protein/etc. per gram) — never the totals
 *   themselves.
 * - **No "weigh your finished loaf" input anywhere, by direct
 *   instruction on the iOS side** — despite this being a real,
 *   commonly-suggested way to get a more precise per-user/per-oven
 *   bake-loss ratio. Explicitly rejected there as putting too much
 *   burden on a user who "probably just wants a reasonably accurate
 *   estimate at a glance." These are style-level DEFAULTS only, not
 *   user-correctable inputs (yet).
 *
 * **Values are estimates, not measured BreadIQ data**, assembled from
 * well-established commercial-baking bake-loss ranges (lean hearth
 * breads run ~12–20% given their high crust-to-crumb ratio and long,
 * hot bakes; enriched pan breads run ~8–12%, since fat/sugar/milk solids
 * slow evaporation; stiff/low-hydration doughs like bagels run lower
 * still since there's less free water to begin with; short, very-hot
 * bakes like pizza lose comparatively little water despite the high
 * temperature, simply because so little TIME elapses). One number per
 * style (not a range), chosen at the middle-to-lower end of each style's
 * typical range rather than the high end, to avoid systematically
 * overstating nutrient density.
 */
object BakeLossCatalog {
    /**
     * Bread-style value (matches `BreadStyleDef.value`) -> bake-loss
     * fraction. Every one of `BreadStyleCatalog.all`'s 12 styles has an
     * explicit entry — [lossFraction]'s fallback below exists only as a
     * defensive default for an unrecognized style value, not because any
     * real style is missing here.
     */
    val defaults: Map<String, Double> = mapOf(
        "baguette" to 0.18,
        "artisan" to 0.14,
        "country" to 0.14,
        "ciabatta" to 0.16,
        "focaccia" to 0.12,
        "pizza_ny" to 0.08,
        "pizza_neo" to 0.06,
        "soft_roll" to 0.10,
        "brioche" to 0.10,
        "bagel" to 0.08,
        "english_muffin" to 0.10,
        "pretzel" to 0.10,
    )

    /**
     * Falls back to `0.13` (a lean-bread-ish middle default) for any
     * style value not in [defaults] — defensive only; every real style
     * in `BreadStyleCatalog.all` has its own explicit entry above.
     */
    fun lossFraction(forStyle: String): Double = defaults[forStyle] ?: 0.13
}

/**
 * One set of macro-nutrient figures — either a whole batch's totals, or
 * those totals scaled down to a smaller basis (100g baked, one piece,
 * one USDA reference serving). Mirrors `NutritionInfo`'s 6 macro fields
 * exactly, minus the USDA/labeling metadata those don't need at this level.
 */
data class NutrientTotals(
    val caloriesKcal: Double,
    val carbohydratesG: Double,
    val fiberG: Double,
    val proteinG: Double,
    val totalFatG: Double,
    val saturatedFatG: Double,
) {
    operator fun plus(other: NutrientTotals): NutrientTotals = NutrientTotals(
        caloriesKcal = caloriesKcal + other.caloriesKcal,
        carbohydratesG = carbohydratesG + other.carbohydratesG,
        fiberG = fiberG + other.fiberG,
        proteinG = proteinG + other.proteinG,
        totalFatG = totalFatG + other.totalFatG,
        saturatedFatG = saturatedFatG + other.saturatedFatG,
    )

    /**
     * Scales every field by [factor] — used both to go from "per 100g of
     * an ingredient" to "for however many grams of it are in this batch"
     * (factor = grams/100), and from "whole-batch totals" down to a
     * smaller basis like one piece or one USDA serving.
     */
    fun scaled(by: Double): NutrientTotals = NutrientTotals(
        caloriesKcal = caloriesKcal * by,
        carbohydratesG = carbohydratesG * by,
        fiberG = fiberG * by,
        proteinG = proteinG * by,
        totalFatG = totalFatG * by,
        saturatedFatG = saturatedFatG * by,
    )

    companion object {
        val zero = NutrientTotals(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Full result of [NutritionCalculator.calcBatchNutrition] — whole-batch
 * totals (bake-invariant) plus the derived per-100g-baked and
 * per-serving breakdowns.
 */
data class BatchNutrition(
    val rawDoughWeight: Double,
    val bakeLossPercent: Double,
    val finishedWeight: Double,
    /**
     * Whole-batch totals. **Does not change with bake loss** — see
     * [BakeLossCatalog]'s own doc comment: baking concentrates nutrients
     * per gram by driving off water, it doesn't change the absolute
     * amount of any nutrient in the loaf.
     */
    val totals: NutrientTotals,
    val per100gBaked: NutrientTotals,
    /**
     * Set when the selected shape is multi-piece (rolls, buns, bagels,
     * English muffins, pretzels — anything [CostEstimator.piecesPerUnit]
     * resolves above 1). `null` for single large-format loaves.
     */
    val perPiece: NutrientTotals? = null,
    /**
     * Set when the selected shape is a single large-format loaf
     * (baguette, boule, batard, sandwich loaf, etc. — anything
     * [CostEstimator.piecesPerUnit] resolves to exactly 1), scaled to
     * the FDA's Reference Amount Customarily Consumed (RACC) for bread,
     * 50g (21 CFR 101.12(b), Table 2) — not a slice, since neither this
     * app nor the user can know how thick any given slice actually is.
     * `null` for multi-piece shapes (those use [perPiece] instead).
     */
    val perUSDAServing: NutrientTotals? = null,
)

/**
 * New, not a port — see [BakeLossCatalog]'s doc comment for the full
 * "why this doesn't exist in the source app" context. Deliberately
 * mirrors [CostEstimator]'s architecture (a pure function taking a
 * [FormulaResult] and deriving a per-batch/per-piece figure from it,
 * reusing [CostEstimator.piecesPerUnit] rather than re-deriving piece
 * counts a second time) since both are the same shape of problem.
 */
object NutritionCalculator {
    /**
     * FDA Reference Amount Customarily Consumed for bread, 21 CFR
     * 101.12(b) Table 2 (the regulation's own worked example is
     * literally "2 slices (50g)"). Used as the serving basis for single
     * large-format loaves, where BreadIQ has no reliable way to know how
     * many slices a user will actually cut or how thick each one is.
     */
    const val usdaBreadServingGrams: Double = 50.0

    /**
     * Sweetener-type -> `NutritionInfoCatalog` key remap — identical
     * shape to `CostEstimator.sweetenerPriceKey`, needed for the same
     * reason: `"granulated_sugar"` isn't a catalog key on its own, it
     * resolves via `"sugar"`. The other three sweetener types map to
     * themselves.
     */
    val sweetenerNutritionKey: Map<String, String> = mapOf(
        "granulated_sugar" to "sugar",
        "honey" to "honey",
        "barley_malt" to "barley_malt",
        "molasses" to "molasses",
    )

    /**
     * `dairyDisplayName` -> `NutritionInfoCatalog` key. `null` (the
     * ordinary case — plain water dough, or milk with no identified
     * source) and any unrecognized value both resolve to `"milk"` (whole
     * milk), matching every other `dairyDisplayName` consumer's existing
     * "unrecognized/null falls back to the default wording" convention.
     */
    private fun milkNutritionKey(dairyDisplayName: String?): String = when (dairyDisplayName) {
        "Buttermilk" -> "buttermilk"
        "Heavy Cream" -> "heavy_cream"
        else -> "milk"
    }

    /**
     * Looks up one ingredient's `NutritionInfoCatalog` entry and scales
     * its per-100g figures to [grams] of it. `0` grams or an unknown key
     * both resolve to [NutrientTotals.zero], not a crash — same
     * defensive shape as `CostEstimator.price` resolving an unknown key
     * to `0`.
     */
    private fun nutrients(forKey: String, grams: Double): NutrientTotals {
        val info = if (grams > 0) NutritionInfoCatalog.info(forKey = forKey) else null
        info ?: return NutrientTotals.zero
        val perGram = NutrientTotals(
            caloriesKcal = info.caloriesKcal, carbohydratesG = info.carbohydratesG, fiberG = info.fiberG,
            proteinG = info.proteinG, totalFatG = info.totalFatG, saturatedFatG = info.saturatedFatG,
        )
        return perGram.scaled(by = grams / 100.0)
    }

    /**
     * The whole-batch nutrient totals — bake-invariant, computed purely
     * from real ingredient gram amounts on [result]. Mirrors
     * `CostEstimator.calcBatchCost`'s own ingredient-by-ingredient sum
     * almost line for line, substituting `NutritionInfoCatalog` lookups
     * for price lookups.
     */
    fun calcBatchTotals(
        result: FormulaResult,
        yeastType: String,
        sweetenerType: String?,
        blend: List<FlourBlendEntry>,
    ): NutrientTotals {
        var total = NutrientTotals.zero

        for (entry in blend) {
            total += nutrients(forKey = entry.type, grams = result.flourWeight * (entry.percent / 100))
        }

        total += nutrients(forKey = "water", grams = result.waterWeight)
        total += nutrients(forKey = "fat", grams = result.fatWeight)
        total += nutrients(forKey = "salt", grams = result.saltWeight)
        total += nutrients(forKey = yeastType.ifEmpty { "instant" }, grams = result.yeastWeight)
        total += nutrients(forKey = "diastatic_malt", grams = result.maltWeight ?: 0.0)

        val sweetenerTypeTruthy = FormulaCalculator.truthy(sweetenerType)
        val sweetKey = if (sweetenerTypeTruthy != null) sweetenerNutritionKey[sweetenerTypeTruthy] ?: sweetenerTypeTruthy else "sugar"
        total += nutrients(forKey = sweetKey, grams = result.sweetenerWeight ?: 0.0)

        total += nutrients(forKey = "eggs", grams = result.eggWeight ?: 0.0)
        total += nutrients(forKey = milkNutritionKey(result.dairyDisplayName), grams = result.milkWeight ?: 0.0)
        total += nutrients(forKey = "butter", grams = result.butterWeight ?: 0.0)

        return total
    }

    /**
     * Full analysis: batch totals, estimated finished (baked) weight via
     * [BakeLossCatalog], and the per-100g-baked / per-piece /
     * per-USDA-serving breakdowns. `shapeValue`/`numLoaves` determine
     * which of `perPiece`/`perUSDAServing` gets set, via the exact same
     * `CostEstimator.piecesPerUnit` the Cost Analysis card already uses —
     * a shape resolving to more than 1 piece uses `perPiece`; a shape
     * resolving to exactly 1 (a single large-format loaf) uses
     * `perUSDAServing` instead, since "per each" makes no sense for a
     * whole baguette or boule.
     */
    fun calcBatchNutrition(
        result: FormulaResult,
        style: String,
        yeastType: String,
        sweetenerType: String?,
        blend: List<FlourBlendEntry>,
        shapeValue: String,
        numLoaves: Int,
    ): BatchNutrition {
        val totals = calcBatchTotals(result = result, yeastType = yeastType, sweetenerType = sweetenerType, blend = blend)

        val rawDoughWeight = result.totalDoughWeight
        val bakeLoss = BakeLossCatalog.lossFraction(forStyle = style)
        val finishedWeight = rawDoughWeight * (1 - bakeLoss)

        val per100gBaked = if (finishedWeight > 0) totals.scaled(by = 100.0 / finishedWeight) else NutrientTotals.zero

        val piecesPerLoaf = CostEstimator.piecesPerUnit(forShape = shapeValue)
        val totalPieces = piecesPerLoaf * numLoaves

        var perPiece: NutrientTotals? = null
        var perUSDAServing: NutrientTotals? = null
        if (piecesPerLoaf > 1) {
            perPiece = if (totalPieces > 0) totals.scaled(by = 1.0 / totalPieces) else NutrientTotals.zero
        } else {
            perUSDAServing = if (finishedWeight > 0) totals.scaled(by = usdaBreadServingGrams / finishedWeight) else NutrientTotals.zero
        }

        return BatchNutrition(
            rawDoughWeight = rawDoughWeight, bakeLossPercent = bakeLoss, finishedWeight = finishedWeight,
            totals = totals, per100gBaked = per100gBaked, perPiece = perPiece, perUSDAServing = perUSDAServing,
        )
    }
}
