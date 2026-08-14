package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.model.IngredientReferencePriceCatalog

data class BatchCost(
    val flourCost: Double,
    val fatCost: Double,
    val saltCost: Double,
    val yeastCost: Double,
    val sweetenerCost: Double,
    val maltCost: Double,
    val eggsCost: Double,
    val milkCost: Double,
    val butterCost: Double,
    val total: Double,
)

/**
 * Ported from the iOS app's `Core/CostEstimator.swift`.
 *
 * Port of `calcBatchCost()` and `getPiecesPerUnit()`
 * (`breadiq-mobile/app/(tabs)/calculator.tsx`) — the Cost Analysis
 * card's math. Both take a [FormulaResult] directly (step 1's model
 * already has every field the source's loosely-typed anonymous
 * parameter object names) rather than re-declaring a redundant input type.
 *
 * **`waterWeight` is on `FormulaResult` but never read by
 * [calcBatchCost]** — declared in the source's own parameter type but
 * absent from the function body, the same dead-field pattern found in
 * `FormulaCalculator`/`ProofTimeCalculator`'s own inputs (`targetDoughWeight`,
 * `flourWeight`). Makes sense here specifically: water is essentially
 * free, so the source simply never costs it.
 */
object CostEstimator {

    /**
     * `INGREDIENT_REF_PRICES`. Derived from `IngredientReferencePriceCatalog.all`
     * (step 1) rather than duplicating it as an independent literal — see
     * that catalog's own doc comment for the consolidation writeup this
     * mirrors from the iOS port (its own `ingredientReferencePrices` was
     * merged with a second, independently-maintained 22-entry table by
     * direct instruction; this port only ever had the one, so no
     * separate merge was needed here).
     */
    val ingredientReferencePrices: Map<String, Double> =
        IngredientReferencePriceCatalog.all.associate { it.key to it.pricePerGram }

    /** `SWEETENER_PRICE_KEY`. */
    val sweetenerPriceKey: Map<String, String> = mapOf(
        "granulated_sugar" to "sugar",
        "honey" to "honey",
        "barley_malt" to "barley_malt",
        "molasses" to "molasses",
    )

    /**
     * `p()`, the local price-resolution closure defined inside
     * `calcBatchCost` in the source — hoisted out here (matching the iOS
     * port's own choice) since it's independently useful/testable.
     */
    fun price(key: String, customPrices: Map<String, Double>, refPrices: Map<String, Double>): Double =
        customPrices[key] ?: refPrices[key] ?: ingredientReferencePrices[key] ?: 0.0

    /**
     * `calcBatchCost()`. `customPrices`/`refPrices` default to empty,
     * matching the source's own default parameters. Tier-gating which
     * prices actually get passed in (the source only passes real
     * `customPrices` for premium-tier users) is a screen-level business
     * rule, not part of this pure calculation — left to whichever screen
     * calls this.
     */
    fun calcBatchCost(
        result: FormulaResult,
        yeastType: String,
        sweetenerType: String?,
        blend: List<FlourBlendEntry>,
        customPrices: Map<String, Double> = emptyMap(),
        refPrices: Map<String, Double> = emptyMap(),
    ): BatchCost {
        fun p(key: String): Double = price(key, customPrices, refPrices)

        val flourCost = blend.sumOf { entry -> result.flourWeight * (entry.percent / 100) * p(entry.type) }
        val fatCost = result.fatWeight * p("fat")
        val saltCost = result.saltWeight * p("salt")
        val yeastCost = result.yeastWeight * p(yeastType.ifEmpty { "instant" })

        val sweetenerTypeTruthy = FormulaCalculator.truthy(sweetenerType)
        val sweetKey = if (sweetenerTypeTruthy != null) sweetenerPriceKey[sweetenerTypeTruthy] ?: sweetenerTypeTruthy else "sugar"
        val sweetenerCost = (result.sweetenerWeight ?: 0.0) * p(sweetKey)

        val maltCost = (result.maltWeight ?: 0.0) * p("diastatic_malt")
        val eggsCost = (result.eggWeight ?: 0.0) * p("eggs")
        val milkCost = (result.milkWeight ?: 0.0) * p("milk")
        val butterCost = (result.butterWeight ?: 0.0) * p("butter")

        val total = flourCost + fatCost + saltCost + yeastCost + sweetenerCost + maltCost + eggsCost + milkCost + butterCost

        return BatchCost(
            flourCost = flourCost, fatCost = fatCost, saltCost = saltCost, yeastCost = yeastCost,
            sweetenerCost = sweetenerCost, maltCost = maltCost, eggsCost = eggsCost,
            milkCost = milkCost, butterCost = butterCost, total = total,
        )
    }

    /**
     * `getPiecesPerUnit()`. Order is load-bearing, not incidental:
     * `"brioche_rolls_15oz"` also contains the substring `"rolls_15oz"`,
     * so the specific brioche check MUST run before the generic
     * 13-piece substring check below it, or brioche rolls would silently
     * resolve to the wrong piece count (13 instead of 12). Unlike the
     * dead/unreachable branch order found in `ProofTimeCalculator`'s
     * piece-size-factor block, reordering these two checks would
     * genuinely change behavior.
     */
    fun piecesPerUnit(forShape: String): Int = when {
        forShape == "brioche_rolls_15oz" || forShape == "brioche_rolls_25oz" -> 12
        forShape.contains("rolls_15oz") || forShape.contains("rolls_25oz") || forShape.contains("artisan_rolls") -> 13
        forShape == "ciabatta_panino_6" || forShape == "soft_hoagie_6in" || forShape == "soft_hoagie_8in" -> 6
        forShape == "soft_burger_bun" -> 6
        forShape == "brioche_burger_bun_4oz" -> 6
        forShape.startsWith("bagel_") -> 6
        forShape.startsWith("pretzel_") -> 6
        forShape.startsWith("em_") -> 12
        else -> 1
    }

    /**
     * The `totalPieces`/`perPiece` composition from the calculator
     * screen's own call site in the source — small enough, and a natural
     * enough composition of the two functions above, to include here
     * rather than defer to a later screen item.
     */
    fun costPerPiece(batchCost: BatchCost, shapeValue: String, numLoaves: Int): Double {
        val totalPieces = piecesPerUnit(shapeValue) * numLoaves
        return if (totalPieces > 0) batchCost.total / totalPieces else 0.0
    }
}
