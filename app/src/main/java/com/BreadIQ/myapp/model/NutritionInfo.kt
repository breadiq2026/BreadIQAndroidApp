package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/NutritionInfo.swift`.
 *
 * One bundled per-100g nutrition reference entry for a BreadIQ catalog
 * ingredient, sourced from USDA FoodData Central (FDC) wherever a real
 * match exists — used for the app's nutritional-analysis feature.
 *
 * **Provenance.** Every row here was independently verified against real
 * USDA FoodData Central data during the iOS port (WebSearch to
 * discover/confirm each FDC ID, then a direct fetch of
 * `tools.myfooddata.com/nutrition-facts/<FDC_ID>/100g`, a server-rendered
 * mirror of real USDA SR Legacy/FNDDS/Branded data) — not copied verbatim
 * from any single AI-generated candidate table. See the iOS file's doc
 * comment for the full row-by-row provenance, including several real FDC
 * ID errors caught and corrected during that verification pass (e.g.
 * All-Purpose Flour, Einkorn, Molasses, Heavy Whipping Cream, Dark Rye
 * Flour's protein value).
 *
 * **Key set.** Mirrors [IngredientReferencePriceCatalog]'s 22 keys
 * exactly, plus 7 import-only extras recognized elsewhere in the app but
 * with no calculator slot or reference price (`lard`, `vegetable_oil`,
 * `brown_sugar`, `powdered_sugar`, `buttermilk`, `heavy_cream`,
 * `egg_yolk`), plus `water` — 30 entries total.
 *
 * Modeled as a plain value type plus a static catalog, not a persisted
 * entity, for the same reason as [IngredientReferencePrice]: bundled,
 * ship-time-constant reference data with no persistent identity.
 */
data class NutritionInfo(
    val key: String,
    val label: String,
    /**
     * USDA FoodData Central ID this row's macros were sourced from.
     * `null` only for `water`, which has no meaningful nutrient profile.
     */
    val usdaFdcId: Int? = null,
    val usdaDescription: String,
    val caloriesKcal: Double,
    val carbohydratesG: Double,
    val fiberG: Double,
    val proteinG: Double,
    val totalFatG: Double,
    val saturatedFatG: Double,
    /**
     * `"USDA"` for a direct, verified FDC match (including reasoned
     * proxies, e.g. diastatic malt via barley malt flour); `"USDA Branded
     * Food"` for a specific real commercial product used as the closest
     * available match; `"BreadIQ Standard"` for an internally-derived
     * approximation with no USDA entry at all.
     */
    val dataSource: String,
)

object NutritionInfoCatalog {
    /**
     * All values per 100g of the ingredient, verified against USDA
     * FoodData Central (see [NutritionInfo]'s doc comment for method).
     */
    val all: List<NutritionInfo> = listOf(
        // Flours
        NutritionInfo(key = "bread", label = "Bread Flour", usdaFdcId = 168896, usdaDescription = "Wheat flour, white, bread, enriched", caloriesKcal = 361.0, carbohydratesG = 72.5, fiberG = 2.4, proteinG = 12.0, totalFatG = 1.7, saturatedFatG = 0.24, dataSource = "USDA"),
        NutritionInfo(key = "all_purpose", label = "All-Purpose Flour", usdaFdcId = 168936, usdaDescription = "Wheat flour, white, all-purpose, enriched, unbleached", caloriesKcal = 364.0, carbohydratesG = 76.3, fiberG = 2.7, proteinG = 10.3, totalFatG = 0.98, saturatedFatG = 0.16, dataSource = "USDA"),
        NutritionInfo(key = "00", label = "00 Flour", usdaFdcId = 168936, usdaDescription = "Wheat flour, white, all-purpose, enriched, unbleached (no distinct Italian 00 flour entry exists in USDA FDC — closest real equivalent used)", caloriesKcal = 364.0, carbohydratesG = 76.3, fiberG = 2.7, proteinG = 10.3, totalFatG = 0.98, saturatedFatG = 0.16, dataSource = "BreadIQ Standard"),
        NutritionInfo(key = "semolina", label = "Semolina Flour", usdaFdcId = 168933, usdaDescription = "Semolina, unenriched", caloriesKcal = 360.0, carbohydratesG = 72.8, fiberG = 3.9, proteinG = 12.7, totalFatG = 1.1, saturatedFatG = 0.15, dataSource = "USDA"),
        NutritionInfo(key = "whole_wheat", label = "Whole Wheat Flour", usdaFdcId = 168893, usdaDescription = "Wheat flour, whole-grain", caloriesKcal = 340.0, carbohydratesG = 72.0, fiberG = 10.7, proteinG = 13.2, totalFatG = 2.5, saturatedFatG = 0.43, dataSource = "USDA"),
        NutritionInfo(key = "rye", label = "Dark Rye Flour", usdaFdcId = 168885, usdaDescription = "Rye flour, dark", caloriesKcal = 325.0, carbohydratesG = 68.6, fiberG = 23.8, proteinG = 15.9, totalFatG = 2.2, saturatedFatG = 0.27, dataSource = "USDA"),
        NutritionInfo(key = "spelt", label = "Spelt Flour", usdaFdcId = 2003587, usdaDescription = "Spelt flour", caloriesKcal = 364.0, carbohydratesG = 70.7, fiberG = 9.3, proteinG = 14.5, totalFatG = 2.5, saturatedFatG = 0.4, dataSource = "USDA (saturated fat estimated — not reported in source; based on total-fat profile similarity to whole wheat)"),
        NutritionInfo(key = "einkorn", label = "Einkorn Flour", usdaFdcId = 2710827, usdaDescription = "Einkorn, grain, dry, raw", caloriesKcal = 369.0, carbohydratesG = 68.7, fiberG = 8.9, proteinG = 15.1, totalFatG = 3.8, saturatedFatG = 0.6, dataSource = "USDA (saturated fat estimated — not reported in source)"),

        // Fat, Salt, Yeast
        NutritionInfo(key = "fat", label = "Fat / Olive Oil", usdaFdcId = 171413, usdaDescription = "Oil, olive, salad or cooking", caloriesKcal = 884.0, carbohydratesG = 0.0, fiberG = 0.0, proteinG = 0.0, totalFatG = 100.0, saturatedFatG = 13.8, dataSource = "USDA"),
        NutritionInfo(key = "salt", label = "Kosher Salt", usdaFdcId = null, usdaDescription = "Salt, table (sodium chloride — no caloric or macronutrient content)", caloriesKcal = 0.0, carbohydratesG = 0.0, fiberG = 0.0, proteinG = 0.0, totalFatG = 0.0, saturatedFatG = 0.0, dataSource = "USDA"),
        NutritionInfo(key = "instant", label = "Instant Yeast", usdaFdcId = 175043, usdaDescription = "Leavening agents, yeast, baker's, active dry (USDA has no separate \"instant\" yeast entry — nutritionally equivalent)", caloriesKcal = 325.0, carbohydratesG = 40.0, fiberG = 27.5, proteinG = 40.0, totalFatG = 7.5, saturatedFatG = 1.0, dataSource = "USDA"),
        NutritionInfo(key = "active_dry", label = "Active Dry Yeast", usdaFdcId = 175043, usdaDescription = "Leavening agents, yeast, baker's, active dry", caloriesKcal = 325.0, carbohydratesG = 40.0, fiberG = 27.5, proteinG = 40.0, totalFatG = 7.5, saturatedFatG = 1.0, dataSource = "USDA"),
        NutritionInfo(key = "fresh", label = "Fresh Yeast", usdaFdcId = 175042, usdaDescription = "Leavening agents, yeast, baker's, compressed", caloriesKcal = 106.0, carbohydratesG = 18.2, fiberG = 8.2, proteinG = 8.2, totalFatG = 1.9, saturatedFatG = 0.24, dataSource = "USDA"),
        NutritionInfo(key = "sourdough", label = "Sourdough Starter", usdaFdcId = null, usdaDescription = "Modeled as 50% all-purpose flour + 50% water by weight (100%-hydration starter) — half of all_purpose's per-100g values", caloriesKcal = 182.0, carbohydratesG = 38.2, fiberG = 1.4, proteinG = 5.2, totalFatG = 0.5, saturatedFatG = 0.1, dataSource = "BreadIQ Standard"),

        // Sweeteners
        NutritionInfo(key = "sugar", label = "Granulated Sugar", usdaFdcId = 169655, usdaDescription = "Sugars, granulated", caloriesKcal = 387.0, carbohydratesG = 100.0, fiberG = 0.0, proteinG = 0.0, totalFatG = 0.0, saturatedFatG = 0.0, dataSource = "USDA"),
        NutritionInfo(key = "honey", label = "Honey", usdaFdcId = 169640, usdaDescription = "Honey", caloriesKcal = 304.0, carbohydratesG = 82.4, fiberG = 0.2, proteinG = 0.3, totalFatG = 0.0, saturatedFatG = 0.0, dataSource = "USDA"),
        NutritionInfo(key = "barley_malt", label = "Barley Malt Syrup", usdaFdcId = 384538, usdaDescription = "Eden Organic — Traditional Barley Malt Syrup (no generic USDA SR entry for malt syrup exists; real Branded Food product used as closest confirmed match)", caloriesKcal = 286.0, carbohydratesG = 66.7, fiberG = 0.0, proteinG = 4.8, totalFatG = 0.0, saturatedFatG = 0.0, dataSource = "USDA Branded Food"),
        NutritionInfo(key = "molasses", label = "Molasses", usdaFdcId = 168820, usdaDescription = "Molasses", caloriesKcal = 290.0, carbohydratesG = 74.7, fiberG = 0.0, proteinG = 0.0, totalFatG = 0.1, saturatedFatG = 0.02, dataSource = "USDA"),
        NutritionInfo(key = "diastatic_malt", label = "Diastatic Malt Powder", usdaFdcId = 169740, usdaDescription = "Barley malt flour (diastatic malt powder is milled sprouted barley — same base ingredient)", caloriesKcal = 361.0, carbohydratesG = 78.3, fiberG = 7.1, proteinG = 10.3, totalFatG = 1.8, saturatedFatG = 0.39, dataSource = "USDA"),

        // Dairy & Eggs
        NutritionInfo(key = "eggs", label = "Large Eggs", usdaFdcId = 171287, usdaDescription = "Egg, whole, raw, fresh", caloriesKcal = 143.0, carbohydratesG = 0.72, fiberG = 0.0, proteinG = 12.6, totalFatG = 9.5, saturatedFatG = 3.1, dataSource = "USDA"),
        NutritionInfo(key = "milk", label = "Whole Milk", usdaFdcId = 746782, usdaDescription = "Milk, whole, 3.25% milkfat, with added vitamin D", caloriesKcal = 60.0, carbohydratesG = 4.6, fiberG = 0.0, proteinG = 3.3, totalFatG = 3.2, saturatedFatG = 1.9, dataSource = "USDA"),
        NutritionInfo(key = "butter", label = "Unsalted Butter", usdaFdcId = 173430, usdaDescription = "Butter, without salt", caloriesKcal = 717.0, carbohydratesG = 0.06, fiberG = 0.0, proteinG = 0.85, totalFatG = 81.1, saturatedFatG = 50.5, dataSource = "USDA"),

        // Import-only extras (recognized by the import classifier, no calculator slot or reference price)
        NutritionInfo(key = "lard", label = "Lard", usdaFdcId = 171401, usdaDescription = "Lard", caloriesKcal = 885.0, carbohydratesG = 0.0, fiberG = 0.0, proteinG = 0.0, totalFatG = 98.5, saturatedFatG = 38.5, dataSource = "USDA"),
        NutritionInfo(key = "vegetable_oil", label = "Vegetable / Canola Oil", usdaFdcId = 172336, usdaDescription = "Oil, canola", caloriesKcal = 886.0, carbohydratesG = 0.0, fiberG = 0.0, proteinG = 0.0, totalFatG = 100.0, saturatedFatG = 7.1, dataSource = "USDA"),
        NutritionInfo(key = "brown_sugar", label = "Brown Sugar", usdaFdcId = 168833, usdaDescription = "Sugars, brown", caloriesKcal = 380.0, carbohydratesG = 98.1, fiberG = 0.0, proteinG = 0.12, totalFatG = 0.0, saturatedFatG = 0.0, dataSource = "USDA"),
        NutritionInfo(key = "powdered_sugar", label = "Powdered / Confectioners' Sugar", usdaFdcId = 169656, usdaDescription = "Sugars, powdered", caloriesKcal = 389.0, carbohydratesG = 99.8, fiberG = 0.0, proteinG = 0.0, totalFatG = 0.0, saturatedFatG = 0.0, dataSource = "USDA"),
        NutritionInfo(key = "buttermilk", label = "Buttermilk", usdaFdcId = 170874, usdaDescription = "Milk, buttermilk, fluid, cultured, lowfat", caloriesKcal = 40.0, carbohydratesG = 4.8, fiberG = 0.0, proteinG = 3.3, totalFatG = 1.1, saturatedFatG = 0.7, dataSource = "USDA"),
        NutritionInfo(key = "heavy_cream", label = "Heavy Cream", usdaFdcId = 170859, usdaDescription = "Cream, fluid, heavy whipping", caloriesKcal = 340.0, carbohydratesG = 2.8, fiberG = 0.0, proteinG = 2.8, totalFatG = 36.1, saturatedFatG = 23.0, dataSource = "USDA"),
        NutritionInfo(key = "egg_yolk", label = "Egg Yolk", usdaFdcId = 172184, usdaDescription = "Egg, yolk, raw, fresh", caloriesKcal = 322.0, carbohydratesG = 3.6, fiberG = 0.0, proteinG = 15.9, totalFatG = 26.5, saturatedFatG = 9.6, dataSource = "USDA"),

        // Water
        NutritionInfo(key = "water", label = "Water", usdaFdcId = null, usdaDescription = "N/A — water has no macronutrient content by definition", caloriesKcal = 0.0, carbohydratesG = 0.0, fiberG = 0.0, proteinG = 0.0, totalFatG = 0.0, saturatedFatG = 0.0, dataSource = "N/A"),
    )

    /** Convenience lookup by app key (e.g. `"all_purpose"`, `"egg_yolk"`). */
    fun info(forKey: String): NutritionInfo? = all.firstOrNull { it.key == forKey }
}
