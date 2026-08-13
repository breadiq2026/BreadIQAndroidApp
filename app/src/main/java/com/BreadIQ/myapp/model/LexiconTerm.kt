package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/LexiconTerm.swift`.
 *
 * One sub-bullet item shown beneath a lexicon term's definition — e.g. the
 * three Ciabatta format entries.
 */
data class LexiconDetail(
    val label: String,
    val desc: String,
)

/**
 * A single baking-glossary entry shown on the in-app Lexicon reference
 * screen, grouped by category.
 *
 * Ground truth (per the iOS port): `LEXICON_TERMS` in
 * `breadiq-mobile/lib/lexicon-data.ts`. 32 terms across 6 categories —
 * not 34, an earlier roadmap guess corrected during that port. Categories,
 * in first-appearance order: Dough Archetypes, Handling & Technique,
 * Pre-Ferments, Fermentation Timeline, Sweeteners & Enhancers, Loaf
 * Formats.
 *
 * Modeled as a plain value type plus a static catalog, not a persisted
 * entity: bundled, ship-time-constant reference data with no persistent
 * identity.
 */
data class LexiconTerm(
    val id: String,
    val term: String,
    val category: String,
    val definition: String,
    val details: List<LexiconDetail>? = null,
    val wide: Boolean? = null,
)

object LexiconCatalog {
    val terms: List<LexiconTerm> = listOf(
        // I. Dough Archetypes
        LexiconTerm(
            id = "artisan-sourdough", term = "Artisan / Sourdough Style", category = "Dough Archetypes",
            definition = "A lean dough relying on long fermentation and high hydration. Characterized by a complex, tangy flavor and a robust, chewy crust."
        ),
        LexiconTerm(
            id = "pain-de-campagne", term = "Pain de Campagne", category = "Dough Archetypes",
            definition = "\"Country bread.\" A traditional French hybrid dough incorporating a percentage of whole wheat or rye to produce an earthier flavor and a more rustic, durable crumb."
        ),
        LexiconTerm(
            id = "baguette-dough", term = "Baguette Dough", category = "Dough Archetypes",
            definition = "A high-protein, lean dough designed for a thin, shattered-glass crust and a lightweight, airy interior."
        ),
        LexiconTerm(
            id = "pizza-ny-roman", term = "Pizza Style (NY & Roman)", category = "Dough Archetypes",
            definition = "A versatile, mid-hydration dough that incorporates oil and sugar to allow for a crispy, foldable, or crunchy bake in standard home ovens."
        ),
        LexiconTerm(
            id = "pizza-neapolitan", term = "Pizza Style (Neapolitan)", category = "Dough Archetypes",
            definition = "The \"Purist\" dough. High-heat (700°F+) profile for a soft, charred, \"leopard-spotted\" crust."
        ),
        LexiconTerm(
            id = "ciabatta", term = "Ciabatta", category = "Dough Archetypes",
            definition = "An ultra-high hydration Italian dough known for its massive alveolar (hole) structure and olive oil enrichment."
        ),
        LexiconTerm(
            id = "focaccia", term = "Focaccia", category = "Dough Archetypes",
            definition = "Designed to be baked flat and heavily dressed in olive oil, creating a \"fried\" bottom crust and a soft, dimpled top."
        ),
        LexiconTerm(
            id = "soft-lean-dough", term = "Soft Lean Dough", category = "Dough Archetypes",
            definition = "A versatile base for rolls and buns, engineered for a tight, pillow-soft crumb."
        ),

        // II. Handling & Technique
        LexiconTerm(
            id = "kneading", term = "Kneading", category = "Handling & Technique",
            definition = "The initial process of working the dough to develop the gluten network through mechanical action."
        ),
        LexiconTerm(
            id = "stretch-and-fold", term = "Stretch and Fold", category = "Handling & Technique",
            definition = "The standard method for building strength in medium-hydration doughs by pulling the dough and folding it over itself at set intervals."
        ),
        LexiconTerm(
            id = "coil-fold", term = "Coil Fold", category = "Handling & Technique",
            definition = "A \"heavy-duty\" fold for high-hydration doughs where the dough is lifted from the center, allowing the ends to \"coil\" under to build vertical structure."
        ),
        LexiconTerm(
            id = "degassing", term = "Degassing", category = "Handling & Technique",
            definition = "The intentional process of pressing down or folding the dough to expel excess carbon dioxide. This redistributes yeast and regulates bubble size for a more uniform crumb."
        ),
        LexiconTerm(
            id = "tensioning", term = "Tensioning", category = "Handling & Technique",
            definition = "Creating a taut \"structural skin\" during shaping to ensure a controlled oven spring and a proper crown."
        ),
        LexiconTerm(
            id = "scoring", term = "Scoring", category = "Handling & Technique",
            definition = "Using a lame (razor) to make purposeful cuts that direct expansion, preventing \"blowouts\" at the seams."
        ),

        // III. Pre-Ferments
        LexiconTerm(
            id = "pre-ferment", term = "Pre-Ferment", category = "Pre-Ferments",
            definition = "A portion of flour, water, and yeast mixed ahead of time to develop organic acids and gluten strength, improving flavor and shelf life."
        ),
        LexiconTerm(
            id = "poolish", term = "Poolish", category = "Pre-Ferments",
            definition = "A liquid pre-ferment mixed at 100% hydration and fermented for 8–16 hours. It produces a sweet, nutty flavor and contributes to a lighter, crispier crust."
        ),
        LexiconTerm(
            id = "biga", term = "Biga", category = "Pre-Ferments",
            definition = "A stiff, dough-like pre-ferment mixed at 50–60% hydration and fermented for 12–24 hours. It provides significant structural strength and a characteristic \"sour\" tang."
        ),

        // IV. Fermentation Timeline
        LexiconTerm(
            id = "bulk-fermentation", term = "Bulk Fermentation", category = "Fermentation Timeline",
            definition = "The \"First Rise\" where the majority of flavor and gas volume is established after the initial mix."
        ),
        LexiconTerm(
            id = "cold-ferment", term = "Cold Ferment", category = "Fermentation Timeline",
            definition = "\"Retarding\" the dough in the refrigerator to slow yeast activity while allowing bacteria to develop complex acidity and improve digestibility."
        ),
        LexiconTerm(
            id = "final-proof", term = "Final Proof", category = "Fermentation Timeline",
            definition = "\"The Last Rise\" post-shaping; the final expansion where the dough reaches its peak volume before hitting the oven."
        ),

        // V. Sweeteners & Enhancers
        LexiconTerm(
            id = "fat-lipids", term = "Fat (Lipids)", category = "Sweeteners & Enhancers",
            definition = "Fat acts as a lubricant within the dough, coating gluten strands to create a more tender crumb and a thinner, softer crust. It also increases shelf life by slowing the staling process. Olive oil is the BreadIQ default; lard, butter, or other vegetable oils can substitute at the same ratio."
        ),
        LexiconTerm(
            id = "diastatic-malt", term = "Diastatic Malt", category = "Sweeteners & Enhancers",
            definition = "An enzyme-active powder that converts starches to sugars, boosting yeast activity and creating a deep, dark brown crust."
        ),
        LexiconTerm(
            id = "barley-malt-syrup", term = "Barley Malt Syrup", category = "Sweeteners & Enhancers",
            definition = "A dark, viscous sweetener that provides a savory, malty flavor and the signature \"chew\" for bagels."
        ),
        LexiconTerm(
            id = "granulated-sugar", term = "Granulated Sugar", category = "Sweeteners & Enhancers",
            definition = "Provides clean sweetness and softens the crumb by interfering with gluten development while speeding up browning via the Maillard reaction."
        ),
        LexiconTerm(
            id = "honey", term = "Honey", category = "Sweeteners & Enhancers",
            definition = "Primarily fructose and glucose, honey retains moisture for an exceptionally soft crumb and long shelf life while aggressively speeding up fermentation."
        ),
        LexiconTerm(
            id = "molasses", term = "Molasses", category = "Sweeteners & Enhancers",
            definition = "A heavy, acidic sweetener that introduces a robust flavor and weakens gluten structure slightly for a very moist, tender, and dense crumb."
        ),

        // VI. Loaf Formats
        LexiconTerm(
            id = "boule", term = "Boule", category = "Loaf Formats",
            definition = "A traditional round loaf; the standard for artisan baking."
        ),
        LexiconTerm(
            id = "batard", term = "Batard", category = "Loaf Formats",
            definition = "An oval or \"football\" shape; offers uniform slices ideal for sandwiches."
        ),
        LexiconTerm(
            id = "baguette-shape", term = "Baguette (Shape)", category = "Loaf Formats",
            definition = "A long, thin wand that maximizes the crust-to-crumb ratio."
        ),
        LexiconTerm(
            id = "pullman-loaf", term = "Pullman Loaf", category = "Loaf Formats",
            definition = "Formulated for a 9×4\" open-top format, designed to crown for a traditional domed slice."
        ),
        LexiconTerm(
            id = "focaccia-format", term = "Focaccia (Format)", category = "Loaf Formats",
            definition = "A flat sheet, dimpled to hold oils and aromatics."
        ),
        LexiconTerm(
            id = "ciabatta-formats", term = "Ciabatta Formats", category = "Loaf Formats",
            definition = "Unlike traditional rounded or oval loaves, Ciabatta is a high-hydration \"Slipper\" bread shaped into rectangular or square formats to preserve its internal air structure. BreadIQ currently supports four distinct scaled versions:",
            details = listOf(
                LexiconDetail(label = "10×4\" Loaf", desc = "The standard large format for sharing or slicing."),
                LexiconDetail(label = "4×4\" Panino", desc = "A square cut specifically engineered for individual sandwiches."),
                LexiconDetail(label = "Piccolo (1.5oz & 2.5oz)", desc = "Small-format rolls (\"little ones\") designed for sliders, appetizers, or basket service."),
            )
        ),
    )

    /**
     * Unique categories, in first-appearance order — ports
     * `LEXICON_CATEGORIES = [...new Set(LEXICON_TERMS.map(t => t.category))]`.
     */
    val categories: List<String> = run {
        val seen = LinkedHashSet<String>()
        for (t in terms) seen.add(t.category)
        seen.toList()
    }

    /** Ports `getLexiconTerm(id)`. */
    fun term(id: String): LexiconTerm? = terms.firstOrNull { it.id == id }
}
