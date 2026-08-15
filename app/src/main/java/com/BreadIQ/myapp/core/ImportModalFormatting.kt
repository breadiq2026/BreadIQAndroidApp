package com.BreadIQ.myapp.core

import java.text.NumberFormat
import java.util.Locale

/**
 * Client-only display formatting for the Import screen — port of
 * `ImportModal.tsx`'s `getDisplayLabel`/`fmtMins`/`round1` and the
 * `CAT_COLORS`/`STYLE_LABEL` tables (lines 307-327, 392-393, 456-470).
 * Kept separate from [ImportAnalyzer] (the backend route's own logic)
 * since none of this exists server-side — it's presentation-only, same
 * split the source itself keeps between `import.ts` and `ImportModal.tsx`.
 */
object ImportModalFormatting {

    fun round1(n: Double): Double = (n * 10).swiftRounded() / 10

    /**
     * Swift's default `Double.formatted()` — locale-aware grouping
     * separators plus up to 1 fractional digit shown only when actually
     * present (no forced trailing `.0`). Every value this is called on in
     * the source is already 1-decimal-rounded upstream (via [round1] or
     * [ImportAnalyzer]'s own `round1`), so `maximumFractionDigits = 1`
     * here is a safety cap, not a rounding step of its own.
     */
    private val numberFormatter: NumberFormat by lazy {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            isGroupingUsed = true
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }
    }

    fun formatted(n: Double): String = numberFormatter.format(n)

    /** `fmtMins()` — lines 456-460. */
    fun fmtMins(m: Int): String {
        if (m < 60) return "${m}m"
        val h = m / 60
        val r = m % 60
        return if (r == 0) "${h}h" else "${h}h ${r}m"
    }

    /** `getDisplayLabel()` — lines 307-327. */
    fun displayLabel(category: IngredientCategory, name: String): String = when (category) {
        IngredientCategory.FLOUR -> when (IngredientClassifier.detectFlourType(name)) {
            FlourType.BREAD -> "Bread Flour"
            FlourType.DOUBLE_ZERO -> "00 Flour"
            FlourType.WHOLE_WHEAT -> "Whole Wheat"
            FlourType.RYE -> "Rye Flour"
            FlourType.SEMOLINA -> "Semolina"
            FlourType.SPELT -> "Spelt"
            FlourType.EINKORN -> "Einkorn"
        }
        IngredientCategory.YEAST -> when (IngredientClassifier.detectYeastType(name)) {
            YeastType.FRESH -> "Fresh Yeast"
            YeastType.ACTIVE_DRY -> "Active Dry Yeast"
            YeastType.INSTANT -> "Instant Yeast"
        }
        IngredientCategory.FAT -> when (IngredientClassifier.detectFatSource(name)) {
            FatSource.BUTTER -> "Butter"
            FatSource.LARD -> "Lard"
            FatSource.OLIVE_OIL -> "Olive Oil"
            FatSource.VEGETABLE_OIL -> "Oil"
        }
        IngredientCategory.SUGAR -> when (IngredientClassifier.detectSugarSource(name)) {
            SugarSource.GRANULATED_SUGAR -> "Sugar"
            SugarSource.HONEY -> "Honey"
            SugarSource.MOLASSES -> "Molasses"
            SugarSource.BARLEY_MALT -> "Barley Malt"
            SugarSource.BROWN_SUGAR -> "Brown Sugar"
            SugarSource.POWDERED_SUGAR -> "Powdered Sugar"
        }
        IngredientCategory.DAIRY -> when (IngredientClassifier.detectDairySource(name)) {
            DairySource.BUTTERMILK -> "Buttermilk"
            DairySource.HEAVY_CREAM -> "Heavy Cream"
            DairySource.WHOLE_MILK -> "Milk"
        }
        IngredientCategory.WATER -> "Water"
        IngredientCategory.SALT -> "Salt"
        IngredientCategory.EGG -> "Eggs"
        // source's `Record<string,string>` lookup has no "egg_yolk" entry — falls through to the raw category string itself, ported literally.
        IngredientCategory.EGG_YOLK -> "egg_yolk"
        IngredientCategory.UNKNOWN -> "Unknown"
    }

    /** `CAT_COLORS` — lines 462-466. */
    fun categoryColorHex(category: IngredientCategory): String = when (category) {
        IngredientCategory.FLOUR -> "#D97706"
        IngredientCategory.WATER -> "#2563EB"
        IngredientCategory.YEAST -> "#7C3AED"
        IngredientCategory.SALT -> "#6B7280"
        IngredientCategory.FAT -> "#CA8A04"
        IngredientCategory.SUGAR -> "#DB2777"
        IngredientCategory.DAIRY -> "#0D9488"
        IngredientCategory.EGG -> "#EA580C"
        IngredientCategory.EGG_YOLK -> "#C2500A"
        IngredientCategory.UNKNOWN -> "#DC2626"
    }

    /**
     * `STYLE_LABEL` — lines 468-470. Unlike [categoryColorHex], this
     * table only has 4 entries in the source (not every `BreadStyleDef`
     * value) — a style outside this set falls back to its own raw value
     * string, ported literally via the `?? result.inferredStyle` pattern
     * at the one real call site (Step 3's title).
     */
    fun styleLabel(style: String): String = when (style) {
        "artisan" -> "Artisan"
        "ciabatta" -> "Ciabatta"
        "soft_roll" -> "Soft Roll"
        "brioche" -> "Brioche"
        else -> style
    }
}
