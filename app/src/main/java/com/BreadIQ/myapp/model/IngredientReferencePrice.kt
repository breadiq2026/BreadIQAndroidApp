package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/IngredientReferencePrice.swift`.
 *
 * One bundled reference-price entry for the Ingredient Costs feature — a
 * baseline $/g figure sourced from a specific product/retailer, used
 * whenever the user hasn't set their own custom price for that ingredient.
 *
 * Ground truth (per the iOS port): `INGREDIENTS` in
 * `breadiq-mobile/app/ingredient-costs.tsx`, consolidated with the source
 * app's separate `CostEstimator` cost table into one 22-entry catalog (was
 * 18 entries before that merge — see the iOS file's doc comment for the
 * full reasoning, including why `maple_syrup` was dropped rather than
 * folded in).
 *
 * Modeled as a plain value type plus a static catalog, not a persisted
 * entity: bundled, ship-time-constant reference data with no persistent
 * identity. Contrast with [IngredientPriceOverride] below, which genuinely
 * is user-editable data.
 */
data class IngredientReferencePrice(
    val key: String,
    val label: String,
    val category: String,
    val pricePerGram: Double,
    val sourceCitation: String,
)

object IngredientReferencePriceCatalog {
    val all: List<IngredientReferencePrice> = listOf(
        IngredientReferencePrice(key = "bread", label = "Bread Flour", category = "Flours", pricePerGram = 0.00256, sourceCitation = "King Arthur, 5 lb"),
        IngredientReferencePrice(key = "all_purpose", label = "All-Purpose Flour", category = "Flours", pricePerGram = 0.00198, sourceCitation = "King Arthur, 5 lb"),
        IngredientReferencePrice(key = "00", label = "00 Flour", category = "Flours", pricePerGram = 0.00450, sourceCitation = "Caputo Pizzeria, 2 kg"),
        IngredientReferencePrice(key = "semolina", label = "Semolina Flour", category = "Flours", pricePerGram = 0.00286, sourceCitation = "Bob's Red Mill, 5 lb"),
        IngredientReferencePrice(key = "whole_wheat", label = "Whole Wheat Flour", category = "Flours", pricePerGram = 0.00264, sourceCitation = "King Arthur, 5 lb"),
        IngredientReferencePrice(key = "rye", label = "Dark Rye Flour", category = "Flours", pricePerGram = 0.00330, sourceCitation = "Bob's Red Mill, 5 lb"),
        IngredientReferencePrice(key = "spelt", label = "Spelt Flour", category = "Flours", pricePerGram = 0.00396, sourceCitation = "Bob's Red Mill, 5 lb"),
        IngredientReferencePrice(key = "einkorn", label = "Einkorn Flour", category = "Flours", pricePerGram = 0.01100, sourceCitation = "Jovial, 32 oz"),
        IngredientReferencePrice(key = "fat", label = "Fat / Olive Oil", category = "Fats & Oils", pricePerGram = 0.01400, sourceCitation = "Colavita EVOO, 1 L"),
        IngredientReferencePrice(key = "salt", label = "Kosher Salt", category = "Salt", pricePerGram = 0.00173, sourceCitation = "Diamond Crystal, 3 lb"),
        IngredientReferencePrice(key = "instant", label = "Instant Yeast", category = "Yeast", pricePerGram = 0.01764, sourceCitation = "SAF Instant, 1 lb"),
        IngredientReferencePrice(key = "active_dry", label = "Active Dry Yeast", category = "Yeast", pricePerGram = 0.02200, sourceCitation = "Fleischmann's, 4 oz"),
        IngredientReferencePrice(key = "fresh", label = "Fresh Yeast", category = "Yeast", pricePerGram = 0.02640, sourceCitation = "Fleischmann's, 2 oz"),
        IngredientReferencePrice(key = "sourdough", label = "Sourdough Starter", category = "Yeast", pricePerGram = 0.00100, sourceCitation = "Maintenance cost"),
        IngredientReferencePrice(key = "sugar", label = "Granulated Sugar", category = "Sweeteners", pricePerGram = 0.00099, sourceCitation = "C&H, 4 lb"),
        IngredientReferencePrice(key = "honey", label = "Honey", category = "Sweeteners", pricePerGram = 0.01190, sourceCitation = "Pure Honey, 32 oz"),
        IngredientReferencePrice(key = "barley_malt", label = "Barley Malt Syrup", category = "Sweeteners", pricePerGram = 0.02037, sourceCitation = "Avg. Amazon listings"),
        IngredientReferencePrice(key = "molasses", label = "Molasses", category = "Sweeteners", pricePerGram = 0.00900, sourceCitation = "Unsulfured, grocery avg"),
        IngredientReferencePrice(key = "diastatic_malt", label = "Diastatic Malt Powder", category = "Malt", pricePerGram = 0.00529, sourceCitation = "Anthony's, 1.5 lb"),
        IngredientReferencePrice(key = "eggs", label = "Large Eggs", category = "Dairy & Eggs", pricePerGram = 0.00584, sourceCitation = "~\$3.50/dozen, 50g/egg"),
        IngredientReferencePrice(key = "milk", label = "Whole Milk", category = "Dairy & Eggs", pricePerGram = 0.00119, sourceCitation = "~\$4.50/gallon"),
        IngredientReferencePrice(key = "butter", label = "Unsalted Butter", category = "Dairy & Eggs", pricePerGram = 0.01101, sourceCitation = "~\$5.00/lb"),
    )
}

/**
 * A user's custom override of one ingredient's reference price, always
 * stored in $/g (the app computes/persists in price-per-gram throughout;
 * only the UI displays $/lb, converting via `pricePerGram * 453.592`).
 *
 * Server-authoritative in the source app today (`GET/PUT/DELETE
 * /api/ingredient-costs/:key`, no local cache at all) — unlike
 * [IngredientReferencePrice] above, this is genuinely user-editable,
 * per-ingredient data that should be cached locally for the offline-first
 * goal, with sync layered on once the Supabase client lands (see
 * PORTING_PLAN.md step 2). Kept as a plain value type for now; becomes a
 * real Room entity once the local-persistence schema is designed in step 5.
 */
data class IngredientPriceOverride(
    val ingredientKey: String,
    val pricePerGram: Double,
)
