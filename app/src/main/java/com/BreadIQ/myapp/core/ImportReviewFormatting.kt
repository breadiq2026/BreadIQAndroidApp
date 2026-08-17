package com.BreadIQ.myapp.core

import android.net.Uri
import com.BreadIQ.myapp.model.BreadStyleCatalog
import com.BreadIQ.myapp.model.BreadStyleDef

/**
 * Ported from the iOS app's `Core/ImportReviewFormatting.swift`. Small
 * pure helpers for `ui/calculator/ImportReviewScreen.kt`, pulled out
 * specifically so they're unit-testable without instantiating a
 * Composable — this codebase's established pattern (see
 * [CalculatorFormatting]/[ImportModalFormatting]).
 */
object ImportReviewFormatting {

    /**
     * Derives a display-friendly source name from a staged import's
     * `sourceURL` — the hostname, e.g.
     * `"https://www.kingarthurbaking.com/recipes/..."` →
     * `"www.kingarthurbaking.com"`. Used both by `ImportReviewScreen`
     * (shown next to the recipe name) and `CalculatorViewModel`
     * (`Recipe.importSourceName`, set once the import is confirmed) —
     * extracted here so both stay in sync rather than each re-deriving
     * it slightly differently.
     */
    fun sourceDomain(urlString: String?): String? = urlString?.let { Uri.parse(it).host }

    /**
     * The style `ImportReviewScreen`'s style picker pre-selects —
     * [CalculatorImportMapping.suggestedStyleValue] resolved against the
     * real catalog, falling back to the catalog's first entry if the
     * mapping ever produced a value not in [BreadStyleCatalog] (defensive;
     * [CalculatorImportApplier.map] only ever returns values that exist in
     * the catalog today, but this avoids a crash if that ever drifts).
     */
    fun initialStyle(mapping: CalculatorImportMapping): BreadStyleDef =
        BreadStyleCatalog.all.firstOrNull { it.value == mapping.suggestedStyleValue } ?: BreadStyleCatalog.all[0]
}
