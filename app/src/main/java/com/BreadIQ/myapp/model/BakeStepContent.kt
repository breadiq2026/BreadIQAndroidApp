package com.BreadIQ.myapp.model

/** A notification's title + body pair. */
data class NotifCopy(
    val title: String,
    val body: String,
)

/**
 * Ported from the iOS app's `Models/BakeStepContent.swift`.
 *
 * Static bake-step copy — notification titles/bodies and in-app step
 * descriptions, keyed by step label string. Covers dozens of distinct
 * bread-baking step types across all styles (sourdough/artisan, bagel,
 * pretzel, focaccia, brioche, English muffin, baguette, ciabatta
 * pre-ferments, etc.) — the exact dictionary sizes are 37/4/11/42 entries
 * across the four maps below (many step labels recur across multiple
 * dictionaries, e.g. "Bulk Fermentation" appears in three of them).
 *
 * Ground truth (per the iOS port): `lib/bakeStepContent.ts` in the source
 * Expo app.
 *
 * This is the DATA half only: the four lookup maps plus the two generic
 * fold-description strings, as static content. The LOOKUP LOGIC
 * (exact-match-then-prefix-match fallback, coil-fold/oven-preheat wrapper
 * functions, etc.) is a separate, later port — that's behavior, not data.
 *
 * Kept string-keyed (matching the source's `Record<string, NotifCopy>`
 * pattern) rather than introducing a step-kind enum now, same reasoning as
 * the iOS port: doing so prematurely risks getting ~40 case names wrong
 * before the lookup logic (which needs prefix-matching behavior) is even
 * built.
 */
object BakeStepContent {
    /** Fires when a step's timer ends. 37 entries. */
    val stepComplete: Map<String, NotifCopy> = mapOf(
        "Autolyse" to NotifCopy(title = "Autolyse complete.", body = "Add your salt and yeast now. Mix until fully incorporated."),
        "Mix" to NotifCopy(title = "Mixing complete.", body = "Cover the bowl. Bulk fermentation has started."),
        "Bulk Fermentation" to NotifCopy(title = "Bulk fermentation complete.", body = "Time to assess your dough. Look for volume increase, bubbles at the surface, and a jiggly, airy feel. Pre-shape when ready."),
        "Pre-shape" to NotifCopy(title = "Bench rest complete.", body = "Dough has relaxed. Shape your final loaves now."),
        "Bench Rest" to NotifCopy(title = "Bench rest complete.", body = "Dough has relaxed. Proceed to final shaping."),
        "Final Proof" to NotifCopy(title = "Final proof complete.", body = "Do the poke test. Score and load the oven immediately."),
        "Final Proof (Cold)" to NotifCopy(title = "Cold proof complete.", body = "Remove from refrigerator. Score from cold — do not temper first."),
        "Final Proof (post-retard)" to NotifCopy(title = "Final proof complete.", body = "Do the poke test. Score and load the oven immediately."),
        "Cold Ferment" to NotifCopy(title = "Cold ferment complete.", body = "Remove from the refrigerator. Allow to temper at room temperature."),
        "Scale to Pan" to NotifCopy(title = "Panning complete.", body = "Focaccia is in the pan. Cover and let it proof."),
        "Proof & Dimple" to NotifCopy(title = "Time to dimple.", body = "Press your fingers in firmly. Drizzle with oil. Load the oven."),
        "Portion & Ball" to NotifCopy(title = "Portioning complete.", body = "Balls are formed. Cover and rest — do not open them yet."),
        "Recovery" to NotifCopy(title = "Dough has recovered.", body = "Ball tension has relaxed. Stretch and top your pizza now."),
        "Temper & Stretch" to NotifCopy(title = "Ready to stretch.", body = "Cold dough has relaxed. Open your pizza by hand now."),
        "Temper" to NotifCopy(title = "Temper complete.", body = "Dough is at temperature. Shape and load the oven."),
        "Degas & Bench Rest" to NotifCopy(title = "Bench rest started.", body = "Gluten is relaxing. Do not touch for 30 minutes. Prepare your shaping surface."),
        "Divide & Shape (Bagel)" to NotifCopy(title = "Bagels shaped.", body = "Smooth rounds formed, holes opened. Refrigerate immediately — do not leave at room temp."),
        "Cold Proof (Bagel)" to NotifCopy(title = "Cold proof started.", body = "Refrigerate 12–24 hours. Cold develops the characteristic chew and malty depth."),
        "Boil & Top" to NotifCopy(title = "Bagels boiled.", body = "Top immediately — 30 seconds before the surface dries. Load the oven now."),
        "Boil" to NotifCopy(title = "Bagels boiled.", body = "Top immediately — the surface dries in seconds. Load the oven now."),
        "Rest & Alkaline Bath" to NotifCopy(title = "Bath complete.", body = "Transfer to parchment. Score the arch, add coarse salt while still wet. Load the oven immediately."),
        "Alkaline Bath" to NotifCopy(title = "Bath complete.", body = "Transfer to parchment. Score the arch, add coarse pretzel salt while still wet. Load the oven immediately."),
        "Place in Rings" to NotifCopy(title = "Muffins in rings.", body = "Rings filled. Cover loosely. Proof until puffy and domed above the ring edge."),
        "Ring Proof" to NotifCopy(title = "Muffins ready for the griddle.", body = "They're puffy and fill the rings. Preheat your griddle to medium-low — 325°F."),
        "Griddle Cook" to NotifCopy(title = "First side done.", body = "Flip now. 6–8 min per side. Low heat — do not rush the center."),
        "Rope & Shape" to NotifCopy(title = "Pretzels shaped.", body = "Cover loosely. Rest 15–20 min before the bath — let the gluten relax."),
        "Divide & Pre-shape" to NotifCopy(title = "Pre-shaping complete.", body = "Pieces are rounded. Cover and let them relax before final shaping."),
        "Score & Load" to NotifCopy(title = "Oven loaded.", body = "Do not open the door in the first 15 minutes. Steam is building."),
        "Bake" to NotifCopy(title = "Check your bread.", body = "Look for deep color and a hollow sound when you tap the bottom."),
        "Pre-ferment Build" to NotifCopy(title = "Pre-ferment is ready.", body = "Peaked and bubbly. Mix your final dough now."),
        "Ferment Pre-ferment" to NotifCopy(title = "Pre-ferment is ready.", body = "Peaked and bubbly. Scale and mix your final dough now."),
        "Mix Final Dough" to NotifCopy(title = "Final dough mixed.", body = "Cover the bowl. Bulk fermentation has started."),
        "Mix Dough" to NotifCopy(title = "Gluten development complete.", body = "Reduce to medium-low. Begin adding butter one tablespoon at a time."),
        "Incorporate Butter" to NotifCopy(title = "Brioche dough complete.", body = "Smooth, glossy, and pulling clean from the bowl. Cover the bowl. Bulk fermentation has started."),
        "Scale & Mix Preferment" to NotifCopy(title = "Pre-ferment mixed.", body = "Cover and ferment at room temperature. Check in the morning."),
        "Scale Final Dough" to NotifCopy(title = "Final dough ingredients scaled.", body = "All ingredients are ready. Mix your final dough now."),
        "Final Shape" to NotifCopy(title = "Shaping complete.", body = "Shaped loaves are in their vessels. Begin the final proof."),
    )

    /**
     * Fold-specific step-complete variants for "Stretch & Fold"/"Coil
     * Fold" labeled steps, matched by substring against "Set 1".."Set 4".
     * 4 entries.
     */
    val stretchFoldVariants: Map<String, NotifCopy> = mapOf(
        "Set 1" to NotifCopy(title = "Time for fold — set 2 of 3.", body = "Wet hands. Lift, stretch, fold, rotate 90°. Four sides. Thirty seconds."),
        "Set 2" to NotifCopy(title = "Time for fold — set 3 of 3.", body = "Wet hands. Last set. Four sides. Thirty seconds. Cover and leave undisturbed."),
        "Set 3" to NotifCopy(title = "All fold sets complete.", body = "Cover and leave undisturbed for the remainder of bulk fermentation."),
        "Set 4" to NotifCopy(title = "Stretch & fold complete.", body = "All sets done. Cover and leave undisturbed for the rest of bulk."),
    )

    /** Fires 5 minutes before a step ends. 11 entries. */
    val stepPrep: Map<String, NotifCopy> = mapOf(
        "Bulk Fermentation" to NotifCopy(title = "5 minutes remaining in bulk fermentation.", body = "Lightly flour your bench surface. Prepare your bench scraper."),
        "Ring Proof" to NotifCopy(title = "5 minutes until griddle.", body = "Preheat your griddle or cast iron to medium-low now. 325°F surface temperature."),
        "Rope & Shape" to NotifCopy(title = "Almost time to shape.", body = "Prepare your shaping surface. Have your rope target size in mind."),
        "Cold Proof (Bagel)" to NotifCopy(title = "Cold proof almost complete.", body = "Fill a wide pot with water. Bring to a boil. Have your toppings ready."),
        "Bench Rest" to NotifCopy(title = "Almost done resting.", body = "Prepare your banneton or proofing container now."),
        "Final Proof" to NotifCopy(title = "Final proof almost done.", body = "Prepare your scoring lame."),
        "Final Proof (post-retard)" to NotifCopy(title = "Final proof almost done.", body = "Prepare your scoring lame."),
        "Cold Ferment" to NotifCopy(title = "Cold ferment ending soon.", body = "Your scoring blade should be ready. Oven should be fully hot."),
        "Recovery" to NotifCopy(title = "Almost ready to stretch.", body = "Flour your work surface lightly. Set out your toppings."),
        "Pre-ferment Build" to NotifCopy(title = "Pre-ferment approaching peak.", body = "Check in 30 minutes. Look for dome, bubbles, and a slightly alcoholic aroma."),
        "Ferment Pre-ferment" to NotifCopy(title = "Pre-ferment approaching peak.", body = "Check in 30 minutes. Look for dome, bubbles, and a slightly alcoholic aroma."),
    )

    /** In-app long-form instructions shown during a step. 42 entries. */
    val stepDescriptions: Map<String, String> = mapOf(
        "Autolyse" to "Combine flour and water only — no salt or yeast yet. Cover. Let gluten hydrate undisturbed.",
        "Scale & Mix Preferment" to "Combine the preferment ingredients listed above. Mix until just combined — shaggy for biga, smooth batter for poolish. Cover as directed and ferment at room temperature.",
        "Scale Final Dough" to "Weigh each final dough ingredient separately. Zero the scale between additions. The pre-ferment goes in last — scoop it directly from the container. Weigh to the gram.",
        "Incorporate Butter" to "Add butter one tablespoon at a time with the mixer running on medium-low. Wait for each addition to fully incorporate before adding the next — rushing creates lumps and breaks the emulsion.\n\nContinue until all butter is incorporated — approximately 10–15 minutes. Stop mixer when dough is smooth, glossy, and pulls cleanly from the bowl. The dough will be silky and slightly slack — this is correct.",
        "Divide & Shape (Bagel)" to "Divide bulk dough by weight into equal portions. Pre-shape each into a smooth, tight ball — surface tension is key. Poke a thumb through the center, stretch the opening to ~2\" diameter, and place on a parchment-lined pan. Holes close slightly during cold proof — that is correct.",
        "Cold Proof (Bagel)" to "Refrigerate bagels uncovered for 12–24 hours. Cold develops chew, malty depth, and gives the exterior a slight skin that helps the boiling bath set cleanly. Uncovered is intentional — slight surface drying improves the bath.",
        "Boil & Top" to "Bring water + barley malt syrup to a full rolling boil. Lower bagels gently, boil 30–45 seconds per side. Transfer to a wire rack and add toppings immediately — the surface dries in seconds.",
        "Boil" to "Bring the boiling solution to a full rolling boil. Lower bagels gently — do not crowd the pot. Boil 30–45 seconds per side, then transfer to a wire rack.\n\nAdd toppings immediately — sesame, poppy, everything, or plain. The surface dries within seconds of leaving the water. Load the oven now.",
        "Rest & Alkaline Bath" to "Rest shaped pretzels uncovered 15–20 min. Bring your alkaline bath solution to a full rolling boil. Dip each pretzel for 20–25 seconds per side. Transfer to parchment immediately. Score the thick arch once with a sharp blade, apply coarse pretzel salt while still wet. Load the oven immediately.",
        "Alkaline Bath" to "Rest shaped pretzels uncovered 15–20 min — gluten relaxes, surface dries slightly for better bath adhesion.\n\nPrepare and heat your alkaline bath solution. Dip each pretzel 20–25 seconds. Transfer to parchment immediately. Score the thick arch once with a sharp blade. Apply coarse pretzel salt while still wet. Load the oven immediately.",
        "Place in Rings" to "Divide and round each piece into a smooth ball. Place in a greased ring mold on a semolina-dusted surface. Do not flatten — the ring controls the height and shape. Dust the top with semolina for the classic crust.",
        "Ring Proof" to "Muffins are proofing in their rings. Ready when the dough domes slightly above the ring rim and feels puffy — not jiggly. Under-proof = no nooks. Over-proof = flat on the griddle. Stay close.",
        "Griddle Cook" to "Transfer rings and all to the ungreased griddle. Cook 6–8 min per side undisturbed — the crust forms by conduction, not convection. Flip carefully with tongs. Internal temp 200–205°F. If browning too fast, lower heat and extend time. Finish in a 350°F oven if needed.",
        "Rope & Shape" to "Divide bulk dough by weight. Pre-shape each into a tight ball and rest uncovered 10 min — the surface dries slightly, helping the bath adhere evenly. Roll each ball into a 20–24\" rope: start from the center and work outward with steady pressure. Form the U-shape, cross the tails twice, fold down onto the arch, press ends to seal.",
        "Mix" to "Add salt and yeast to the autolysed dough. Mix to full incorporation. Dough should be smooth and slightly tacky.",
        "Bulk Fermentation" to "Cover the bowl. Dough doubles in volume — keep it warm between fold sets.\n\nPerform your coil folds at equal intervals throughout the bulk fermentation period. Divide the total bulk time into thirds — one set at each interval keeps your dough building strength evenly. BreadIQ will send you a reminder when it's time for each fold.",
        "Pre-shape" to "Flour the bench lightly. Fold the dough onto itself to build surface tension. Round it loosely, seam up, and cover.",
        "Bench Rest" to "Dough is resting, releasing tension. Do not touch. Cover with a damp cloth.",
        "Final Shape" to "Shape the dough into your chosen form. Work with confidence — hesitation creates uneven tension. Seal the seam tightly and place seam-down into your proofing vessel.",
        "Final Proof" to "Shaped dough is proofing. Stay close to temperature — even 5°F changes timing significantly.",
        "Final Proof (Cold)" to "Shaped dough cold-proofs in the refrigerator. Scoring from cold gives you cleaner cuts and more oven spring.",
        "Final Proof (post-retard)" to "The cold ferment completed most of the final proof. This is a short temper and final rise.",
        "Cold Ferment" to "Dough is fermenting cold and slow. Acids build. Gluten strengthens. Do not disturb.",
        "Temper" to "Cold dough needs to come up in temperature before baking. Cover. Do not rush — a warm center gives more oven spring.",
        "Score & Load" to "Score decisively — one clean stroke per cut. Hesitation causes dragging. Load immediately after scoring.",
        "Bake" to "Do not open the oven door in the first 15 minutes. Steam is building the crust.",
        "Portion & Ball" to "Divide evenly by weight. Round each piece — smooth side up, seam tucked tight underneath.",
        "Recovery" to "Gluten tightened during balling. Rest until the dough releases easily when pressed. Do not force it open.",
        "Temper & Stretch" to "Cold dough opens slowly. Start from the center and work outward. If it resists, give it 2 more minutes.",
        "Scale to Pan" to "Oil the pan generously. Dough goes in smooth side up. Press gently to fill corners. Cover.",
        "Proof & Dimple" to "Dough should jiggle like set Jell-O. Press fingers in firmly — holes fill slowly. Drizzle oil into every dimple.",
        "Degas & Bench Rest" to "Brioche: fold gently 2–3 times — do NOT punch down. The butter lamination tears easily. Pre-shape loosely and cover. Lean doughs: punch down, fold once, and cover.",
        "Divide & Pre-shape" to "Scale by weight. Round each piece smooth side up. For brioche: handle gently — the butter network is fragile. Bench rest covered 10–15 min before final shaping.",
        "Recipe Card" to "Your complete formula. Review all weights before you begin. This card stays with you throughout the bake.",
        "Scale Ingredients" to "Weigh each ingredient separately. Zero the scale between additions. Accuracy here determines everything downstream.",
        "Build Biga" to "Combine the biga flour, water, and yeast. Stir until just combined — a rough, shaggy texture is correct. No gluten development needed. Cover tightly with plastic wrap.",
        "Build Poolish" to "Combine the poolish flour, water, and yeast. Stir 2–3 minutes until smooth — it should resemble a thick batter. Cover loosely to allow off-gassing.",
        "Build Pre-ferment" to "Combine pre-ferment flour, water, and yeast as specified. Mix until just combined. Cover and ferment at room temperature.",
        "Pre-ferment Build" to "Your pre-ferment is fermenting. Ready when it has doubled in size, the surface is domed, and you can see bubbles throughout. Do not rush it.",
        "Ferment Pre-ferment" to "Your pre-ferment is fermenting. Ready when it has doubled in size, the surface is domed, and you can see bubbles throughout. Do not rush it.",
        "Mix Final Dough" to "Combine the pre-ferment with the remaining flour, water, and salt. Hold back a small amount of water to tune consistency. Mix until a cohesive dough forms. Add fat last if the recipe calls for it.",
        "Mix Dough" to "Combine all ingredients. Mix until a cohesive dough forms — shaggy is fine at this stage. Develop gluten through kneading or stretch-and-fold during bulk.",
    )

    /**
     * Generic in-app description for steps labeled "Coil Fold ..." that
     * don't have a specific [stepDescriptions] entry.
     */
    const val foldDescriptionCoil: String =
        "Wet your hands. Slide them under the dough from opposite sides, lift the dough up, let it fold onto itself, and set it back down. Rotate 90° and repeat. Four coil folds total. Takes about 30 seconds."

    /**
     * Generic in-app description for steps labeled "Stretch & Fold ..."
     * that don't have a specific [stepDescriptions] entry.
     */
    const val foldDescriptionSF: String =
        "Wet hands — never flour. Grab one side, stretch up firmly, fold over center. Rotate 90°. Four corners total. Takes 30 seconds."
}
