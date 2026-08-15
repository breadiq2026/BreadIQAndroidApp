package com.BreadIQ.myapp.core

/**
 * Canonical unit key `normalizeUnit()` resolves any user-entered unit
 * string down to. Mirrors the source's return values exactly; modeled as
 * a Kotlin enum (source returns a plain `string`) purely for
 * exhaustiveness at the [convertToGrams] call sites — not an observable
 * behavior change.
 */
enum class ConversionUnit(val rawValue: String) {
    G("g"), OZ("oz"), LB("lb"),
    /**
     * Has no source counterpart — added per direct instruction alongside
     * [IngredientLineParser]'s `recognizedUnits`' `"kg"`/`"kilogram"`/
     * `"kilograms"`/`"kilo"` additions. Needed as a real case (not just a
     * `normalizeUnit` synonym folded into [G]) because the ×1000
     * conversion in [convertToGrams] below has to run on the
     * *unconverted* quantity — folding "kg" into `.g` would silently
     * treat a kilogram quantity as a gram quantity.
     */
    KG("kg"),
    CUP("cup"), TBSP("tbsp"), TSP("tsp"), ML("ml"), STICK("stick"), COUNT("count"),
}

data class ConversionResult(val grams: Double, val flagged: String? = null)

/**
 * Port of `ImportModal.tsx`'s volume-to-weight density tables and
 * conversion logic (`breadiq-mobile/components/ImportModal.tsx`, lines
 * 100-196) — deliberately excluded from [IngredientClassifier].
 * `CATEGORY_DEFAULT_UNIT` (lines 210-213) is included here too, even
 * though it sits physically between `convertToGrams` and `parseFraction`
 * in the source — `parseFraction` itself stays out of scope for
 * [IngredientLineParser].
 *
 * **Mobile/web drift, checked against the canonical
 * `lib/ingredient-densities/src/index.ts` module this block claims to be
 * "inlined from"** (same discipline as [IngredientClassifier], which
 * found the `egg_yolk` category gap in the same header comment's claim):
 * - **A genuine numeric disagreement, not just structural**: canonical
 *   converts an egg yolk to **17g** (`if (name.includes("yolk")) return
 *   { grams: quantity * 17 }`); mobile uses **18g** in both of its
 *   yolk-handling spots (the dedicated `egg_yolk` category branch and the
 *   nested check inside the plain `egg` branch). Ported mobile's 18g,
 *   since that's this item's actual scope — a recipe imported on web vs.
 *   this app would compute very slightly different dough weights from
 *   the same "3 egg yolks" line today.
 * - `normalizeUnit()`'s `"ml"` synonym list is missing `"millilitre"`
 *   (British spelling) on mobile — canonical recognizes 4 variants,
 *   mobile only 3. A user typing "millilitre" gets `.ml` on web but falls
 *   through to the `.count` catch-all on mobile. Ported mobile's
 *   (shorter) list.
 * - Canonical's flour/salt density tables carry extra entries mobile's
 *   copy doesn't have (`all_purpose_flour: 125`, `sea_salt`) — but both
 *   are provably dead code even in the canonical module itself: canonical's
 *   own lookup logic never reads either key. Not missing functionality,
 *   just extra unused data on the web side.
 * - The yeast "count" fallback's `flagged` message text differs
 *   cosmetically ("Assumed standard 7g packet." on mobile vs
 *   "...verify if using a different size." on canonical) — no behavioral
 *   effect, mobile's shorter text ported as-is.
 * - Every density VALUE that exists on both sides (fat/sugar/dairy/yeast
 *   cup-tbsp-tsp figures, flour g-per-cup by type, water's fixed
 *   237/14.8/4.9 factors, oz/lb conversion constants) matches exactly
 *   between the two copies — confirmed by diffing every table line by
 *   line, not just spot-checking.
 */
object IngredientDensityConverter {

    // MARK: - Density tables (lines 100-105)

    private data class FatDensity(val cup: Double, val tbsp: Double, val stick: Double?)
    private data class SugarDensity(val cup: Double, val tbsp: Double)
    private data class DairyDensity(val cup: Double, val tbsp: Double)
    private data class SaltDensity(val tsp: Double, val tbsp: Double)

    /**
     * `FLOUR_G_PER_CUP`. Modeled as an exhaustive `when` over [FlourType]
     * rather than a map — the source's `?? 120` fallback
     * (`FLOUR_G_PER_CUP[detectFlourType(name)] ?? 120`) is defensive
     * TS-typing boilerplate that can never actually trigger
     * (`detectFlourType` only ever returns one of these 7 cases, all
     * present in the source's own table), so there's no unreachable path
     * to preserve — Kotlin's exhaustiveness check makes the fallback
     * provably redundant rather than removing real behavior.
     */
    private fun flourGramsPerCup(type: FlourType): Double = when (type) {
        FlourType.BREAD -> 120.0
        FlourType.DOUBLE_ZERO -> 120.0
        FlourType.SEMOLINA -> 167.0
        FlourType.WHOLE_WHEAT -> 130.0
        FlourType.RYE -> 102.0
        FlourType.SPELT -> 100.0
        FlourType.EINKORN -> 100.0
    }

    /** `FAT_G`. */
    private fun fatDensity(source: FatSource): FatDensity = when (source) {
        FatSource.BUTTER -> FatDensity(cup = 227.0, tbsp = 14.0, stick = 113.0)
        FatSource.LARD -> FatDensity(cup = 205.0, tbsp = 12.8, stick = null)
        FatSource.OLIVE_OIL -> FatDensity(cup = 216.0, tbsp = 13.5, stick = null)
        FatSource.VEGETABLE_OIL -> FatDensity(cup = 218.0, tbsp = 13.6, stick = null)
    }

    /** `SUGAR_G`. */
    private fun sugarDensity(source: SugarSource): SugarDensity = when (source) {
        SugarSource.GRANULATED_SUGAR -> SugarDensity(cup = 200.0, tbsp = 12.5)
        SugarSource.HONEY -> SugarDensity(cup = 340.0, tbsp = 21.0)
        SugarSource.MOLASSES -> SugarDensity(cup = 340.0, tbsp = 20.0)
        SugarSource.BARLEY_MALT -> SugarDensity(cup = 340.0, tbsp = 20.0)
        SugarSource.BROWN_SUGAR -> SugarDensity(cup = 220.0, tbsp = 13.75)
        SugarSource.POWDERED_SUGAR -> SugarDensity(cup = 120.0, tbsp = 7.5)
    }

    /** `DAIRY_G`. */
    private fun dairyDensity(source: DairySource): DairyDensity = when (source) {
        DairySource.WHOLE_MILK -> DairyDensity(cup = 244.0, tbsp = 15.25)
        DairySource.BUTTERMILK -> DairyDensity(cup = 245.0, tbsp = 15.3)
        DairySource.HEAVY_CREAM -> DairyDensity(cup = 238.0, tbsp = 14.875)
    }

    /** `YEAST_G`. */
    private fun yeastGramsPerTsp(type: YeastType): Double = when (type) {
        YeastType.INSTANT -> 3.0
        YeastType.ACTIVE_DRY -> 3.1
        YeastType.FRESH -> 8.0
    }

    /** `SALT_G`. */
    private val saltDensityTable = SaltDensity(tsp = 6.0, tbsp = 18.0)
    private val saltDensityKosher = SaltDensity(tsp = 5.0, tbsp = 15.0)

    /**
     * `CATEGORY_DEFAULT_UNIT` (lines 210-213). Mobile's copy has 10
     * entries (including `egg_yolk`); the canonical module's has 9 (no
     * `egg_yolk` — consistent with the category gap [IngredientClassifier]
     * already documented).
     */
    fun defaultUnit(category: IngredientCategory): ConversionUnit = when (category) {
        IngredientCategory.FLOUR -> ConversionUnit.CUP
        IngredientCategory.WATER -> ConversionUnit.CUP
        IngredientCategory.YEAST -> ConversionUnit.TSP
        IngredientCategory.SALT -> ConversionUnit.TSP
        IngredientCategory.FAT -> ConversionUnit.G
        IngredientCategory.SUGAR -> ConversionUnit.TBSP
        IngredientCategory.DAIRY -> ConversionUnit.CUP
        IngredientCategory.EGG -> ConversionUnit.COUNT
        IngredientCategory.EGG_YOLK -> ConversionUnit.COUNT
        IngredientCategory.UNKNOWN -> ConversionUnit.G
    }

    // MARK: - normalizeUnit() — lines 107-118

    fun normalizeUnit(unit: String): ConversionUnit {
        val u = unit.lowercase().trim()
        if (u in setOf("g", "gram", "grams", "gr")) return ConversionUnit.G
        if (u in setOf("oz", "ounce", "ounces")) return ConversionUnit.OZ
        if (u in setOf("lb", "lbs", "pound", "pounds", "#")) return ConversionUnit.LB
        if (u in setOf("kg", "kilogram", "kilograms", "kilo")) return ConversionUnit.KG
        if (u in setOf("cup", "cups", "c")) return ConversionUnit.CUP
        if (u in setOf("tbsp", "tbs", "tbl", "tbls", "tblsp", "tablespoon", "tablespoons")) return ConversionUnit.TBSP
        if (u in setOf("tsp", "ts", "teaspoon", "teaspoons")) return ConversionUnit.TSP
        if (u in setOf("ml", "milliliter", "milliliters")) return ConversionUnit.ML
        if (u in setOf("stick", "sticks")) return ConversionUnit.STICK
        return ConversionUnit.COUNT
    }

    // MARK: - convertToGrams() — lines 120-196

    fun convertToGrams(quantity: Double, unit: String, category: IngredientCategory, name: String): ConversionResult {
        val u = normalizeUnit(unit)

        if (u == ConversionUnit.G) return ConversionResult(grams = quantity)
        if (u == ConversionUnit.OZ) return ConversionResult(grams = quantity * 28.3495)
        if (u == ConversionUnit.LB) return ConversionResult(grams = quantity * 453.592)
        if (u == ConversionUnit.KG) return ConversionResult(grams = quantity * 1000)
        if (u == ConversionUnit.ML) {
            if (category == IngredientCategory.FAT) {
                val f = fatDensity(IngredientClassifier.detectFatSource(name))
                return ConversionResult(grams = quantity * f.cup / 237)
            }
            if (category == IngredientCategory.DAIRY) {
                val d = dairyDensity(IngredientClassifier.detectDairySource(name))
                return ConversionResult(grams = quantity * d.cup / 237)
            }
            return ConversionResult(grams = quantity)
        }

        if (category == IngredientCategory.EGG_YOLK) {
            return ConversionResult(grams = quantity * 18)
        }
        if (category == IngredientCategory.EGG) {
            val n = name.lowercase()
            if (n.contains("white")) return ConversionResult(grams = quantity * 30)
            if (n.contains("yolk")) return ConversionResult(grams = quantity * 18)
            return ConversionResult(grams = quantity * 50)
        }
        if (category == IngredientCategory.YEAST) {
            val g = yeastGramsPerTsp(IngredientClassifier.detectYeastType(name))
            if (u == ConversionUnit.TSP) return ConversionResult(grams = quantity * g)
            if (u == ConversionUnit.TBSP) return ConversionResult(grams = quantity * g * 3)
            if (u == ConversionUnit.CUP) return ConversionResult(grams = quantity * g * 48)
            if (u == ConversionUnit.COUNT) return ConversionResult(grams = quantity * 7, flagged = "Assumed standard 7g packet.")
            return ConversionResult(grams = quantity)
        }
        if (category == IngredientCategory.SALT) {
            val s = if (name.lowercase().contains("kosher")) saltDensityKosher else saltDensityTable
            if (u == ConversionUnit.TSP) return ConversionResult(grams = quantity * s.tsp)
            if (u == ConversionUnit.TBSP) return ConversionResult(grams = quantity * s.tbsp)
            if (u == ConversionUnit.CUP) return ConversionResult(grams = quantity * s.tbsp * 16)
            return ConversionResult(grams = quantity)
        }
        if (category == IngredientCategory.FLOUR) {
            val g = flourGramsPerCup(IngredientClassifier.detectFlourType(name))
            if (u == ConversionUnit.CUP) return ConversionResult(grams = quantity * g)
            if (u == ConversionUnit.TBSP) return ConversionResult(grams = quantity * g / 16)
            if (u == ConversionUnit.TSP) return ConversionResult(grams = quantity * g / 48)
            return ConversionResult(grams = quantity)
        }
        if (category == IngredientCategory.FAT) {
            val f = fatDensity(IngredientClassifier.detectFatSource(name))
            if (u == ConversionUnit.STICK) return ConversionResult(grams = quantity * (f.stick ?: 113.0))
            if (u == ConversionUnit.CUP) return ConversionResult(grams = quantity * f.cup)
            if (u == ConversionUnit.TBSP) return ConversionResult(grams = quantity * f.tbsp)
            if (u == ConversionUnit.TSP) return ConversionResult(grams = quantity * f.tbsp / 3)
            return ConversionResult(grams = quantity)
        }
        if (category == IngredientCategory.SUGAR) {
            val s = sugarDensity(IngredientClassifier.detectSugarSource(name))
            if (u == ConversionUnit.CUP) return ConversionResult(grams = quantity * s.cup)
            if (u == ConversionUnit.TBSP) return ConversionResult(grams = quantity * s.tbsp)
            if (u == ConversionUnit.TSP) return ConversionResult(grams = quantity * s.tbsp / 3)
            return ConversionResult(grams = quantity)
        }
        if (category == IngredientCategory.DAIRY) {
            val d = dairyDensity(IngredientClassifier.detectDairySource(name))
            if (u == ConversionUnit.CUP) return ConversionResult(grams = quantity * d.cup)
            if (u == ConversionUnit.TBSP) return ConversionResult(grams = quantity * d.tbsp)
            if (u == ConversionUnit.TSP) return ConversionResult(grams = quantity * d.tbsp / 3)
            return ConversionResult(grams = quantity)
        }
        if (category == IngredientCategory.WATER) {
            if (u == ConversionUnit.CUP) return ConversionResult(grams = quantity * 237)
            if (u == ConversionUnit.TBSP) return ConversionResult(grams = quantity * 14.8)
            if (u == ConversionUnit.TSP) return ConversionResult(grams = quantity * 4.9)
            return ConversionResult(grams = quantity)
        }

        return ConversionResult(grams = quantity, flagged = "Unrecognized ingredient — enter grams directly.")
    }
}
