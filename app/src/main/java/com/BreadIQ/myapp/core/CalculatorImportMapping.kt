package com.BreadIQ.myapp.core

/**
 * One ingredient from a `GET /api/import/staged/:token` payload — the
 * Safari-extension "import recipe" deep-link flow, distinct from the
 * in-app `ImportModal` wizard's own richer `ImportIngredientInput`
 * (this shape carries only name/category/grams/flagged, matching the
 * source's `StagedIngredient` interface in `calculator.tsx`).
 */
data class StagedImportIngredient(
    val name: String,
    val category: IngredientCategory,
    val grams: Double,
    val flagged: String? = null,
)

/**
 * `suggestedStyleValue` (renamed from the original `styleValue`, per
 * `IMPORT_REVIEW_SPEC.md` §3) is a ranked SUGGESTION, not a command —
 * `ImportReviewScreen` pre-selects it in its style picker, but the user
 * confirms or overrides it there before anything is applied to the
 * calculator. Nothing here computes it any differently; this is a naming
 * fix at the boundary between this pure mapping function and its caller,
 * which used to call `selectStyle(_:)` with this value directly and
 * silently — see `CalculatorScreen.applyImportReviewOutcome`.
 */
data class CalculatorImportMapping(
    val suggestedStyleValue: String,
    val hydration: Double,
    val fat: Double,
    val salt: Double,
    val yeast: Double,
    /**
     * Non-nil only when `sugarPct >= 2` — matches the source's own
     * conditional `setSweetenerType`/`setSweetenerPct` calls (no "clear
     * sweetener" branch exists for the below-2% case; whatever the
     * calculator already had stays untouched there).
     */
    val sweetenerType: String? = null,
    val sweetenerPct: Double? = null,
    /**
     * **New, not a source field** — the source's `applyImportToCalculator`
     * sums dairy grams only for the hydration-percent calculation and the
     * brioche style-inference heuristic, then discards the amount
     * entirely; there was no milk slot on its output at all. This is the
     * first time any dairy amount reaches the calculator: `null` when no
     * `.dairy` ingredient is present, otherwise `round(dairyG/flourG*100)`
     * — the same whole-percent shape as [hydration]/[fat] above —
     * clamped to `CalculatorScreen`'s own "Whole Milk" stepper range
     * (5...40), the only range this value is ever actually used through
     * today.
     */
    val milkPercent: Double? = null,
    /**
     * See `FormulaResult.dairyDisplayName`'s own doc comment for the full
     * writeup — `null` for no dairy or plain whole milk (every display
     * site keeps its own existing default wording), otherwise
     * `"Buttermilk"`/`"Heavy Cream"`, matching `ImportModalFormatting`'s
     * own Step 3 wording for those two sources exactly.
     */
    val dairyDisplayName: String? = null,
)

sealed class CalculatorImportMappingError {
    /** "No flour found in this import — cannot populate calculator." */
    data object NoFlour : CalculatorImportMappingError()
}

sealed class CalculatorImportMappingResult {
    data class Success(val mapping: CalculatorImportMapping) : CalculatorImportMappingResult()
    data class Failure(val error: CalculatorImportMappingError) : CalculatorImportMappingResult()
}

/**
 * Port of `calculator.tsx`'s `applyImportToCalculator` (lines 1086-1140)
 * — the Safari-extension deep-link "auto-apply" mapping, deliberately
 * kept separate from [ImportAnalyzer] even though both analyze a flat
 * ingredient list. They're two genuinely different functions in the
 * source: this one is a lightweight nearest-style heuristic that writes
 * directly into the Calculator's own card-0 controls (rounded to
 * whole/tenth percentages, clamped to each slider's real range), not the
 * wizard's own full baker's-percentage analysis:
 *
 * - Rounding differs: whole-percent rounding for hydration/fat/sugar
 *   here vs. [ImportAnalyzer]'s 1-decimal `round1`.
 * - Style inference differs: this one has its own 4-branch heuristic
 *   (brioche/soft_roll/ciabatta/baguette, falling back to artisan) with
 *   a `baguette` branch [ImportAnalyzer]'s style inference doesn't have
 *   at all (that one only ever returns artisan/soft_roll/ciabatta/brioche).
 * - Yeast/salt get `|| 0.8` / `|| 2.0` JS-falsy fallbacks; [ImportAnalyzer]
 *   has no such fallback, since its output is a display-only formula
 *   breakdown, not a slider position that needs to stay usable.
 *
 * **A genuine source bug, confirmed by running the literal JS, not
 * assumed from reading it — fixed here rather than preserved, per direct
 * instruction.** The source computes `yeastPct`/`saltPct` via
 * `Math.round((yeastG / flourG) * 10) / 10` — the ratio (already a small
 * fraction like 0.01 for 1% yeast) multiplied by only 10, not 1000.
 * Getting one decimal place of PERCENT from a ratio needs
 * `ratio * 100 * 10 / 10`, i.e. `* 1000`; `* 10` is two orders of
 * magnitude short. The practical effect in the source: for essentially
 * any realistic bread recipe (yeast/salt between roughly 0.5%-3% of
 * flour weight), `ratio * 10` never reaches the 0.5 threshold needed to
 * round up to a nonzero tenth, so `yeastPct`/`saltPct` are literally
 * ALWAYS `0` there — meaning the `|| 0.8` / `|| 2.0` fallbacks aren't
 * really "fallbacks" for an edge case in the source, they're the value
 * produced for essentially every real recipe. This port uses `* 1000`
 * (below), producing the intended one-decimal percentage instead — e.g.
 * the 500g-flour/5g-yeast baseline recipe now correctly yields `1.0`,
 * not the `0.8` fallback. The `|| 0.8`/`|| 2.0` fallback behavior ITSELF
 * is still ported faithfully below (a real ingredient list with
 * literally zero yeast or salt still falls back, matching the source's
 * own JS-falsy `x || fallback` idiom) — only the arithmetic feeding into
 * it was wrong.
 *
 * **A real inconsistency between the two source call sites, fixed here
 * rather than preserved, per direct instruction.** `byCategory("egg")`
 * here summed ONLY the `.egg` category — unlike `/api/import/analyze`'s
 * route handler (ported as [ImportAnalyzer]), which explicitly sums
 * `.egg` + `.eggYolk` together into one `eggGrams` figure. A staged
 * import containing only egg yolks (no whole eggs) would silently
 * contribute nothing to this mapping's hydration/style-inference math,
 * even though the same ingredients count fully in the in-app wizard's
 * own Step 3 results. `eggG` below now sums both categories, matching
 * [ImportAnalyzer]'s `eggWholeGrams + eggYolkGrams` shape exactly.
 */
object CalculatorImportApplier {

    fun map(ingredients: List<StagedImportIngredient>): CalculatorImportMappingResult {
        fun byCategory(cat: IngredientCategory): Double = ingredients.filter { it.category == cat }.sumOf { it.grams }

        val flourG = byCategory(IngredientCategory.FLOUR)
        if (flourG <= 0) return CalculatorImportMappingResult.Failure(CalculatorImportMappingError.NoFlour)

        val waterG = byCategory(IngredientCategory.WATER)
        val dairyG = byCategory(IngredientCategory.DAIRY)
        // `.egg` + `.eggYolk`, matching ImportAnalyzer's eggWholeGrams +
        // eggYolkGrams — see this object's own doc comment for the fix
        // write-up.
        val eggG = byCategory(IngredientCategory.EGG) + byCategory(IngredientCategory.EGG_YOLK)
        val fatG = byCategory(IngredientCategory.FAT)
        val sugarG = byCategory(IngredientCategory.SUGAR)
        val yeastG = byCategory(IngredientCategory.YEAST)
        val saltG = byCategory(IngredientCategory.SALT)

        val totalMoist = waterG + dairyG * 0.87 + eggG * 0.75
        val hydPct = ((totalMoist / flourG) * 100).swiftRounded()
        val fatPct = ((fatG / flourG) * 100).swiftRounded()
        val sugarPct = ((sugarG / flourG) * 100).swiftRounded()

        // `* 1000`, not the source's buggy `* 10` — see this object's own
        // doc comment for the full writeup of the fix.
        val yeastPctRaw = (((yeastG / flourG) * 1000).swiftRounded()) / 10
        val yeastPct = if (jsFalsy(yeastPctRaw)) 0.8 else yeastPctRaw
        val saltPctRaw = (((saltG / flourG) * 1000).swiftRounded()) / 10
        val saltPct = if (jsFalsy(saltPctRaw)) 2.0 else saltPctRaw

        var suggestedStyleValue = "artisan"
        if (fatPct >= 30 || (eggG > 0 && dairyG > 0 && fatG > 0)) {
            suggestedStyleValue = "brioche"
        } else if (fatPct >= 5 && sugarPct >= 2) {
            suggestedStyleValue = "soft_roll"
        } else if (hydPct >= 78) {
            suggestedStyleValue = "ciabatta"
        } else if (hydPct < 65) {
            suggestedStyleValue = "baguette"
        }

        var sweetenerType: String? = null
        var sweetenerPct: Double? = null
        if (sugarPct >= 2) {
            sweetenerType = "granulated_sugar"
            sweetenerPct = minOf(sugarPct, 20.0)
        }

        // Milk percent — null (not 0) when no dairy ingredient is
        // present, so callers can tell "no dairy in this import" apart
        // from "dairy present but rounds to a clamp boundary." See this
        // field's own doc comment on [CalculatorImportMapping] for the
        // full writeup.
        val milkPercent: Double? = if (dairyG > 0) {
            minOf(maxOf(((dairyG / flourG) * 100).swiftRounded(), 5.0), 40.0)
        } else {
            null
        }

        // Dominant dairy source, by summed grams — [StagedImportIngredient]
        // carries a `name` even though its `category` was already decided
        // upstream by the Safari extension, so `detectDairySource` (a
        // pure name-matching function, unrelated to how `category` got
        // set) can still run locally on every `.dairy`-tagged ingredient
        // here. Most real imports have exactly one dairy line; a mixed
        // import (e.g. buttermilk + a splash of cream) resolves to
        // whichever source contributed the most grams — there's only one
        // milk slot in the generated formula, so *some* single label has
        // to win.
        var wholeMilkG = 0.0
        var buttermilkG = 0.0
        var heavyCreamG = 0.0
        for (ingredient in ingredients) {
            if (ingredient.category != IngredientCategory.DAIRY) continue
            when (IngredientClassifier.detectDairySource(ingredient.name)) {
                DairySource.WHOLE_MILK -> wholeMilkG += ingredient.grams
                DairySource.BUTTERMILK -> buttermilkG += ingredient.grams
                DairySource.HEAVY_CREAM -> heavyCreamG += ingredient.grams
            }
        }
        val dominant = maxOf(wholeMilkG, buttermilkG, heavyCreamG)
        val dairyDisplayName: String? = when {
            dominant == 0.0 -> null // no dairy ingredient at all
            dominant == buttermilkG -> "Buttermilk"
            dominant == heavyCreamG -> "Heavy Cream"
            else -> null // whole milk is dominant (or ties for it) — keep each site's own default wording
        }

        return CalculatorImportMappingResult.Success(
            CalculatorImportMapping(
                suggestedStyleValue = suggestedStyleValue,
                hydration = minOf(maxOf(hydPct, 50.0), 100.0),
                fat = minOf(maxOf(fatPct, 0.0), 20.0),
                salt = minOf(maxOf(saltPct, 1.5), 3.5),
                yeast = minOf(maxOf(yeastPct, 0.1), 2.0),
                sweetenerType = sweetenerType,
                sweetenerPct = sweetenerPct,
                milkPercent = milkPercent,
                dairyDisplayName = dairyDisplayName,
            ),
        )
    }

    /**
     * JS's `x || fallback` — falsy for `0`/`-0`/`NaN` (the only numeric
     * falsy values reachable here; `flourG > 0` is already guaranteed by
     * the caller, so a `NaN` from a `0/0` division can't actually occur,
     * but checked anyway for a faithful, literal port).
     */
    private fun jsFalsy(n: Double): Boolean = n == 0.0 || n.isNaN()
}
