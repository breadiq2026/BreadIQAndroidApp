package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/TechniqueGuideCatalog.swift`, itself a
 * verbatim port of `bread-lab/src/components/technique-guide.tsx`'s six
 * catalogs (~600 lines of real technique prose, transcribed directly from
 * the source file, not from a secondhand summary, at every hop of this
 * chain — iOS from the web app, this port from iOS).
 *
 * Keys are the raw style/shape string values — this app's own
 * `BreadStyleCatalog`/`LoafShapeCatalog` `.value` strings line up with
 * these directly.
 *
 * **Every catalog's key count matches the source exactly**: `kneading`/
 * `proofingGeneral` cover the full 13-value style-key union (this app's
 * own `BreadStyleCatalog` only has 12 of those 13 — missing `whole_wheat`,
 * a real, pre-existing gap in the ported app's own source, not introduced
 * here or by the iOS port; the `whole_wheat` entry is kept anyway for
 * source fidelity, simply unreachable via this app's own style picker
 * today). `shapingByShape`/`baking` cover the full 7-value shape-key
 * union. `shapingByStyle`/`bakingByStyle` cover exactly the 10 styles the
 * source itself overrides (missing `country`/`artisan`/`whole_wheat` —
 * those three fall through to the shape-keyed catalogs via the eventual
 * lookup logic, a separate port from this data-only pass, same as
 * `BakeStepContent`'s lookup logic in step 1).
 */
object TechniqueGuideCatalog {

    // KNEADING (13 keys — full style-key set)

    val kneading: Map<String, TechniqueSection> = mapOf(
        "baguette" to TechniqueSection(
            method = "Slap & Fold (French Fold)",
            steps = listOf(
                "Combine flour, water, yeast, and salt. Mix until no dry flour remains.",
                "Rest 20–30 minutes (autolyse) to hydrate the flour before working the dough.",
                "Slap the dough onto the counter and fold it back over itself. Repeat in a steady rhythm for 8–10 minutes.",
                "Test with the windowpane: slowly stretch a small piece — it should stretch thin enough to see light through without tearing.",
            ),
            tip = "A cool dough (68–72°F) is critical for baguettes. Warm hands accelerate fermentation and shorten your window.",
        ),
        "country" to TechniqueSection(
            method = "Stretch & Fold in the Bowl",
            steps = listOf(
                "Mix until shaggy, then rest 30 minutes.",
                "During the first 2 hours of bulk, perform 4 sets of stretch-and-fold spaced 30 minutes apart.",
                "Each set: wet your hand, grab one side of the dough and stretch it up high, then fold it over to the opposite side. Rotate the bowl 90° and repeat — 4 folds completes one set.",
                "The dough will feel progressively tighter and smoother after each set.",
            ),
            tip = "Stop folding once the dough holds its shape and resists stretching — that's full gluten development.",
            lexiconId = "stretch-and-fold",
        ),
        "artisan" to TechniqueSection(
            method = "Lamination + Coil Folds",
            steps = listOf(
                "Mix flour, water, and levain/yeast until no dry flour remains. Rest 30–45 minutes.",
                "Lamination: wet your counter, gently stretch the dough into a large thin rectangle — go slowly and don't tear it. Fold into thirds lengthwise, then fold in half. Return to the bowl.",
                "Coil folds: every 30–45 minutes, slide both hands under the center of the dough, lift it, and let the sides fold beneath it. Rotate 90° and repeat. Do 3–4 sets total.",
                "The dough is ready when it holds a round shape, feels airy, and jiggles when the bowl is shaken.",
            ),
            tip = "Artisan dough builds strength through time and folds — not friction. There is no kneading here. Trust the process.",
            lexiconId = "coil-fold",
        ),
        "ciabatta" to TechniqueSection(
            method = "No Kneading — Coil Folds Only",
            steps = listOf(
                "Mix flour, water, yeast, and salt until just combined. Do NOT knead — this dough is nearly pourable at 78–85% hydration.",
                "Cover and rest 30 minutes.",
                "Perform 5–6 gentle coil folds at 20–30 minute intervals during bulk fermentation.",
                "Handle minimally — the large, irregular holes come from gas retention, which aggressive handling destroys.",
            ),
            tip = "Always use wet hands — never flour — when handling ciabatta. Flour ruins the open crumb.",
            lexiconId = "coil-fold",
        ),
        "focaccia" to TechniqueSection(
            method = "Gentle Mix + Coil Folds in Oil",
            steps = listOf(
                "Combine flour, water, yeast, salt, and 2 tablespoons of the olive oil. Mix by hand until no dry flour remains — no kneading, just incorporation. The dough will look rough and shaggy at first. That's fine.",
                "Cover and rest 15–20 minutes. The gluten will begin relaxing and hydrating on its own without any effort from you.",
                "Coil fold 1: wet your hands thoroughly (no flour ever). Slide both hands under the center of the dough, lift it up, and let both sides fold down underneath. Rotate the bowl 90° and repeat — that's one complete coil fold. Perform 2–3 sets, spaced 15–20 minutes apart.",
                "Pour the remaining olive oil into a large baking pan or sheet tray. Gently transfer the dough into the oiled pan and stretch it toward the edges. If it springs back, stop, cover it for 20 minutes, then try again.",
            ),
            tip = "Never add flour to focaccia dough — not to the counter, not to your hands, never. Wet hands only. The oil-rich environment is what creates focaccia's signature pillowy interior and crispy base.",
            lexiconId = "coil-fold",
        ),
        "whole_wheat" to TechniqueSection(
            method = "Autolyse + Traditional Kneading",
            steps = listOf(
                "Mix flour and water only (no yeast or salt yet). Rest 30–45 minutes — this is the autolyse. It gives the bran time to hydrate and gluten to begin developing before salt tightens everything.",
                "Add yeast and salt. Knead 8–10 minutes by hand or 5–6 minutes in a stand mixer with a dough hook.",
                "Perform 2–3 stretch-and-fold sets at 30-minute intervals during the first hour of bulk.",
            ),
            tip = "Whole wheat dough feels denser than white flour dough — that's normal. The bran severs gluten strands, so don't expect the same extensibility.",
            lexiconId = "kneading",
        ),
        "pizza_ny" to TechniqueSection(
            method = "Full Mixer Development + Oil Incorporation",
            steps = listOf(
                "Combine flour, water, yeast, salt, and sugar in the mixer bowl. Mix at low speed 2–3 minutes until no dry flour remains.",
                "Increase to medium speed and mix 6–8 minutes until smooth and cohesive. The dough will feel firm and slightly tacky.",
                "Stream in the oil in the last 2 minutes of mixing — adding oil too early coats the gluten strands before they fully develop. The dough will become smoother and more extensible.",
                "Windowpane test: stretch a small piece between your fingers — it should stretch thin enough to let light through without tearing. If it tears, mix 2 more minutes.",
                "Ball the dough, lightly oil the surface, cover, and proceed to bulk fermentation before portioning.",
            ),
            tip = "A well-built NY/Roman dough stretches to 12–13\" without tearing. If it tears or snaps back during stretching, the dough needs more rest — never more force.",
            lexiconId = "kneading",
        ),
        "pizza_neo" to TechniqueSection(
            method = "Patient Full Development — Mixer or Slap & Fold",
            steps = listOf(
                "Combine 00 flour and cool water. Mix briefly until a shaggy mass forms, then rest 30 minutes uncovered (autolyse). No oil — no sugar.",
                "Add yeast and salt. Mix at low-to-medium speed 15–20 minutes, or hand-knead using the slap-and-fold technique.",
                "Slap-and-fold: grab the dough with both hands, slap it against the counter, and fold it back over itself. Repeat in a steady rhythm for 15–20 minutes — the dough transforms from shaggy to silky smooth.",
                "The finished dough should be very smooth, supple, and highly extensible — it should stretch 12\" with almost no resistance when warm.",
                "Ball and proceed to a 24–48 hour cold ferment. Neapolitan fermentation cannot be rushed.",
            ),
            tip = "Under-developed dough tears when stretched; properly developed dough drapes over your hands like fabric. The cold ferment does more for flavor and extensibility than any single mixing technique.",
        ),
        "soft_roll" to TechniqueSection(
            method = "Standard Mixer Development + Fat Incorporation",
            steps = listOf(
                "Add flour, sugar, salt, and yeast to the mixer bowl. Add water and mix at low speed 2 minutes until a rough dough forms.",
                "Increase to medium speed. Mix 4–5 minutes until smooth and cohesive.",
                "With the mixer running, stream in the fat (oil, butter, or lard) over 2 minutes. The dough will look loose at first — that's normal. Continue until it comes together and pulls cleanly from the bowl sides.",
                "Total mix time: 6–8 minutes. The finished dough should be smooth, soft, and slightly tacky — not sticky, not dry. It should pass a windowpane test despite the fat content.",
                "Round into a ball, cover, and bulk ferment until 50–75% larger.",
            ),
            tip = "Enriched doughs are more sensitive to over-mixing than lean doughs — fat lubricates gluten strands and can break them down if worked too long. Stop as soon as the dough is smooth and passes the windowpane.",
            lexiconId = "kneading",
        ),
        "bagel" to TechniqueSection(
            method = "Stand Mixer — Full Gluten Development (Stiff Dough)",
            steps = listOf(
                "Dissolve the barley malt syrup in the warm water. Add flour, yeast, and salt to the mixer bowl, then stream in the malt water.",
                "Mix at low speed 2 minutes until no dry flour remains. Bagel dough is noticeably stiffer than a lean bread dough at 55–57% hydration — that's correct.",
                "Increase to medium speed and mix 8–10 minutes until the dough is smooth, firm, and elastic. It should pull cleanly from the bowl sides and feel very tight.",
                "Windowpane test: bagel dough won't stretch as thin as high-hydration artisan dough — aim for a translucent membrane that holds without tearing immediately.",
                "Ball tightly, cover, and bulk ferment 1–1.5 hours at room temperature, or refrigerate overnight for a cold bulk.",
            ),
            tip = "Bagel dough should feel significantly stiffer than bread dough — it needs to be firm enough to hold its ring shape through the boil without collapsing. Do not add extra water to make it feel more comfortable.",
        ),
        "english_muffin" to TechniqueSection(
            method = "Stand Mixer — Enriched Dough Development",
            steps = listOf(
                "Warm the milk to 90–95°F (warm to the touch, not hot). Combine flour, sugar, salt, and instant yeast in the mixer bowl. Add warm milk and softened butter.",
                "Mix at low speed 2 minutes until a shaggy dough forms. Increase to medium speed.",
                "Mix 6–8 minutes until the dough is smooth, soft, and slightly tacky — it should pull from the bowl sides cleanly. The butter and milk make this dough more tender and extensible than a lean bread dough.",
                "Soft windowpane: stretch a small piece gently — it should hold without tearing, but don't expect the crackle-thin membrane of a high-hydration dough.",
            ),
            tip = "English muffin dough is wetter than you might expect — resist the urge to add flour. The high hydration from milk is what creates the signature nooks and crannies when the muffin hits the hot griddle.",
        ),
        "pretzel" to TechniqueSection(
            method = "Stand Mixer — Full Gluten Development (Stiff Dough)",
            steps = listOf(
                "Combine flour, water, yeast, salt, and sugar in the mixer bowl. Mix at low speed 2 minutes until a rough dough forms.",
                "Increase to medium-high speed and mix 8–10 minutes. Pretzel dough is stiffer than standard bread dough (55% hydration) and develops a tight, elastic gluten network.",
                "The finished dough should be smooth, firm, and slightly tacky — it should pull cleanly from the bowl and spring back immediately when poked.",
                "Do not add fat — the fat-free formula is what allows the alkaline bath to penetrate the surface and create the distinctive mahogany crust.",
            ),
            tip = "A well-developed pretzel dough holds its rope shape without springing back aggressively. If the ropes keep shrinking during rolling, cover the pieces and rest 5 minutes to relax the gluten before continuing.",
        ),
        "brioche" to TechniqueSection(
            method = "Stand Mixer — Extended Butter Incorporation",
            steps = listOf(
                "Combine flour, sugar, salt, yeast, and eggs in the mixer bowl. Mix at medium speed 5 minutes until smooth and cohesive — before any butter is added.",
                "With the mixer running, add cold butter in small pieces (1 tablespoon at a time) over 10–15 minutes. Do not rush — adding butter too fast causes the dough to break apart into greasy pieces.",
                "After all butter is incorporated, mix at medium-high speed 5–8 more minutes until the dough is silky, shiny, and pulls cleanly from the bowl.",
                "Windowpane test: stretch a small piece — it should stretch incredibly thin without tearing. This confirms full gluten development despite the fat content.",
                "Place in a lightly oiled bowl, cover, and refrigerate overnight. Cold retarding is essential — a warm brioche dough is nearly unworkable.",
            ),
            tip = "Cold butter added one tablespoon at a time is the non-negotiable rule. Warm or rushed butter turns brioche greasy and breaks the emulsion. If the dough looks greasy at any point, refrigerate it 15 minutes before continuing.",
            lexiconId = "kneading",
        ),
    )

    // SHAPING_BY_SHAPE (7 keys — full shape-key set)

    val shapingByShape: Map<String, TechniqueSection> = mapOf(
        "batard" to TechniqueSection(
            method = "Oval Torpedo (Batard) into Oblong Banneton",
            steps = listOf(
                "After bulk fermentation, gently tip the dough onto an un-floured surface. Pre-shape into a loose round ball by folding the edges to the center, then flip seam-side down and drag toward you to build light surface tension. Bench rest 20–25 minutes uncovered.",
                "Flip seam-side up. Fold the top third down, then fold the bottom third up over it — like folding a letter.",
                "Roll the dough toward you with both hands, using the counter edge to build surface tension. The seam runs along the bottom.",
                "Gently elongate and taper the ends to achieve a 12–13\" oval torpedo. Place seam-side up in a well-floured oblong banneton.",
                "Cover and refrigerate, or proof at room temperature according to your schedule.",
            ),
            tip = "Surface tension is what creates oven spring. If the surface looks slack or tears during shaping, your dough may be over-fermented.",
            lexiconId = "tensioning",
        ),
        "boule" to TechniqueSection(
            method = "Round Boule with Surface Tension into Round Banneton",
            steps = listOf(
                "After bulk fermentation, gently tip the dough onto an un-floured surface. Fold the edges to the center to form a rough round, flip seam-side down, and bench rest 20 minutes uncovered.",
                "Flip seam-side up. Fold all four sides to the center (north, south, east, west), then flip seam-side down.",
                "Place both hands around the far side of the dough. Drag it slowly toward you on the counter without lifting it. The friction builds surface tension.",
                "Rotate 90° and repeat the drag. Do this 4–6 times until the surface is taut and smooth. Place seam-side up in a well-floured 8\" round banneton.",
                "Cover and refrigerate, or proof at room temperature according to your schedule.",
            ),
            tip = "Don't flour your hands when shaping — the friction between bare skin and the counter is what tightens the surface. Flour eliminates that grip.",
            lexiconId = "tensioning",
        ),
        "pullman" to TechniqueSection(
            method = "Log Roll into the Pullman Pan",
            steps = listOf(
                "After bulk, flatten the dough into a rectangle roughly the width of your Pullman pan.",
                "Roll tightly from one short end — like a jelly roll — pressing gently but firmly as you go.",
                "Pinch the seam firmly shut. Place seam-side down into the lightly oiled Pullman pan.",
                "Press the dough gently to the corners. It should fill the pan about halfway. Proof until it crowns just to the rim of the pan before sliding on the lid.",
            ),
            tip = "For the classic square cross-section, the slide-on lid is non-negotiable. If the dough over-proofs before baking, the lid won't close — watch the timing closely.",
        ),
        "pullman_soft" to TechniqueSection(
            method = "Log Roll into the Pullman Pan — Enriched Dough",
            steps = listOf(
                "After bulk fermentation and a 10–15 minute bench rest, flatten the dough into a rectangle roughly the width of your 9\" Pullman pan.",
                "Roll tightly from one short end — like a jelly roll — pressing gently but firmly as you go. The enriched dough (fat + sugar) is softer than lean bread dough; handle with confidence.",
                "Pinch the seam firmly shut. Place seam-side down into the lightly oiled Pullman pan. Press the dough gently to the corners — it should fill the pan about halfway.",
                "Proof until the dough crowns just to the rim (or slightly above for a round top without the lid). Slide on the oiled lid just before baking for a square sandwich loaf.",
            ),
            tip = "Enriched Pullman dough proofs faster than lean dough — check earlier than you would for an artisan Pullman. The dough should feel soft, puffy, and gently press back when touched.",
        ),
        "boule_8_oval" to TechniqueSection(
            method = "Oval Batard Shape into 8\" Oblong Banneton",
            steps = listOf(
                "After bulk fermentation, gently tip the dough onto an unfloured surface. At ~475g this is a compact loaf — handle with confidence.",
                "Pre-shape into a loose round by folding the edges to the center. Flip seam-side down and bench rest 15–20 minutes uncovered.",
                "Final shape: flip seam-side up. Fold the top third down, then fold the bottom third up and over (letter fold). Seal the seam firmly with the heel of your hand.",
                "Roll the dough toward you with both hands, using gentle pressure from the counter to build surface tension. Taper the ends slightly to form a short, even oval.",
                "Dust an 8\" oblong banneton generously with rice flour or a 50/50 rice-AP blend. Place the dough seam-side up and nestle it to fill the form evenly.",
            ),
            tip = "The smaller mass of this loaf means it needs a tighter shaping pass than a larger boule — any slack in the surface will spread flat in the oven rather than springing up. Score with a single confident slash on the long axis.",
            lexiconId = "tensioning",
        ),
        "boule_10_round" to TechniqueSection(
            method = "Round Boule Shape into 10\" Round Banneton",
            steps = listOf(
                "After bulk fermentation, gently tip the dough onto an unfloured surface. At ~1,000g this is a heavy batch — use steady, confident movements throughout.",
                "Pre-shape into a rough round by folding the edges to the center. Flip seam-side down and bench rest 20–25 minutes uncovered — the extra mass needs more relaxation time.",
                "Final shape: flip seam-side up. Fold all four sides to the center (north, south, east, west), then flip seam-side down.",
                "Place both hands around the far side of the dough. Drag slowly toward you without lifting — friction against the counter builds surface tension. Rotate 90° and repeat 6–8 times until the surface is very taut and smooth.",
                "Dust a 10\" round banneton generously with rice flour. Place the dough seam-side up — the dough should fill the banneton approximately two-thirds full.",
            ),
            tip = "A 1kg loaf takes significantly more oven time to open than a standard 8\" boule. Keep the Dutch oven lid on for the full 25 minutes — resist the urge to check early. Cold fermenting overnight gives the crumb extra structure to handle the size.",
            lexiconId = "tensioning",
        ),
        "boule_10_oval" to TechniqueSection(
            method = "Oval Batard Shape into 10\" Oblong Banneton",
            steps = listOf(
                "After bulk fermentation, gently tip the dough onto an unfloured surface. At ~775g this is a substantial loaf — pre-shape with light but confident pressure.",
                "Pre-shape into a loose round, flip seam-side down, and bench rest 20–25 minutes uncovered.",
                "Final shape: flip seam-side up. Fold the top third down, then fold the bottom third up and over (letter fold). Seal the seam firmly with the heel of your hand.",
                "Roll toward you with both hands, using the counter edge to build surface tension. Gently elongate and taper both ends to form a 10–11\" oval torpedo shape.",
                "Dust a 10\" oblong banneton generously with rice flour. Place the dough seam-side up — it should fill the banneton about two-thirds full.",
            ),
            tip = "Surface tension is what creates oven spring. If the surface tears or looks slack during shaping, the dough may be over-fermented. This size benefits especially from a cold retard — firm dough from the fridge holds its shape and scores far more cleanly.",
            lexiconId = "tensioning",
        ),
    )

    // SHAPING_BY_STYLE (10 keys)

    val shapingByStyle: Map<String, TechniqueSection> = mapOf(
        "baguette" to TechniqueSection(
            method = "Hand-Rolling into Bâtons (No Banneton)",
            steps = listOf(
                "After bulk, divide the dough into equal portions (see divide guide above for your count). Gently pre-shape each into a loose log — not a tight roll. Bench rest 20–30 minutes seam-side up on a lightly floured surface.",
                "Final shaping: flatten the piece slightly into a rectangle. Fold the top third down to the center, then fold the bottom third up and seal the seam with the heel of your hand.",
                "Using both hands, roll the log outward from the center — simultaneously lengthening and tapering the ends — until you reach 14–16\" (or the length of your baking steel/stone). Apply even, gentle pressure.",
                "Place seam-side up on a floured linen couche (or seam-side down on floured parchment), with a fold of couche between each baguette to support the sides.",
            ),
            tip = "The final roll should feel light and even — not forced. Stop short of your target length; the dough will relax and extend slightly during proofing. Over-tight rolling tears the dough and destroys the structure.",
        ),
        "ciabatta" to TechniqueSection(
            method = "Slab Portioning — No Traditional Shaping",
            steps = listOf(
                "After bulk, generously flour your work surface with a 50/50 mix of flour and semolina if available. Gently tip the dough out — do NOT punch down or degas in any way.",
                "Using a bench scraper, divide into rectangles (typically 3–4 pieces). Slide the scraper under each piece — do not lift with your hands.",
                "Carefully flip each rectangle seam-side up onto heavily floured parchment or a floured couche. The irregular shape is intentional — do not reshape.",
                "Proof directly on the parchment. Transfer to the oven using the parchment as a sling, or use a peel and flip directly onto a preheated baking stone.",
            ),
            tip = "Ciabatta gets zero shaping beyond cutting. Every touch degasses the alveoli you spent hours building. The wild, open crumb structure happens here — respect it.",
        ),
        "focaccia" to TechniqueSection(
            method = "Pan Stretch — No Shaping or Banneton Needed",
            steps = listOf(
                "Focaccia shaping happens in the pan, not on the counter. After bulk, pour the olive oil into your pan and coat the entire surface generously.",
                "Gently transfer the dough into the oiled pan — don't degas it. Using well-oiled hands, stretch the dough toward the corners and edges.",
                "If the dough springs back and resists, stop immediately. Cover the pan and let it relax 20–30 minutes, then try again. Forcing it tears the gluten and leaves you with a dense crumb.",
                "The dough should fill the pan fully and look relaxed and even. Cover and proceed to proofing.",
            ),
            tip = "The dimples come LAST — just before baking, not before proofing. Dimpling before the proof expels gas prematurely. Press all the way to the pan bottom right before you load it into the oven.",
        ),
        "pizza_ny" to TechniqueSection(
            method = "Ball Formation + Hand Stretching — No Rolling Pin",
            steps = listOf(
                "After bulk fermentation, divide the dough into equal portions (see divide guide above for your count and weight). Use a bench scraper — no tearing. Gently pre-shape each into a tight, smooth ball using a light circular drag technique on an unfloured surface.",
                "Place each ball seam-side down in a lightly oiled deli container or on a floured tray. Cover tightly and cold ferment at least 12–24 hours at 38°F.",
                "Remove from the refrigerator 60–90 minutes before baking. Allow to temper until soft, puffy, and very extensible. Do not skip temper — cold balls tear when stretched.",
                "To stretch: press the ball flat with your palm. Pick it up and drape it over your knuckles. Use your knuckles to gently push outward from the center, rotating as you go. Work toward the edges last, leaving a thicker rim.",
                "Do NOT use a rolling pin — it deflates the rim gas built during fermentation. Sauce, top, and bake immediately on a preheated stone or steel.",
            ),
            tip = "If the dough keeps springing back and resisting your stretch, set it down, cover it, and rest 10 minutes — then try again. The gluten needs to relax, not be forced.",
            lexiconId = "tensioning",
        ),
        "pizza_neo" to TechniqueSection(
            method = "Gentle Ball Formation + Fingertip Stretch",
            steps = listOf(
                "After bulk, gently divide into equal portions (see divide guide above). Handle with the absolute minimum force — Neapolitan dough is fragile and highly extensible.",
                "Form each ball: fold edges to center, flip seam-side down, and use a cupped hand to drag lightly in a small circular motion until you feel slight surface tension. Do NOT squeeze or press.",
                "Place seam-side down in individual proofing containers or on a semolina-dusted tray. Cover and cold ferment 24–48 hours.",
                "Remove 2–3 hours before baking. Allow to fully temper — the balls should look pillow-soft and feel very slack and extensible when lifted.",
                "Stretch on a lightly semolina-dusted surface: use fingertips to press from the center outward, leaving a 1\" cornicione untouched. Then gently slap and rotate — the center should be paper-thin, the rim thick and airy.",
            ),
            tip = "The cornicione (rim) is the soul of Neapolitan pizza — never press the outer inch while stretching. The leopard-spotting char from a 900°F oven inflates the trapped gas in the rim in seconds.",
            lexiconId = "tensioning",
        ),
        "soft_roll" to TechniqueSection(
            method = "Divide → Pre-shape → Bench Rest → Final Shape",
            steps = listOf(
                "After bulk fermentation, turn the dough onto a lightly floured surface. Press down gently to degas (a full punch-down for rolls — unlike artisan loaves). Divide into the target number of pieces (see divide guide above).",
                "Pre-shape each portion into a loose round ball by folding edges to center and lightly rounding. Cover and bench rest 10–15 minutes — this allows the gluten to relax for clean final shaping.",
                "Dinner rolls / slider buns: cup each portion in your palm and use a circular rolling motion against the counter to form a smooth, tight ball. Place 1–2\" apart on a parchment-lined sheet pan.",
                "Burger buns: shape as for rolls, then gently flatten each ball into a 3.5–4\" disc with your palm before panning. They will puff back to a bun height during proof.",
                "Hoagie / sub rolls: flatten the pre-shaped ball into a rectangle, fold the top third down and the bottom third up (letter fold), seal the seam firmly, then roll gently to even length. Place seam-side down. Pullman loaf: flatten into a rectangle the width of the pan, roll tightly from one end, pinch seam closed, and place seam-side down in the oiled pan.",
            ),
            tip = "Soft roll dough is forgiving — it's enriched, extensible, and shapeable. Do not over-work after the bench rest. A quick, confident shape beats slow, tentative handling every time.",
        ),
        "bagel" to TechniqueSection(
            method = "Rope & Ring — Two Methods",
            steps = listOf(
                "After bulk, divide the dough into equal portions (typically 115g for standard bagels, 55g for mini). Pre-shape each into a tight ball. Cover and rest 5 minutes.",
                "Method 1 — rope: roll each ball into an even rope 8–10\" long. Wrap around your hand, overlap the ends by 1–1.5\", and press and roll the seam firmly on the counter to seal. The hole should be larger than your target — it closes significantly during the boil and bake.",
                "Method 2 — thumb-through: poke your thumb through the center of the ball and stretch the ring evenly. More uniform than the rope method for beginners.",
                "Arrange shaped bagels on parchment-lined sheet pans with 2\" between each. Refrigerate uncovered overnight (12–18 hours) for maximum flavor and structure before boiling.",
            ),
            tip = "The ring opening needs to be oversized — a bagel with a tight hole will seal completely during boiling. Aim for a 2–2.5\" opening diameter before retarding.",
        ),
        "english_muffin" to TechniqueSection(
            method = "Portion & Ring Placement",
            steps = listOf(
                "After bulk fermentation, gently tip the dough onto a lightly cornmeal-dusted surface. No punching — handle gently to preserve the gas bubbles that create nooks and crannies.",
                "Divide into equal portions (95g for standard 3.5\" muffins, 130g for large 4\" muffins). Round each into a smooth ball.",
                "Place inside well-greased English muffin rings (or directly on a greased griddle for a rustic shape). The dough should fill the ring about half-full — it expands during proofing and cooking.",
                "Dust the tops with cornmeal. Cover loosely and proof 45–60 minutes until the dough puffs and fills the rings about ¾ full.",
            ),
            tip = "Cornmeal on both top and bottom surfaces is the signature English muffin texture — don't skip it. Dust before proofing so it adheres fully to the dough surface.",
        ),
        "pretzel" to TechniqueSection(
            method = "Rope & Twist — Traditional Knot",
            steps = listOf(
                "After bulk, divide the dough into equal portions. Pre-shape into balls and rest uncovered 5–10 minutes — slight surface drying helps the alkaline bath adhere.",
                "For pretzel twists: roll each ball into an even rope 20–24\" long, starting from the center and working outward. Even diameter throughout means even browning.",
                "Form a U-shape with the rope. Cross the tails twice at the top, fold the crossed ends down onto the arch, and press firmly to seal. The classic pretzel knot.",
                "For pretzel buns or sliders: shape into a smooth, slightly flattened round bun. Score a shallow 'X' across the top before the alkaline bath — this opens the surface for the characteristic texture.",
                "For pretzel sticks: roll into even 8–10\" cylinders. Leave uncovered at room temperature while you prepare the alkaline bath.",
            ),
            tip = "Leave shaped pretzels uncovered at room temperature while you prepare the alkaline bath. A slight surface skin helps the lye or baking soda solution adhere evenly and creates the characteristic pretzel texture.",
        ),
        "brioche" to TechniqueSection(
            method = "Pan Loaf, Rolls, or Buns — Shape Cold",
            steps = listOf(
                "Remove the cold bulk-fermented dough from the refrigerator. Work quickly — brioche softens fast at room temperature and becomes difficult to shape when warm.",
                "Divide into portions (see divide guide above). Pre-shape each into a tight ball — the dough will feel firm and very manageable cold.",
                "For a loaf pan: flatten the ball into a rectangle, roll firmly from the top edge down, pinch the seam tightly, and place seam-side down in a generously buttered 9×5\" pan.",
                "For dinner rolls: shape into smooth, tight balls. Place in a buttered pan with sides touching for pull-apart rolls, or spaced on a sheet pan for individual rolls.",
                "For burger buns: flatten each ball into a 3.5–4\" disc and place on a parchment-lined baking sheet.",
                "Brush all surfaces with egg wash (1 egg + 1 Tbsp milk). Proof at room temperature 2–3 hours until puffed and jiggly.",
            ),
            tip = "Egg wash brioche twice for the deepest glaze: once right before the final proof and once just before loading the oven. Two coats give you the lacquered mahogany crust that defines professional brioche.",
        ),
    )

    // PROOFING_GENERAL (13 keys — full style-key set)

    val proofingGeneral: Map<String, TechniqueSection> = mapOf(
        "baguette" to TechniqueSection(
            method = "Short Proof — Slightly Under is Correct",
            steps = listOf(
                "Proof in a couche or on floured parchment, seam-side up, for 45–75 minutes at room temperature (70–75°F).",
                "Baguettes are intentionally proofed slightly under relative to other breads — this is what creates the dramatic ear and oven spring.",
                "Poke test: gently press with a floured finger. The dent should spring back about halfway — not snap back immediately (under) or stay dented (over).",
                "Scoring check: score marks that spring shut right after cutting are a sign of over-proofing. They should open cleanly and hold.",
            ),
            tip = "When in doubt between under and over for baguettes, choose under. You lose the ear to over-proofing, and that's the whole point.",
        ),
        "country" to TechniqueSection(
            method = "Room Temp or Cold Ferment",
            steps = listOf(
                "Option A — Same day: proof 1–2 hours at room temperature (70–75°F) until 50–60% larger.",
                "Option B — Overnight cold ferment: transfer shaped dough (in banneton) to the refrigerator for 8–14 hours. Bake straight from the fridge — no counter time needed.",
                "Poke test: press gently with a floured finger. The indent should slowly fill back — not snap back (under) or stay dented (over).",
                "Jiggle test: gently shake the banneton. The dough should wobble freely like loose Jell-O. If it feels firm and immovable, give it more time.",
            ),
            tip = "Cold ferment gives you scheduling flexibility and builds significantly deeper flavor. The difference in a long cold-proofed country loaf is dramatic.",
        ),
        "artisan" to TechniqueSection(
            method = "Jiggle Test + Poke Test",
            steps = listOf(
                "Proof 1–3 hours at 72°F after shaping for a same-day bake, or cold ferment overnight (8–16 hours at 38–40°F) for deeper flavor.",
                "Jiggle test: gently shake the banneton — the dough should wobble freely like loose Jell-O. Firm = under-proofed.",
                "Poke test: slowly springs back most of the way, leaving a faint shallow indent. Snaps back completely = under. Stays fully dented = over.",
                "With a levain/sourdough culture, allow extra time — wild yeast acts more slowly than commercial yeast at equivalent temperatures. Watch the dough, not the clock.",
            ),
            tip = "Artisan dough is more forgiving slightly under-proofed than over. When in doubt, bake it — you can't un-over-proof.",
        ),
        "ciabatta" to TechniqueSection(
            method = "Flat Proof on Floured Couche",
            steps = listOf(
                "After portioning, rest the pieces on a heavily floured couche or parchment for 60–90 minutes at room temperature.",
                "The pieces will spread flat and relaxed — this is correct. Ciabatta proof looks alarmingly flat. Do not panic.",
                "Poke test: use a floured finger, the dent should fill back slowly over 5–10 seconds. It won't puff back like a stiffer dough.",
                "Transfer very carefully with a bench scraper and a flipping board to avoid deflating the open crumb you've worked to build.",
            ),
            tip = "All of ciabatta's dramatic rise happens in the oven, not in the proof. The flat appearance before baking is correct.",
        ),
        "focaccia" to TechniqueSection(
            method = "Pan Proof Until Pillowy — Then Dimple",
            steps = listOf(
                "After stretching into the oiled pan, cover and proof 45–75 minutes at room temperature until the dough looks domed, pillowy, and relaxed across the entire pan.",
                "Poke test: press gently — the dent should fill back slowly. Focaccia is proofed slightly more than lean dough — a fuller proof gives a lighter, airier crumb.",
                "Just before baking, press your oiled fingers firmly all the way to the pan bottom across the entire surface to create the signature dimples.",
                "Drizzle generously with olive oil, add toppings, and bake immediately — don't let it sit after dimpling.",
            ),
            tip = "Dimple right before loading the oven, not before proofing. The dimples channel olive oil to the base, creating the crispy bottom that defines great focaccia.",
        ),
        "whole_wheat" to TechniqueSection(
            method = "Shorter Proof — Watch Carefully",
            steps = listOf(
                "Proof 1–1.5 hours at 72°F after shaping, or 8–10 hours cold ferment.",
                "Whole wheat proofs noticeably faster than white flour dough due to enzymatic activity in the bran.",
                "Target 50% volume increase. Set a timer — over-proofing whole wheat is a very common mistake.",
                "Poke test: indent springs back slowly but fully. Any dent that lingers means baking now.",
            ),
            tip = "Whole wheat dough is less forgiving of over-proofing than white flour dough. When in doubt, err on the side of slightly under.",
        ),
        "pizza_ny" to TechniqueSection(
            method = "Cold Ferment Required — Then Room-Temp Temper",
            steps = listOf(
                "Cold fermentation is not optional for NY/Roman style — the retard builds the extensibility and flavor that define the style. Minimum 12 hours; 24–48 hours is ideal.",
                "Store portioned dough balls tightly covered at 38°F. The dough should look smooth and firm when it goes in.",
                "Remove from the refrigerator 60–90 minutes before stretching. Temper uncovered at room temperature.",
                "When ready: each ball should feel puffy, pillowy, and extensible when gently pressed. Cold, stiff balls will tear when stretched — always temper fully.",
            ),
            tip = "Pizza dough is not proofed in a traditional sense — there is no poke test or banneton. The dough is ready when it's warm, extensible, and relaxed. This is entirely a tactile check.",
        ),
        "pizza_neo" to TechniqueSection(
            method = "Long Cold Ferment — Cannot Be Rushed",
            steps = listOf(
                "After balling, place each portion in an individual covered container or a sectioned tray. Cold ferment at 38°F for 24–48 hours.",
                "The extended cold ferment is what produces the flavor complexity and the gluten extensibility that allows Neapolitan dough to stretch paper-thin without tearing.",
                "Remove from the refrigerator 2–3 hours before baking. Fully temper at room temperature.",
                "The balls should feel pillow-soft and stretch easily when lifted with one finger. If they still feel dense and cold, wait longer.",
            ),
            tip = "Neapolitan pizza fermentation is measured in days, not hours. If you can only do 12 hours, the results will still be good — but 24–48 hours is where the flavor and texture truly open up.",
        ),
        "soft_roll" to TechniqueSection(
            method = "Short Final Proof — Watch Carefully",
            steps = listOf(
                "Soft roll dough proofs quickly due to higher yeast content and the sugar that feeds fermentation. Most formats reach final proof in 45–75 minutes at 72°F.",
                "Rolls should look visibly puffed — about 50–60% larger than when shaped. They will look slightly underdone; they spring up further in the oven.",
                "Poke test: gently press a roll with a floured finger. It should spring back slowly and nearly completely. Stays dented = over-proofed. Snaps back = under-proofed.",
                "Egg wash (optional but recommended): brush lightly with a beaten egg + splash of water right before loading the oven. Do not apply too early — it skins the surface and inhibits spring.",
            ),
            tip = "Enriched doughs over-proof faster than lean doughs. Set a timer at the 35-minute mark and check every 10 minutes from there. A batch of over-proofed rolls is very difficult to recover.",
        ),
        "bagel" to TechniqueSection(
            method = "Cold Retard After Shaping — Required",
            steps = listOf(
                "After shaping, place bagels on parchment-lined sheet pans and refrigerate uncovered overnight (12–18 hours at 38°F). Cold retarding develops flavor and firms the structure for the boil.",
                "Remove from the refrigerator just before boiling — bagels go from fridge directly to the boiling pot. No room-temperature counter time needed.",
                "Readiness check: bagels should feel slightly puffy and jiggly but still very firm. The skin should be dry. Over-proofed bagels will spread in the pot.",
                "After boiling and topping, bake immediately — do not let boiled bagels sit.",
            ),
            tip = "Properly cold-retarded bagels hold their shape in the boil, develop a chewy crust, and bloom evenly in the oven. Under-proofed bagels will be dense; over-proofed will spread.",
        ),
        "english_muffin" to TechniqueSection(
            method = "Short Proof — 45–60 Minutes at Room Temperature",
            steps = listOf(
                "After shaping and placing in rings on the griddle (or parchment), proof at room temperature (70–75°F) for 45–60 minutes.",
                "The muffins should look slightly puffed and relaxed in the rings — not dramatically risen, but noticeably bigger than when first placed.",
                "Poke test: a very gentle press should spring back slowly. The dough is soft and enriched — do not over-proof or it will spread flat on the griddle.",
                "Begin heating the griddle during the final 15 minutes of proofing — it needs time to reach an even medium-low temperature before the muffins go on.",
            ),
            tip = "English muffins are more forgiving of slightly under-proof than over-proof. A slightly under-proofed muffin still has excellent texture; an over-proofed one spreads flat and loses its height on the griddle.",
        ),
        "pretzel" to TechniqueSection(
            method = "Brief Room-Temperature Rest — No Long Proof",
            steps = listOf(
                "After shaping, pretzels need only a brief 15–30 minute uncovered room-temperature rest before the alkaline bath. They do not require a traditional final proof.",
                "The alkaline bath causes the dough to puff slightly on its own, and the bake provides most of the remaining rise.",
                "For cold-retarded pretzels (12–24 hour refrigerator rest after shaping), remove from the fridge and proceed directly to the alkaline bath while still cold.",
                "Do not allow pretzels to proof until visibly puffy before bathing — over-proofed pretzels spread flat and lose their shape in the bath.",
            ),
            tip = "Pretzels proof far less than bread dough. Most of the final volume comes from oven spring, not the proof. A slight surface skin after the room-temperature rest is intentional and helps the bath adhere.",
        ),
        "brioche" to TechniqueSection(
            method = "Long Room-Temperature Proof — 2–4 Hours",
            steps = listOf(
                "After shaping and applying the first egg wash, proof at room temperature (70–72°F) for 2–4 hours. Brioche proofs more slowly than lean dough due to the fat and sugar content.",
                "Target: nearly doubled in volume. The loaf or rolls should look visibly puffy and feel very soft and jiggly when the pan is gently shaken.",
                "Poke test: press very lightly — the indent should fill back slowly over 3–5 seconds. Brioche holds its shape better than lean dough even when fully proofed.",
                "Apply the second egg wash just before loading the oven. The double application is what creates the signature deep mahogany glaze.",
            ),
            tip = "Brioche proofs on its own schedule — do not rush it with higher temperature. A slow, cool proof (68–70°F) produces a finer, more even crumb. A faster warm proof can cause irregular large holes and a fragile structure.",
        ),
    )

    // BAKING (7 keys — full shape-key set)

    val baking: Map<String, BakingSection> = mapOf(
        "batard" to BakingSection(
            temp = "500°F / 260°C",
            duration = "Lid on: 20 min → Lid off: 15–20 min",
            steam = "Preheated Dutch oven (oval if available). Preheat the pot for at least 45 minutes in the oven.",
            scoring = "Score with a lame at a shallow 15–20° angle. One long slash along the center, or 3 diagonal slashes offset from center. The shallow angle creates the ear.",
            internalTemp = "205–210°F / 96–99°C",
            tip = "Score straight from the refrigerator if cold-proofed — cold dough holds its shape and the blade drags less.",
        ),
        "boule" to BakingSection(
            temp = "500°F / 260°C",
            duration = "Lid on: 20 min → Lid off: 20 min",
            steam = "Preheated Dutch oven — essential. The lid traps steam that keeps the crust pliable for maximum oven spring in the first 20 minutes.",
            scoring = "One off-center crescent cut at a 30–45° angle gives a dramatic ear. A cross or decorative leaf pattern also works.",
            internalTemp = "205–210°F / 96–99°C",
            tip = "Don't open the Dutch oven during the first 20 minutes — you'll vent all the steam. Resist the urge.",
        ),
        "pullman" to BakingSection(
            temp = "375°F / 190°C",
            duration = "Lid on: 30 min → Lid off: 15–20 min",
            steam = "No steam needed — the sealed lid traps moisture naturally. Lightly oil the inside of the lid before placing it.",
            scoring = "No scoring — the Pullman lid shapes the loaf. Any surface dome is intentional and will flatten under the lid.",
            internalTemp = "200°F / 93°C",
            tip = "Tap the bottom of the loaf — a hollow sound means it's done. Pullman bakes lower and slower to avoid scorching the sides against the pan.",
        ),
        "pullman_soft" to BakingSection(
            temp = "375°F / 190°C",
            duration = "Lid on: 25–30 min → Lid off: 10–15 min",
            steam = "No steam needed — the sealed lid traps moisture from the enriched dough naturally. Lightly oil the lid before placing it.",
            scoring = "No scoring — the Pullman lid shapes the loaf. For a round-top sandwich loaf, leave the lid off entirely; the dough will crown naturally.",
            internalTemp = "200–205°F / 93–96°C",
            tip = "Enriched Pullman bakes slightly faster than lean dough at this temp. Start checking at 35 minutes total. Internal temp is the most reliable doneness check — don't rely on color alone.",
        ),
        "boule_8_oval" to BakingSection(
            temp = "500°F / 260°C",
            duration = "Lid on: 18 min → Lid off: 12–15 min",
            steam = "Preheated Dutch oven (oval if available). Preheat the pot for at least 45 minutes. The smaller mass opens faster — pull the lid slightly earlier than a standard boule.",
            scoring = "Score with a lame at a shallow 15–20° angle. One long confident slash along the central axis creates the ear and gives the loaf room to bloom.",
            internalTemp = "205–210°F / 96–99°C",
            tip = "Score straight from the refrigerator if cold-proofed — cold dough holds its shape and the blade drags less. At only ~475g this loaf is done faster than it looks.",
        ),
        "boule_10_round" to BakingSection(
            temp = "500°F / 260°C",
            duration = "Lid on: 25 min → Lid off: 20–25 min",
            steam = "Preheated Dutch oven — use the largest one you have. Preheat for at least 45 minutes. The extra mass needs the full 25 minutes of steam to open fully before the crust sets.",
            scoring = "One bold off-center crescent at 30–45° for a dramatic ear, or a decorative wheat/leaf pattern — both work well on this larger canvas.",
            internalTemp = "205–210°F / 96–99°C",
            tip = "A 1kg loaf takes longer to reach internal temp than the crust color suggests. Don't pull early — always probe with a thermometer. The bottom should sound hollow when tapped.",
        ),
        "boule_10_oval" to BakingSection(
            temp = "500°F / 260°C",
            duration = "Lid on: 22 min → Lid off: 18–20 min",
            steam = "Preheated Dutch oven (oval preferred for this shape). Preheat the pot for at least 45 minutes to ensure full thermal mass.",
            scoring = "Score with a lame at a shallow 15–20° angle. One long slash along the central axis, or 2–3 offset diagonal cuts. Score decisively — hesitation drags and deflates.",
            internalTemp = "205–210°F / 96–99°C",
            tip = "This is the most versatile format — large enough for a full loaf, shaped for clean slicing. Cold proofing overnight produces the tightest crumb structure and most dramatic ear on this size.",
        ),
    )

    // BAKING_BY_STYLE (10 keys)

    val bakingByStyle: Map<String, BakingSection> = mapOf(
        "baguette" to BakingSection(
            temp = "475–500°F / 246–260°C",
            duration = "Steam phase: 12–15 min → Vent & finish: 8–12 min",
            steam = "Heavy steam for the first 12–15 minutes is essential. Use a preheated steam pan, lava rocks, or a covered cloche. Vent fully before the final dry phase.",
            scoring = "Score at a very shallow 10–15° angle with a curved lame. One long diagonal slash, or 5–7 overlapping diagonal cuts offset slightly from center. Score decisively — one clean stroke per slash.",
            internalTemp = "205–210°F / 96–99°C",
            tip = "Bake on a preheated baking steel or stone — the thermal mass gives you the bottom crust and spring that a baking sheet can't. If using parchment, slide the baguettes directly onto the steel.",
        ),
        "ciabatta" to BakingSection(
            temp = "475°F / 246°C",
            duration = "15–20 min with steam → 5–10 min dry finish",
            steam = "Steam in the first 10 minutes is important for ciabatta's blistered crust. Use a preheated steam pan or ice cubes in a hot pan placed on the oven floor.",
            scoring = "No scoring — ciabatta is not scored. The random blistered top is natural and intentional.",
            internalTemp = "205°F / 96°C",
            tip = "Bake on a preheated baking stone or steel. Flip each piece seam-side down just before loading — the seam opens during baking into the characteristic irregular crust.",
        ),
        "focaccia" to BakingSection(
            temp = "425–450°F / 218–232°C",
            duration = "20–25 minutes total — no steam phase",
            steam = "No steam needed — the generous olive oil coating provides all the crust moisture required.",
            scoring = "No scoring. Dimple aggressively with oiled fingers all the way to the bottom of the pan immediately before baking.",
            internalTemp = "200–205°F / 93–96°C",
            tip = "Lift a corner at 20 minutes — the bottom should be deeply golden and pulling away from the pan sides. That's the sign of perfect focaccia.",
        ),
        "pizza_ny" to BakingSection(
            temp = "450–525°F / 232–274°C",
            duration = "6–10 minutes depending on oven and stone/steel temp",
            steam = "No steam. Bake on a preheated baking stone or steel — preheat at maximum temperature for at least 60 minutes. The thermal mass is what crisps the base.",
            scoring = "No scoring. Pizza dough is never scored — the cornicione inflates naturally from the gas trapped during fermentation.",
            internalTemp = "No internal temp target. Visual cues: golden to lightly charred rim, set (non-shiny) center. Lift with a spatula and check the base for deep golden color.",
            tip = "A baking steel outperforms a stone for NY pizza — it conducts heat faster and reaches higher base temperatures. Preheat your oven at maximum temperature for at least 60 minutes before baking.",
        ),
        "pizza_neo" to BakingSection(
            temp = "700–950°F / 370–510°C (wood-fired). Home oven: max temp + broiler.",
            duration = "60–90 seconds in a wood-fired oven. 6–10 minutes in a home oven at max temperature.",
            steam = "No steam. The rapid vaporization of water inside the dough at these temperatures creates all the puff and char naturally.",
            scoring = "No scoring. The cornicione inflates in seconds at these temperatures — scoring is not used and would deflate the rim.",
            internalTemp = "Leopard-spotting char on the cornicione and a fully set, slightly blistered top are the visual cues. No thermometer needed.",
            tip = "There is no adequate home-oven substitute for a 900°F wood-fired oven. A baking steel plus broiler is the best approximation — preheat the steel as high as your oven allows (60+ min), then switch to broiler mode for the last few minutes before loading.",
        ),
        "soft_roll" to BakingSection(
            temp = "375–425°F / 190–218°C. Dinner rolls / slider buns: 400–425°F. Hoagie / sub rolls: 375–400°F. Burger buns: 375°F.",
            duration = "12–16 min for small rolls. 18–22 min for hoagie rolls. 25–35 min for Pullman loaf.",
            steam = "No steam required. Egg wash replaces steam to promote tender, golden browning. For a steamed crust, tent the pan loosely with foil for the first 10 minutes.",
            scoring = "No scoring for rolls, buns, or hoagies — the soft crust opens naturally. A single decorative slash may be added to hoagie rolls. Pullman loaf: no scoring — the lid shapes the loaf.",
            internalTemp = "200–205°F / 93–96°C. Rolls bake fast — begin checking at 12 minutes. A hollow tap on the bottom confirms doneness.",
            tip = "For maximum softness, brush immediately with melted butter, olive oil, or water the moment rolls come out of the oven and cover loosely with a clean kitchen towel while they cool. This traps steam and keeps the crust tender — without this step, the crust firms up as it cools.",
        ),
        "bagel" to BakingSection(
            temp = "450–500°F / 232–260°C",
            duration = "After boiling: 20–25 minutes total — flip once at the halfway point",
            steam = "No steam. The boil creates the crust — steam after boiling would soften what the boil just set. Bake dry on a preheated sheet pan or baking steel.",
            scoring = "No scoring. Bagels are never scored — the tight crust set by the alkaline boil handles oven spring without slashing.",
            internalTemp = "No internal temp target. Visual cue: deep mahogany brown on all surfaces. Under-baked bagels are pale and feel soft; properly baked bagels are dark, shiny, and produce a hollow sound when tapped.",
            tip = "Boil each bagel 30–60 seconds per side in water + 1 tbsp barley malt syrup per quart. Longer boil = thicker crust, chewier texture. Shorter = thinner, crispier shell. Flip at the halfway point through baking for even browning.",
        ),
        "english_muffin" to BakingSection(
            temp = "Griddle: 325–350°F / 163–177°C (medium-low). Optional oven finish: 350°F.",
            duration = "5–7 minutes per side on the griddle. Oven finish (if needed): 5 minutes at 350°F.",
            steam = "No steam and no oven required — the griddle provides even conduction heat for the characteristic flat crust and open crumb. A brief oven finish ensures full doneness without burning the crust.",
            scoring = "No scoring. English muffins are griddle-cooked, not scored. The nooks and crannies develop naturally from steam expanding inside the muffin as it cooks against the hot griddle surface.",
            internalTemp = "200–205°F / 93–96°C. The muffin should feel firm (not squishy) and spring back when pressed. Insert a thermometer from the side into the center.",
            tip = "Medium-low heat is the key — a too-hot griddle burns the crust before the interior sets. If both sides are done but the muffin still feels squishy, finish in a 350°F oven for 5 minutes rather than increasing the griddle heat.",
        ),
        "pretzel" to BakingSection(
            temp = "450°F / 232°C",
            duration = "12–16 minutes — do not flip; bake until deep mahogany brown",
            steam = "No steam. The alkaline bath locks the crust — steam would counteract the bath's work. Bake dry on a parchment-lined sheet pan.",
            scoring = "Optional: one shallow slash across the widest part of the pretzel belly just before the alkaline bath. Pretzel buns / sliders: score a shallow 'X' across the top before bathing.",
            internalTemp = "190–200°F / 88–93°C internal. Visual cue: deep mahogany — nearly dark brown exterior. Pale pretzels are underbaked. Full color development is the goal.",
            tip = "The alkaline bath is what makes a pretzel a pretzel. Use 1 tsp food-grade lye dissolved in 4 cups water, or ⅓ cup baking soda per 4 cups water (baking soda produces lighter color and milder flavor). Lye pretzels bake faster — watch carefully in the last few minutes.",
        ),
        "brioche" to BakingSection(
            temp = "350°F / 177°C",
            duration = "Loaf: 35–45 minutes. Dinner rolls: 18–22 minutes. Burger buns: 16–20 minutes.",
            steam = "No steam. The egg wash creates the lacquered golden crust — steam would dilute it. Tent with foil after 25 minutes if the loaf top is browning too quickly.",
            scoring = "No scoring. The butter network and double egg wash create a naturally beautiful crust that doesn't need slashing. Scoring would drag and tear the enriched surface.",
            internalTemp = "190–195°F / 88–91°C for loaves. Rolls and buns: 185–190°F — they bake faster due to their higher surface-to-volume ratio.",
            tip = "Brush with egg wash twice: once before the final proof and once just before baking. This double application is what creates the deep mahogany glaze of bakery-quality brioche. A single wash gives a pale, matte result.",
        ),
    )
}
