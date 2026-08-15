package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BakingSection
import com.BreadIQ.myapp.model.TechniqueGuideCatalog
import com.BreadIQ.myapp.model.TechniqueSection

/**
 * The `Method` section's real content-resolution logic — the fallback
 * chain, the `soft_roll` shaping-steps filter, and the "Divide After
 * Bulk"/"Portion After Bulk" row — all ported verbatim from
 * `bread-lab/src/lib/excel-export.ts` (lines 329-349 for the fallback
 * chain and filter, lines 593-647 for divide info), not from
 * `technique-guide.tsx`'s own simpler `DivideInfo` React component.
 * [TechniqueGuideCatalog] (the six real technique catalogs) is already
 * ported and sitting unused on Android until this file — real data, not
 * placeholders (an old doc comment on the iOS source's own
 * `TechniqueGuide.swift` calls the catalogs "a later pass" that "lands"
 * eventually; that pass landed a while ago, the comment there is just
 * stale).
 *
 * **A real, worth-flagging drift, carried forward from the iOS port's
 * own discovery**: `excel-export.ts`'s own comment claims its divide-info
 * block "mirrors DivideInfo component in the app" — it doesn't, not
 * exactly. `DivideInfo` (the on-screen component) uses a much shorter
 * `piecesPerUnit` rule (only `_15oz`/`_25oz` → 13, the two hoagie shapes
 * → 6, burger bun → 7, else 1). `excel-export.ts`'s own inline copy is
 * materially more detailed — it separately handles
 * `brioche_rolls_15oz`/`brioche_rolls_25oz` (12, not 13),
 * `ciabatta_panino_6` (6), `brioche_burger_bun_4oz` (6, not 7), every
 * `bagel_`/`pretzel_`-prefixed shape (6), and every `em_`-prefixed shape
 * (12) — none of which `DivideInfo` accounts for at all. Since this is
 * specifically for the XLSX export, `excel-export.ts`'s own real logic
 * (ported below) is the one that matters here, not the on-screen
 * component's simpler version.
 */
object TechniqueGuideLookup {

    data class Resolved(
        val kneading: TechniqueSection,
        val shaping: TechniqueSection,
        val proofing: TechniqueSection,
        val baking: BakingSection,
    )

    /**
     * The fallback chain, ported verbatim from `excel-export.ts` lines
     * 329-332:
     * ```
     * kneading = KNEADING[styleKey] ?? KNEADING.artisan
     * shaping  = SHAPING_BY_STYLE[styleKey] ?? SHAPING_BY_SHAPE[shapeKey] ?? SHAPING_BY_SHAPE.boule
     * proofing = PROOFING_GENERAL[styleKey] ?? PROOFING_GENERAL.artisan
     * baking   = BAKING_BY_STYLE[styleKey] ?? BAKING[shapeKey] ?? BAKING.boule
     * ```
     * `styleValue`/`shapeValue` are this app's own `BreadStyleDef.value`/
     * `LoafShape.value` raw strings — a direct pass-through, no mapping
     * step, matching how the reference's own real call site
     * (`bread-lab/src/pages/calculator.tsx`) passes its raw form-field
     * values with no transformation either.
     */
    fun resolve(styleValue: String, shapeValue: String): Resolved {
        val kneading = TechniqueGuideCatalog.kneading[styleValue] ?: TechniqueGuideCatalog.kneading.getValue("artisan")
        var shaping = TechniqueGuideCatalog.shapingByStyle[styleValue]
            ?: TechniqueGuideCatalog.shapingByShape[shapeValue]
            ?: TechniqueGuideCatalog.shapingByShape.getValue("boule")
        val proofing = TechniqueGuideCatalog.proofingGeneral[styleValue] ?: TechniqueGuideCatalog.proofingGeneral.getValue("artisan")
        val baking = TechniqueGuideCatalog.bakingByStyle[styleValue]
            ?: TechniqueGuideCatalog.baking[shapeValue]
            ?: TechniqueGuideCatalog.baking.getValue("boule")

        shaping = shaping.copy(steps = filteredSoftRollSteps(styleValue, shapeValue, shaping.steps))
        return Resolved(kneading, shaping, proofing, baking)
    }

    /**
     * `excel-export.ts` lines 334-349, ported exactly: which shaping
     * steps show up for `soft_roll` depends on the selected shape (dinner
     * roll/slider bun vs. burger bun vs. hoagie/pullman), not just the
     * style. A no-op for every other style (returns `steps` unchanged).
     */
    fun filteredSoftRollSteps(styleValue: String, shapeValue: String, steps: List<String>): List<String> {
        if (styleValue != "soft_roll" || TechniqueGuideCatalog.shapingByStyle["soft_roll"] == null) return steps
        val isRoll = shapeValue.contains("_15oz") || shapeValue.contains("_25oz")
        val isBurger = shapeValue == "soft_burger_bun"
        val isHoagie = shapeValue == "soft_hoagie_6in" || shapeValue == "soft_hoagie_8in"
        val isPullman = shapeValue == "pullman_soft"
        return steps.filterIndexed { i, _ ->
            when {
                i <= 1 -> true
                i == 2 -> isRoll
                i == 3 -> isBurger
                i == 4 -> isHoagie || isPullman
                else -> true
            }
        }
    }

    data class DivideAfterBulkInfo(val label: String, val text: String)

    /**
     * `excel-export.ts` lines 593-647, ported exactly — see this object's
     * own doc comment for how this differs from `technique-guide.tsx`'s
     * simpler on-screen `DivideInfo` component. Returns `null` when the
     * row shouldn't render at all (focaccia/ciabatta have no traditional
     * divide step).
     */
    fun divideAfterBulk(styleValue: String, shapeValue: String, numLoaves: Int, doughWeightPerPiece: Double): DivideAfterBulkInfo? {
        if (styleValue == "focaccia" || styleValue == "ciabatta") return null

        val piecesPerUnit: Int = when {
            shapeValue == "brioche_rolls_15oz" || shapeValue == "brioche_rolls_25oz" -> 12
            shapeValue.contains("rolls_15oz") || shapeValue.contains("rolls_25oz") || shapeValue.contains("artisan_rolls") -> 13
            shapeValue == "ciabatta_panino_6" || shapeValue == "soft_hoagie_6in" || shapeValue == "soft_hoagie_8in" -> 6
            shapeValue == "soft_burger_bun" -> 6
            shapeValue == "brioche_burger_bun_4oz" -> 6
            shapeValue.startsWith("bagel_") || shapeValue.startsWith("pretzel_") -> 6
            shapeValue.startsWith("em_") -> 12
            else -> 1
        }

        val isPizza = styleValue == "pizza_ny" || styleValue == "pizza_neo"
        val totalPieces = numLoaves * piecesPerUnit
        val pieceWeightG = (doughWeightPerPiece / piecesPerUnit).swiftRounded().toInt()
        val timing = if (isPizza) "after bulk — before cold ferment" else "after bulk fermentation"

        val text: String = if (piecesPerUnit == 1) {
            if (numLoaves > 1) {
                "Divide into $numLoaves equal pieces of approximately ${doughWeightPerPiece.swiftRounded().toInt()}g each $timing."
            } else {
                val closing = if (isPizza) "Ball and cold ferment before stretching." else "No dividing needed."
                "Single batch — ~${doughWeightPerPiece.swiftRounded().toInt()}g total dough. $closing"
            }
        } else {
            val batchDesc = if (numLoaves > 1) " ($numLoaves batches × $piecesPerUnit per batch)" else ""
            "Divide into $totalPieces pieces of approximately ${pieceWeightG}g each$batchDesc $timing."
        }

        val label: String = if (isPizza || piecesPerUnit > 1) {
            "Portion After Bulk"
        } else if (numLoaves > 1) {
            "Divide After Bulk"
        } else {
            "This Batch"
        }

        return DivideAfterBulkInfo(label = label, text = text)
    }
}
