package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/LoafShape.swift`.
 *
 * A specific loaf/roll/piece format within a bread style — e.g. `"12"
 * Baguette"` vs `"Dinner Rolls 1.5 oz (per 13)"`, both under the baguette
 * style. `flourPerLoaf` is the per-loaf flour weight anchor the formula
 * calculator scales everything else from.
 *
 * Ground truth (per the iOS port): `LOAF_SHAPES` in
 * `breadiq-mobile/app/(tabs)/calculator.tsx`. 46 entries — not 38, an
 * earlier iOS-side roadmap guess corrected during that port; see the iOS
 * file's doc comment for the full drift-vs-`bread-lab` writeup.
 *
 * Modeled as a plain value type plus a static catalog, not a persisted
 * entity: bundled, ship-time-constant reference data with no persistent
 * identity and nothing to independently query or relate.
 */
data class LoafShape(
    val value: String,
    val label: String,
    val flourPerLoaf: Int,
    val styles: List<String>,
    val dimensions: String,
    val finishedWeight: String,
)

object LoafShapeCatalog {
    val all: List<LoafShape> = listOf(
        // Baguette
        LoafShape(value = "baguette_6", label = "6\" Demi-Baguette", flourPerLoaf = 84, styles = listOf("baguette"), dimensions = "~6\" long × 2–2.5\" wide", finishedWeight = "~110g"),
        LoafShape(value = "baguette_12", label = "12\" Baguette", flourPerLoaf = 179, styles = listOf("baguette"), dimensions = "~12\" long × 2.5–3\" wide", finishedWeight = "~235g"),
        LoafShape(value = "baguette_15", label = "15\" Baguette", flourPerLoaf = 254, styles = listOf("baguette"), dimensions = "~15\" long × 3–3.5\" wide", finishedWeight = "~330g"),
        LoafShape(value = "baguette_18", label = "18\" Batard (Long)", flourPerLoaf = 343, styles = listOf("baguette"), dimensions = "~18\" long × 3–3.5\" wide", finishedWeight = "~445g"),
        LoafShape(value = "baguette_rolls_15oz", label = "Dinner Rolls 1.5 oz (per 13)", flourPerLoaf = 329, styles = listOf("baguette"), dimensions = "~3\" round per roll", finishedWeight = "~37g × 13"),
        LoafShape(value = "baguette_rolls_25oz", label = "Petite Baguette Rolls 2.5 oz (per 13)", flourPerLoaf = 549, styles = listOf("baguette"), dimensions = "~6–7\" long per roll", finishedWeight = "~62g × 13"),
        // Artisan / Country
        LoafShape(value = "boule", label = "8\" Round Boule", flourPerLoaf = 373, styles = listOf("artisan", "country"), dimensions = "~8\" diameter × 3–4\" tall", finishedWeight = "~545g"),
        LoafShape(value = "boule_8_oval", label = "8\" Oval Boule", flourPerLoaf = 259, styles = listOf("artisan"), dimensions = "~8–9\" long × 4–5\" wide", finishedWeight = "~413g"),
        LoafShape(value = "boule_10_round", label = "10\" Round Boule", flourPerLoaf = 546, styles = listOf("artisan"), dimensions = "~10\" diameter × 4–5\" tall", finishedWeight = "~870g"),
        LoafShape(value = "boule_10_oval", label = "10\" Oval Boule", flourPerLoaf = 423, styles = listOf("artisan"), dimensions = "~10–11\" long × 5–6\" wide", finishedWeight = "~674g"),
        LoafShape(value = "batard", label = "15\" Batard", flourPerLoaf = 375, styles = listOf("country"), dimensions = "~12–13\" long × 4–5\" wide", finishedWeight = "~580g"),
        LoafShape(value = "pullman_country", label = "9\" Sandwich Loaf", flourPerLoaf = 477, styles = listOf("country"), dimensions = "9\" × 4\" × 4\" pan", finishedWeight = "~696g"),
        LoafShape(value = "artisan_rolls_15oz", label = "Artisan Rolls 1.5 oz (per 13)", flourPerLoaf = 314, styles = listOf("artisan", "country"), dimensions = "~3–4\" round", finishedWeight = "~37g × 13"),
        LoafShape(value = "artisan_rolls_25oz", label = "Artisan Rolls 2.5 oz (per 13)", flourPerLoaf = 525, styles = listOf("artisan", "country"), dimensions = "~4–5\" long", finishedWeight = "~62g × 13"),
        // Ciabatta
        LoafShape(value = "ciabatta_loaf", label = "10\"×4\" Loaf", flourPerLoaf = 175, styles = listOf("ciabatta"), dimensions = "~10\" long × 4\" wide", finishedWeight = "~275g"),
        LoafShape(value = "ciabatta_panino_6", label = "4\"×4\" Panino Rolls (per 7)", flourPerLoaf = 489, styles = listOf("ciabatta"), dimensions = "~4\" × 4\" per roll", finishedWeight = "~113g × 7"),
        LoafShape(value = "ciabatta_rolls_15oz", label = "Piccolo Rolls 1.5 oz (per 13)", flourPerLoaf = 297, styles = listOf("ciabatta"), dimensions = "~3\" round", finishedWeight = "~37g × 13"),
        LoafShape(value = "ciabatta_rolls_25oz", label = "Piccolo Rolls 2.5 oz (per 13)", flourPerLoaf = 495, styles = listOf("ciabatta"), dimensions = "~4–5\" long", finishedWeight = "~62g × 13"),
        // Focaccia
        LoafShape(value = "focaccia_10x15", label = "10\"×15\" Sheet Pan", flourPerLoaf = 509, styles = listOf("focaccia"), dimensions = "10\" × 15\" jelly roll", finishedWeight = "~1,050g dough"),
        LoafShape(value = "focaccia_13x18", label = "13\"×18\" Half Sheet Pan", flourPerLoaf = 794, styles = listOf("focaccia"), dimensions = "13\" × 18\" half sheet", finishedWeight = "~1,640g dough"),
        LoafShape(value = "focaccia_9x13", label = "9\"×13\" Baking Dish", flourPerLoaf = 397, styles = listOf("focaccia"), dimensions = "9\" × 13\" dish", finishedWeight = "~820g dough"),
        LoafShape(value = "focaccia_9round", label = "9\" Round Pan", flourPerLoaf = 240, styles = listOf("focaccia"), dimensions = "9\" round pan", finishedWeight = "~495g dough"),
        // NY/Roman Pizza
        LoafShape(value = "pizza_8in_ny", label = "8\" Individual Pizza", flourPerLoaf = 77, styles = listOf("pizza_ny"), dimensions = "8\" diameter", finishedWeight = "~130g dough"),
        LoafShape(value = "pizza_12in_ny", label = "12\" Pizza", flourPerLoaf = 169, styles = listOf("pizza_ny"), dimensions = "12\" diameter", finishedWeight = "~285g dough"),
        LoafShape(value = "pizza_16in_ny", label = "16\" Large Pizza", flourPerLoaf = 297, styles = listOf("pizza_ny"), dimensions = "16\" diameter", finishedWeight = "~500g dough"),
        // Soft Roll
        LoafShape(value = "pullman_soft", label = "9\" Sandwich Loaf", flourPerLoaf = 510, styles = listOf("soft_roll"), dimensions = "9\" × 4\" × 4\" pan", finishedWeight = "~790g"),
        LoafShape(value = "soft_rolls_15oz", label = "Dinner Rolls 1.5 oz (per 13)", flourPerLoaf = 314, styles = listOf("soft_roll"), dimensions = "~3\" round", finishedWeight = "~37g × 13"),
        LoafShape(value = "soft_rolls_25oz", label = "Sandwich Rolls 2.5 oz (per 13)", flourPerLoaf = 523, styles = listOf("soft_roll"), dimensions = "~4–5\" long", finishedWeight = "~62g × 13"),
        LoafShape(value = "soft_hoagie_6in", label = "6\" Hoagie / Sub Rolls (per 6)", flourPerLoaf = 436, styles = listOf("soft_roll"), dimensions = "~6\" long per roll", finishedWeight = "~128g × 6"),
        LoafShape(value = "soft_hoagie_8in", label = "8\" Hoagie / Sub Rolls (per 6)", flourPerLoaf = 579, styles = listOf("soft_roll"), dimensions = "~8\" long per roll", finishedWeight = "~170g × 6"),
        LoafShape(value = "soft_burger_bun", label = "Burger Buns 3.5 oz (per 6)", flourPerLoaf = 339, styles = listOf("soft_roll"), dimensions = "~3.5–4\" round disc", finishedWeight = "~99g × 6"),
        // Neapolitan Pizza
        LoafShape(value = "pizza_8in_neo", label = "8\" Individual Pizza", flourPerLoaf = 72, styles = listOf("pizza_neo"), dimensions = "8\" diameter", finishedWeight = "~120g dough"),
        LoafShape(value = "pizza_12in_neo", label = "12\" Pizza", flourPerLoaf = 158, styles = listOf("pizza_neo"), dimensions = "12\" diameter", finishedWeight = "~262g dough"),
        LoafShape(value = "pizza_16in_neo", label = "16\" Large Pizza", flourPerLoaf = 277, styles = listOf("pizza_neo"), dimensions = "16\" diameter", finishedWeight = "~460g dough"),
        // Brioche
        LoafShape(value = "brioche_loaf_9in", label = "9\" Sandwich Loaf", flourPerLoaf = 400, styles = listOf("brioche"), dimensions = "9\" × 5\" × 4\" pan", finishedWeight = "~790g"),
        LoafShape(value = "brioche_rolls_15oz", label = "Dinner Rolls 1.5 oz (per dozen)", flourPerLoaf = 217, styles = listOf("brioche"), dimensions = "~3\" round per roll", finishedWeight = "~40g × 12"),
        LoafShape(value = "brioche_rolls_25oz", label = "Dinner Rolls 2.5 oz (per dozen)", flourPerLoaf = 362, styles = listOf("brioche"), dimensions = "~3.5\" round per roll", finishedWeight = "~67g × 12"),
        LoafShape(value = "brioche_burger_bun_4oz", label = "Burger Buns 4 oz (per 6)", flourPerLoaf = 301, styles = listOf("brioche"), dimensions = "~4\" round disc", finishedWeight = "~118g × 6"),
        // Bagel
        LoafShape(value = "bagel_std_6pk", label = "Standard Bagels (per 6)", flourPerLoaf = 430, styles = listOf("bagel"), dimensions = "~4\" diameter", finishedWeight = "~92g × 6"),
        LoafShape(value = "bagel_mini_6pk", label = "Mini Bagels (per 6)", flourPerLoaf = 206, styles = listOf("bagel"), dimensions = "~3\" diameter", finishedWeight = "~44g × 6"),
        // English muffin
        LoafShape(value = "em_std_12pk", label = "Standard Muffins 3.5\" (per 12)", flourPerLoaf = 587, styles = listOf("english_muffin"), dimensions = "3.5\" rings × 1\"", finishedWeight = "~76g × 12"),
        LoafShape(value = "em_large_12pk", label = "Large Muffins 4\" (per 12)", flourPerLoaf = 803, styles = listOf("english_muffin"), dimensions = "4\" rings × 1.25\"", finishedWeight = "~104g × 12"),
        // Pretzel
        LoafShape(value = "pretzel_std_6pk", label = "Standard Soft Pretzels (per 6)", flourPerLoaf = 664, styles = listOf("pretzel"), dimensions = "~8\" wide twist", finishedWeight = "~150g × 6"),
        LoafShape(value = "pretzel_small_6pk", label = "Small Pretzels (per 6)", flourPerLoaf = 323, styles = listOf("pretzel"), dimensions = "~5\" wide twist", finishedWeight = "~72g × 6"),
        LoafShape(value = "pretzel_sticks_6pk", label = "Pretzel Sticks (per 6)", flourPerLoaf = 152, styles = listOf("pretzel"), dimensions = "~9\" long sticks", finishedWeight = "~34g × 6"),
        LoafShape(value = "pretzel_slider_6pk", label = "Pretzel Slider Buns (per 6)", flourPerLoaf = 171, styles = listOf("pretzel"), dimensions = "~3\" round bun", finishedWeight = "~38g × 6"),
    )
}
