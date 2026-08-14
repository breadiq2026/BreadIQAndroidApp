package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Screens/CalculatorScreen.swift` — its
 * module-level static catalogs (`calculator.tsx`'s own module-level
 * consts, one port removed). Split into `model/` rather than kept
 * inline in the screen file, matching this port's existing convention
 * for static reference catalogs ([BreadStyleCatalog], [LoafShapeCatalog]).
 */
data class FlourTypeOption(val value: String, val label: String, val absorptionAdj: Double, val note: String)

val calculatorFlourTypes: List<FlourTypeOption> = listOf(
    FlourTypeOption(value = "bread", label = "Bread Flour", absorptionAdj = 0.0, note = "12–14% protein — the backbone of most lean breads."),
    FlourTypeOption(value = "all_purpose", label = "All-Purpose Flour", absorptionAdj = -5.0, note = "10–12% protein — versatile, widely available. Best for brioche, soft rolls, and pizza. Slightly less water absorption than bread flour."),
    FlourTypeOption(value = "00", label = "00 Flour", absorptionAdj = -2.0, note = "Silky texture, lower protein. Classic for focaccia and pizza."),
    FlourTypeOption(value = "semolina", label = "Semolina (Fine Durum)", absorptionAdj = 15.0, note = "Golden color, nutty flavor. Absorbs ~1.5% more water per 10%."),
    FlourTypeOption(value = "whole_wheat", label = "Whole Wheat", absorptionAdj = 40.0, note = "Earthy, nutty flavor. Absorbs ~4% more water per 10% inclusion."),
    FlourTypeOption(value = "rye", label = "Dark Rye", absorptionAdj = 40.0, note = "Deep, complex flavor. Absorbs ~4% more water per 10%."),
    FlourTypeOption(value = "spelt", label = "Spelt", absorptionAdj = -15.0, note = "Nutty, sweet. Fragile gluten — reduce mix time, handle gently."),
    FlourTypeOption(value = "einkorn", label = "Einkorn", absorptionAdj = -15.0, note = "Rich, nutty. Very weak gluten. Best at 15–20% in a blend."),
)

data class FlourBlendTemplate(val label: String, val blend: List<FlourBlendEntry>)

val flourBlendTemplates: List<FlourBlendTemplate> = listOf(
    FlourBlendTemplate(label = "Bread Flour", blend = listOf(FlourBlendEntry(type = "bread", percent = 100.0))),
    FlourBlendTemplate(label = "All-Purpose", blend = listOf(FlourBlendEntry(type = "all_purpose", percent = 100.0))),
    FlourBlendTemplate(label = "Semolina", blend = listOf(FlourBlendEntry(type = "bread", percent = 70.0), FlourBlendEntry(type = "semolina", percent = 30.0))),
    FlourBlendTemplate(label = "Whole Wheat", blend = listOf(FlourBlendEntry(type = "bread", percent = 80.0), FlourBlendEntry(type = "whole_wheat", percent = 20.0))),
    FlourBlendTemplate(label = "Rye", blend = listOf(FlourBlendEntry(type = "bread", percent = 80.0), FlourBlendEntry(type = "rye", percent = 20.0))),
    FlourBlendTemplate(label = "00 Flour", blend = listOf(FlourBlendEntry(type = "00", percent = 100.0))),
    FlourBlendTemplate(label = "Spelt", blend = listOf(FlourBlendEntry(type = "bread", percent = 70.0), FlourBlendEntry(type = "spelt", percent = 30.0))),
    FlourBlendTemplate(label = "Einkorn", blend = listOf(FlourBlendEntry(type = "bread", percent = 80.0), FlourBlendEntry(type = "einkorn", percent = 20.0))),
)

data class SweetenerOption(val value: String, val label: String, val waterContent: Double, val note: String)

val calculatorSweetenerTypes: List<SweetenerOption> = listOf(
    SweetenerOption(value = "granulated_sugar", label = "Granulated Sugar", waterContent = 0.0, note = "Fine sugar — adds sweetness, feeds yeast at low %. Dry, no hydration adjustment."),
    SweetenerOption(value = "honey", label = "Honey", waterContent = 0.17, note = "~17% water — free water is reduced to preserve target hydration."),
    SweetenerOption(value = "barley_malt", label = "Barley Malt Syrup", waterContent = 0.25, note = "~25% water. Powerful fermentation accelerator — significantly shorter proof times."),
    SweetenerOption(value = "molasses", label = "Molasses", waterContent = 0.22, note = "~22% water. Deep bittersweet flavor, dense moist crumb."),
)

data class YeastOption(val value: String, val label: String, val factor: Double, val note: String)

val calculatorYeastTypes: List<YeastOption> = listOf(
    YeastOption(value = "instant", label = "Instant", factor = 1.0, note = "Mix directly into flour. No activation or proofing needed."),
    YeastOption(value = "active_dry", label = "Active Dry", factor = 1.25, note = "Dissolve in warm water (105°F) for 5–10 min until foamy before use."),
    YeastOption(value = "fresh", label = "Fresh / Cake", factor = 3.0, note = "Crumble directly into flour or dissolve in a small amount of warm water."),
)

data class PrefermentInfo(val label: String, val hydrationIdeal: Double, val flourPercentSuggested: Double, val hydrationLabel: String, val description: String)

/**
 * `levain` exists in the source's `PREFERMENT_TYPES` table but is
 * filtered out of the dropdown (`.filter(([k]) => k !== "levain")`) —
 * omitted here entirely rather than kept dead, matching the iOS port's
 * own decision (nothing else reads it).
 */
val prefermentTypes: Map<String, PrefermentInfo> = mapOf(
    "poolish" to PrefermentInfo(label = "Poolish", hydrationIdeal = 100.0, flourPercentSuggested = 40.0, hydrationLabel = "100% (equal parts flour + water)", description = "A French wet pre-ferment. Mix equal weights flour and water with a pinch of yeast, ferment 12–16h at room temp. Produces open crumb and mild tang."),
    "biga" to PrefermentInfo(label = "Biga", hydrationIdeal = 55.0, flourPercentSuggested = 40.0, hydrationLabel = "50–60% (stiff)", description = "An Italian stiff pre-ferment. Mixed with very little yeast, fermented 12–24h cool and slow. Complex crumb with creamy flavor."),
)

data class MaltGuidance(val range: String, val ideal: String)

val maltGuidance: Map<String, MaltGuidance> = mapOf(
    "baguette" to MaltGuidance("0.3–0.5%", "0.4%"),
    "artisan" to MaltGuidance("0.1–0.3%", "0.2%"),
    "country" to MaltGuidance("0.2–0.4%", "0.3%"),
    "ciabatta" to MaltGuidance("0.1–0.3%", "0.2%"),
    "focaccia" to MaltGuidance("0.1–0.2%", "0.15%"),
    "soft_roll" to MaltGuidance("0.2–0.4%", "0.3%"),
)

val calculatorCardTitles: List<String> = listOf(
    "What would you like to make?",
    "How do you want to build it?",
    "Fermentation",
    "Environment",
    "Calculate",
)
