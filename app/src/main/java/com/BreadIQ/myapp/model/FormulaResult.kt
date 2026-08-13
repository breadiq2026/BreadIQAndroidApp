package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/FormulaResult.swift`.
 *
 * One flour's contribution to a blended formula's *computed* gram
 * breakdown (preferment/final-dough split) — distinct from
 * [FlourBlendEntry], which is just the `{type, percent}` input.
 */
data class FlourBreakdownEntry(
    val type: String,
    val label: String,
    val percent: Double,
    val grams: Double,
    val prefermentGrams: Double? = null,
    val finalDoughGrams: Double,
)

data class BakerPercentages(
    val water: Double,
    val salt: Double,
    val fat: Double,
    val yeast: Double,
    val malt: Double? = null,
    val sweetener: Double? = null,
    val eggs: Double? = null,
    val milk: Double? = null,
    val butter: Double? = null,
)

/**
 * What's left in the final dough after a preferment is pulled out.
 * Includes the enriched-ingredient pass-through fields (sweetener/egg/
 * milk/butter never go in the preferment — they always land here).
 */
data class PrefermentFinalMix(
    val flourWeight: Double,
    val waterWeight: Double,
    val saltWeight: Double,
    val fatWeight: Double,
    val yeastWeight: Double,
    val prefermentWeight: Double,
    val sweetenerWeight: Double? = null,
    val eggWeight: Double? = null,
    val milkWeight: Double? = null,
    val butterWeight: Double? = null,
)

data class PrefermentResult(
    val type: String,
    val flourWeight: Double,
    val waterWeight: Double,
    val saltWeight: Double,
    val yeastWeight: Double,
    val totalWeight: Double,
    val finalMix: PrefermentFinalMix,
)

/**
 * Direct output shape of the backend's `calcFormula()`
 * (`api-server/src/routes/calculator.ts`, per the iOS port's verification
 * against its actual `return { ... }` statement, not inferred from client
 * usage).
 *
 * This is a pure calculation result, recomputed fresh every time the user
 * adjusts a calculator slider — the source app never persists it on its
 * own. When a formula is saved, specific fields get copied into a [Recipe]
 * instead; when a bake is started, specific fields get copied into
 * [BakeStep]s. Modeled as a plain value type rather than a persisted
 * entity for that reason — it has no persistent identity of its own, and
 * a value type is what the eventual `FormulaCalculator` port (a pure
 * `(FormulaInput) -> FormulaResult` function) needs to be easily
 * golden-fixture-testable against the live API's JSON responses.
 */
data class FormulaResult(
    val flourWeight: Double,
    val waterWeight: Double,
    val saltWeight: Double,
    val fatWeight: Double,
    val yeastWeight: Double,
    val maltWeight: Double? = null,
    val sweetenerWeight: Double? = null,
    val sweetenerWaterWeight: Double? = null,
    val eggWeight: Double? = null,
    val eggCount: Double? = null,
    val milkWeight: Double? = null,
    val butterWeight: Double? = null,
    /**
     * **New, not a backend field — a documented departure from this
     * type's own "direct output shape of `calcFormula()`" contract above,
     * not bent quietly (matches the iOS port's own note).** `null` for the
     * ordinary case (plain water dough, or milk added by hand with no
     * known source) — every display site falls back to its own existing
     * hardcoded label ("Whole Milk"/"Milk") exactly as before. Only set
     * when an imported recipe's dairy ingredient is identifiably NOT whole
     * milk — `"Buttermilk"`/`"Heavy Cream"`. Pure pass-through — never
     * read by any math in `FormulaCalculator`.
     */
    val dairyDisplayName: String? = null,
    val effectiveHydrationPercent: Double? = null,
    val totalDoughWeight: Double,
    val flourBreakdown: List<FlourBreakdownEntry>? = null,
    val bakerPercentages: BakerPercentages,
    val preferment: PrefermentResult? = null,
)
