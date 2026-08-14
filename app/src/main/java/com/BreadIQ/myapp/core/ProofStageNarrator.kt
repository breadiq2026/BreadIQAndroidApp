package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.ProofStage
import com.BreadIQ.myapp.model.ProofTimeResult
import com.BreadIQ.myapp.model.TemperatureUnit
import kotlin.math.abs

/**
 * Ported from the iOS app's `Core/ProofStageNarrator.swift`, itself a
 * port of `calcProofTime()`'s stage-text half (`api-server/src/routes/
 * calculator.ts`, lines 828-1148) — the 13-style-branch prose generator
 * that builds `stages`, `totalMinutes`, and `notes`, completing the
 * [ProofTimeResult] that [ProofTimeCalculator] (the previous porting
 * step) could not produce alone. Every branch was re-read directly from
 * the iOS source for this port rather than assumed from a pattern; the
 * one-off exceptions found there are called out inline (dead `isBaguette`
 * value, three unreachable brioche piece-size branches, and a
 * JS-number-to-string formatting gap for fractional `coldHours`).
 */
object ProofStageNarrator {

    /**
     * Matches how a JavaScript template literal (`${n}`) stringifies a
     * number — whole values print without a trailing `.0` (`12`, not
     * `12.0`). The source interpolates raw `coldHours` (a user-entered
     * value that can be fractional, e.g. 1.5) directly into prose several
     * times below; Kotlin's default `Double` string conversion would
     * print `12.0`/`1.5` inconsistently with JS's `12`/`1.5`. The
     * `abs(n) < 1e15` guard is a native-only crash-avoidance carried over
     * from the iOS port (not present in the original JS, which never
     * traps regardless of magnitude) — falls back to `n.toString()`
     * (never-crashing) for magnitudes no real `coldHours` value would
     * ever reach.
     */
    fun jsNumber(n: Double): String =
        if (n % 1.0 == 0.0 && abs(n) < 1e15) n.toLong().toString() else n.toString()

    /**
     * [unit] is display-only, per [TemperatureFormatting]'s own contract
     * — every Fahrenheit `Double` in [input]/[math] still comes in and
     * gets used for actual math exactly as before; only the *rendered
     * prose* below routes through [TemperatureFormatting] for this unit.
     * Defaults to [TemperatureUnit.FAHRENHEIT] so existing callers that
     * don't care about the toggle keep compiling and behaving exactly as
     * before.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun narrate(input: ProofTimeInput, math: ProofTimeMath, unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT): ProofTimeResult {
        // Re-derive the same handful of defaulted/resolved values
        // ProofTimeCalculator.calculate() computes internally. Each is a
        // trivial one-line elvis/comparison expression on the same
        // ProofTimeInput, so recomputing here (rather than growing
        // ProofTimeMath) carries no real drift risk — unlike the more
        // involved coldRetardPercent/estimatedIDTF values ProofTimeMath
        // already exposes for exactly that reason.
        val hasColdRetard = input.fermentationType == "cold" || ((input.coldRetardHours ?: 0.0) > 0)
        val coldHours = input.coldRetardHours ?: (if (hasColdRetard) 12.0 else 0.0)
        val coldTempF = input.coldRetardTempF ?: 38.0
        val finalTempF = input.finalProofTempF ?: input.ambientTempF
        val isHighFat = (input.fatPercent ?: 0.0) >= 15
        val isSpeedRun = input.isSpeedRun ?: false
        val numLoaves = input.numLoaves ?: 1

        val isFocaccia = input.breadStyle == "focaccia"
        val isCiabatta = input.breadStyle == "ciabatta"
        val isArtisan = input.breadStyle == "artisan"
        val isPizza = input.breadStyle == "pizza_ny" || input.breadStyle == "pizza_neo"
        val isSoftRoll = input.breadStyle == "soft_roll"
        val isBrioche = input.breadStyle == "brioche"
        val isBagel = input.breadStyle == "bagel"
        val isEnglishMuffin = input.breadStyle == "english_muffin"
        val isPretzel = input.breadStyle == "pretzel"
        // `isBaguette` (breadStyle == "baguette") is declared in the
        // source but never actually read anywhere in the function — a
        // dead value, same pattern as `targetDoughWeight`/`flourWeight`
        // in the two calculators ported before this one. Not carried over.
        val pretzelDipTime = if (input.loafStyle == "pretzel_sticks_6pk" || input.loafStyle == "pretzel_slider_6pk")
            "15–20 sec" else "20–25 sec"

        val stages = mutableListOf<ProofStage>()

        // MARK: - Pre-ferment build

        if (input.usePrefermant) {
            val pfHours = if (input.isSourdough) 4 else 12
            val pfMixNote = if (input.isSourdough)
                "Combine sourdough starter ingredients and mix until smooth. "
            else
                "Biga: mix briefly until shaggy (1–2 min). Poolish: mix 2–3 min until resembling a thick batter. "
            stages.add(
                ProofStage(
                    name = "Pre-ferment Build",
                    durationMinutes = pfHours * 60,
                    description = if (input.isSourdough)
                        "${pfMixNote}Let ripen for $pfHours hours at ${TemperatureFormatting.display(input.ambientTempF, unit)} until active, domed, and bubbly throughout — it should be at peak rise with a clean, yeasty aroma."
                    else
                        "${pfMixNote}Ferment for $pfHours hours at room temperature until doubled, domed, and starting to recede at the crown.",
                ),
            )
        }

        // MARK: - Bulk fermentation

        var bulkDesc = "Bulk ferment at ${TemperatureFormatting.display(input.ambientTempF, unit)} for approximately ${jsNumber(FormulaCalculator.r1(math.bulkFermentMinutes / 60.0))} hours."
        val growthNote = if (hasColdRetard && !isHighFat) "30–40%" else "50–75%"

        if (isFocaccia) {
            bulkDesc += " Optionally perform 2 gentle stretch-and-folds during the first hour. Bulk is complete when the dough is pillowy and has grown 50–75%."
        } else if (isCiabatta) {
            val sfInterval = (math.bulkFermentMinutes / 3.0).swiftRounded().toInt()
            bulkDesc += if (isSpeedRun)
                " SpeedRun: perform 3 sets of coil folds every $sfInterval min. Do NOT degas at any point — preserve the open bubble structure throughout."
            else
                " Perform 3 sets of coil folds every $sfInterval min. Do NOT degas at any point — preserve the open bubble structure throughout."
        } else if (isPizza) {
            bulkDesc += if (isSpeedRun)
                " No stretch-and-fold needed. SpeedRun: doubled yeast and warm water accelerate bulk significantly — watch closely. Bulk is complete when the dough is smooth, barely puffy, and just begins to feel extensible. Do not over-ferment; without cold retard, keep bulk tight and let the room-temp rest do the final relaxation work."
            else
                " No stretch-and-fold needed. Bulk is complete when the dough is smooth, slightly puffy, and noticeably more extensible than when it went in. Do not over-ferment — pizza dough continues to develop flavor during cold retard and the final room-temp rest."
        } else if (isSoftRoll) {
            bulkDesc += " No stretch-and-fold needed. Bulk is complete when the dough has grown 50–75%, feels soft and pillowy, and pulls away from the bowl sides cleanly. Enriched doughs (fat + sugar) rise faster than lean doughs — watch for signs of over-proofing."
        } else if (isBrioche) {
            bulkDesc += if (isSpeedRun)
                " SpeedRun on brioche will produce a same-day result but sacrifices some of the flavor complexity that extended fermentation develops. Watch closely for over-proofing signs — enriched doughs move faster with doubled yeast. Reduce bake temp by ${TemperatureFormatting.displayDeltaRange(10.0, 15.0, unit)} to compensate for the high sugar content. No stretch-and-fold needed."
            else
                " No stretch-and-fold needed. Target 1.5× volume increase — NOT doubled. Enriched doughs rise less than lean doughs and over-proofing destroys the delicate crumb. Bulk is complete when the dough is soft, pillowy, and has increased 40–60% in volume. Cold ferment after bulk is strongly recommended for best flavor and structure."
        } else if (isBagel) {
            bulkDesc += if (isSpeedRun)
                " SpeedRun bagels are workable but sacrifice the chewy depth that develops during a cold proof. With doubled yeast, keep bulk on the short side — lean stiff dough moves fast. Watch closely and move to shaping as soon as volume is up 50–60%."
            else
                " Bagel dough is stiff and lean — bulk progresses at a steady, predictable pace. No stretch-and-fold needed. Bulk is complete when the dough has grown 50–75% and feels smooth, firm, and slightly springy. Move directly to dividing, shaping, and cold proofing — do not delay after bulk."
        } else if (isEnglishMuffin) {
            bulkDesc += if (isSpeedRun)
                " SpeedRun English muffins are workable, but the characteristic nooks and crannies develop best with a slower, colder fermentation. Watch closely for over-proofing — this is a moderately enriched dough that moves faster with doubled yeast than its lean counterparts."
            else
                " No stretch-and-fold needed. Bulk is complete when the dough has grown 50–75% and feels soft and pillowy. Cold proofing after bulk is strongly recommended — the slower acidification develops flavor and the characteristic nook-and-cranny structure. Enriched doughs can over-proof quietly; watch for signs of excessive gas and slack texture."
        } else if (isPretzel) {
            bulkDesc += if (isSpeedRun)
                " SpeedRun works well for pretzels — same-day is the traditional production method anyway. With doubled yeast, keep a close eye on the dough. Lean stiff dough responds quickly and the window between ready and over-proofed is narrow."
            else
                " Pretzel dough is lean and stiff — bulk moves at a steady, predictable pace. No stretch-and-fold needed. Bulk is complete when the dough has grown 1.5× and feels smooth, resilient, and slightly springy. Do not over-ferment — move directly to dividing and shaping."
        } else if (isArtisan || input.isSourdough) {
            val sfInterval = if (isSpeedRun) 20 else 30
            bulkDesc += " Perform 4 sets of 4-way stretch-and-fold every $sfInterval minutes during the first ${if (isSpeedRun) "80 minutes" else "2 hours"}."
            if (isSpeedRun) {
                bulkDesc += " SpeedRun: shortened intervals accelerate gluten development — watch for over-proofing signs."
            }
            bulkDesc += " Bulk is complete when the dough has grown $growthNote, feels airy, and jiggles when the container is shaken."
        } else {
            bulkDesc += " Bulk is complete when the dough has grown $growthNote and feels airy and light."
        }

        if (hasColdRetard && coldHours > 0 && !isHighFat && !isBagel && !isBrioche && !input.isSourdough) {
            bulkDesc += " Fermentation continues actively during the first 2–4 hours in the refrigerator as the dough mass cools — the cold retard completes the proof. Stop bulk earlier than you normally would: targeting 30–40% volume increase at room temperature is intentional here."
        } else if (hasColdRetard && coldHours > 0 && !isHighFat && !isBagel && input.isSourdough) {
            bulkDesc += " Cold retard follows: stop bulk at 30–40% volume increase rather than the usual 50–75%. Fermentation continues at fridge temp during the first few hours — the cold retard finishes the job and builds flavor."
        }

        stages.add(ProofStage(name = "Bulk Fermentation", durationMinutes = math.bulkFermentMinutes, description = bulkDesc))

        // MARK: - Degas & bench rest

        if (!isFocaccia && !isCiabatta && !isPizza && !isBagel && !isEnglishMuffin) {
            var divideNote = ""
            val dwpp = FormulaCalculator.truthy(input.doughWeightPerPiece)
            if (numLoaves > 1 && dwpp != null) {
                divideNote = " After the rest, divide into $numLoaves equal pieces of ~${dwpp.swiftRounded().toInt()}g each."
            }
            stages.add(
                ProofStage(
                    name = "Degas & Bench Rest",
                    durationMinutes = 30,
                    description = if (isBrioche)
                        "Gently deflate the dough — do NOT punch down aggressively. The butter lamination is fragile; over-handling tears it. Fold the dough gently 2–3 times to redistribute gases and build some structure, then pre-shape into a loose round. Cover and bench rest 30 minutes. Handle with care: brioche dough tears easily when worked too hard.$divideNote"
                    else
                        "Punch the dough down to release CO2, fold it over itself, and form into a loose ball. Cover and rest at room temperature for 30 minutes. This relaxes the gluten for clean dividing and final shaping without tearing.$divideNote",
                ),
            )
        }

        // MARK: - Shape stage

        stages.add(
            shapeStage(
                input = input, numLoaves = numLoaves,
                isFocaccia = isFocaccia, isCiabatta = isCiabatta, isPizza = isPizza,
                isBagel = isBagel, isEnglishMuffin = isEnglishMuffin, isPretzel = isPretzel,
                isBrioche = isBrioche, isSoftRoll = isSoftRoll,
            ),
        )

        // MARK: - Cold ferment / room-temp recovery / final proof

        if (hasColdRetard && coldHours > 0) {
            stages.add(
                ProofStage(
                    name = if (isBagel) "Cold Proof (Bagel)" else "Cold Ferment",
                    durationMinutes = (coldHours * 60).swiftRounded().toInt(),
                    description = if (isBagel)
                        "Refrigerate shaped bagels uncovered at ${TemperatureFormatting.display(coldTempF, unit)} for ${jsNumber(coldHours)} hour${if (coldHours != 1.0) "s" else ""}. This is the cold proof — not a bulk ferment. It develops the characteristic chew, malty depth, and gives the surface a slight skin that helps the boiling bath adhere cleanly. Uncovered is intentional. Remove from the refrigerator when ready to boil."
                    else
                        "Ferment in the refrigerator at ${TemperatureFormatting.display(coldTempF, unit)} for ${jsNumber(coldHours)} hour${if (coldHours != 1.0) "s" else ""}. Slow fermentation concentrates flavor compounds, strengthens gluten, and develops complexity. The dough completes approximately ${math.coldRetardPercent}% of its final proof during this stage.",
                ),
            )

            // Guaranteed non-null here: ProofTimeCalculator computes
            // recoveryMinutes from this exact same `hasColdRetard &&
            // coldHours > 0` condition on the same input, matching the
            // source's own non-null assertion.
            val recMins = math.recoveryMinutes!!
            if (isHighFat || isBagel) {
                stages.add(
                    ProofStage(
                        name = "Room-Temp Recovery",
                        durationMinutes = recMins,
                        description = if (isBagel)
                            "While the bagels come up to temperature, bring a wide pot of water + barley malt syrup to a rolling boil. Have your toppings ready — the surface dries in seconds after boiling and toppings must go on immediately. Allow approximately $recMins minutes for setup."
                        else
                            "Remove from the refrigerator and leave at ${TemperatureFormatting.display(input.ambientTempF, unit)} for approximately $recMins minutes to bring the dough back to working temperature before final proof.",
                    ),
                )
            }

            if (math.coldRetardPercent >= 95) {
                stages.add(
                    coldCompleteStage(
                        math = math, finalTempF = finalTempF, pretzelDipTime = pretzelDipTime, unit = unit,
                        isFocaccia = isFocaccia, isPizza = isPizza, isBrioche = isBrioche, isBagel = isBagel,
                        isEnglishMuffin = isEnglishMuffin, isPretzel = isPretzel, isHighFat = isHighFat,
                    ),
                )
            } else {
                stages.add(
                    postRetardFinalProofStage(
                        math = math, finalTempF = finalTempF, pretzelDipTime = pretzelDipTime, unit = unit,
                        isFocaccia = isFocaccia, isCiabatta = isCiabatta, isPizza = isPizza, isBrioche = isBrioche,
                        isBagel = isBagel, isEnglishMuffin = isEnglishMuffin, isPretzel = isPretzel,
                    ),
                )
            }
        } else {
            stages.add(
                noRetardFinalProofStage(
                    input = input, math = math, finalTempF = finalTempF, pretzelDipTime = pretzelDipTime, unit = unit,
                    isFocaccia = isFocaccia, isPizza = isPizza, isBrioche = isBrioche, isBagel = isBagel,
                    isEnglishMuffin = isEnglishMuffin, isPretzel = isPretzel,
                ),
            )
        }

        val totalMinutes = stages.sumOf { it.durationMinutes }

        // `inputFinalTempF &&` in the source is a truthy check on the RAW
        // optional field (not the resolved `finalTempF`, which defaults
        // to ambient and would make this comparison trivially false
        // whenever unset) — an explicit 0°F would also count as falsy
        // here, matching JS exactly via `truthy()`.
        var proofEnvNote = ""
        val rawFinalTempF = FormulaCalculator.truthy(input.finalProofTempF)
        if (rawFinalTempF != null && rawFinalTempF.swiftRounded().toInt() != input.ambientTempF.swiftRounded().toInt()) {
            proofEnvNote = " Final proof at ${TemperatureFormatting.display(finalTempF, unit)} (vs bulk at ${TemperatureFormatting.display(input.ambientTempF, unit)})."
        }

        val notes: String = if (hasColdRetard && coldHours > 0) {
            if (isHighFat)
                "Cold ferment at ${TemperatureFormatting.display(coldTempF, unit)} for ${jsNumber(coldHours)}h completed ~${math.coldRetardPercent}% of the final proof. Allow ~${math.recoveryMinutes!!} min recovery at room temp before final proof — cold fat in enriched doughs needs time to soften before baking.$proofEnvNote"
            else
                "Cold ferment at ${TemperatureFormatting.display(coldTempF, unit)} for ${jsNumber(coldHours)}h completed ~${math.coldRetardPercent}% of the final proof. Score and bake directly from the refrigerator — cold dough scores cleanly, holds its shape, and the temperature contrast drives better oven spring.$proofEnvNote"
        } else {
            "Proof times are estimates — the poke test is the ground truth.$proofEnvNote"
        }

        return ProofTimeResult(
            bulkFermentMinutes = math.bulkFermentMinutes,
            finalProofMinutes = math.finalProofMinutes,
            totalMinutes = totalMinutes,
            coldFermentHours = math.coldFermentHours,
            recoveryMinutes = math.recoveryMinutes,
            notes = notes,
            stages = stages,
        )
    }

    // MARK: - Shape stage (source lines 917-1033)

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun shapeStage(
        input: ProofTimeInput, numLoaves: Int,
        isFocaccia: Boolean, isCiabatta: Boolean, isPizza: Boolean, isBagel: Boolean, isEnglishMuffin: Boolean,
        isPretzel: Boolean, isBrioche: Boolean, isSoftRoll: Boolean,
    ): ProofStage {
        if (isFocaccia) {
            val focacciaPanLabel = when (input.loafStyle) {
                "focaccia_13x18" -> "13\"×18\""
                "focaccia_9x13" -> "9\"×13\" baking dish"
                "focaccia_9round" -> "9\" round"
                else -> "10\"×15\""
            }
            return ProofStage(
                name = "Scale to Pan",
                durationMinutes = 10,
                description = "Generously oil your $focacciaPanLabel pan with extra-virgin olive oil. Turn the bulk-fermented dough directly into the pan — no pre-shaping, no tight forming. Allow the dough to relax and spread naturally; coax gently toward the edges without forcing. Cover loosely.",
            )
        }

        if (isCiabatta) {
            val isPanino = input.loafStyle == "ciabatta_panino_6"
            val dwpp = FormulaCalculator.truthy(input.doughWeightPerPiece)
            val description = if (isPanino) {
                val paninoG = if (dwpp != null) " of ~${(dwpp / 6).swiftRounded().toInt()}g each" else ""
                "Gently divide the bulk dough into 6 equal pieces$paninoG using a floured bench scraper — do NOT roll, press, or pre-shape. Each piece should retain its irregular, open structure. The flat shape means ciabatta panini have a high surface-area-to-volume ratio and reach final proof noticeably faster than a full slab. Begin checking well before the estimated time."
            } else {
                "DO NOT pre-shape, roll, or degas. Gently turn the bulk-fermented dough onto an oiled tray or well-floured couche — maintain the full slab structure. All gas and bubble development happened during bulk; any aggressive handling destroys it. Allow the dough to spread and relax naturally. Cover loosely."
            }
            return ProofStage(name = if (isPanino) "Divide to Panini" else "Transfer to Tray / Couche", durationMinutes = 10, description = description)
        }

        if (isPizza) {
            val dwpp = FormulaCalculator.truthy(input.doughWeightPerPiece)
            val portionDesc = if (numLoaves > 1 && dwpp != null)
                "Divide the bulk dough into $numLoaves equal portions of ~${dwpp.swiftRounded().toInt()}g each."
            else
                "Divide the bulk dough into individual portions."
            return ProofStage(
                name = "Portion & Ball",
                durationMinutes = 15,
                description = "$portionDesc Gently pre-shape each piece into a smooth, taut ball using a light drag technique on an unfloured surface — do NOT degas or overwork. Place in a lightly oiled container or on a floured tray, cover tightly, and rest for at least 20 minutes before stretching. Well-rested balls stretch evenly; underrested balls tear.",
            )
        }

        if (isBagel) {
            val piecesPerBatch = 6
            val totalPieces = numLoaves * piecesPerBatch
            val dwpp = FormulaCalculator.truthy(input.doughWeightPerPiece)
            val pieceG = if (dwpp != null) " of ~${(dwpp / piecesPerBatch).swiftRounded().toInt()}g each" else ""
            val totalNote = if (numLoaves > 1) " ($numLoaves batches × $piecesPerBatch = $totalPieces total)" else ""
            return ProofStage(
                name = "Divide & Shape (Bagel)",
                durationMinutes = 20,
                description = "Divide the bulk dough into $totalPieces equal portions$pieceG$totalNote. Pre-shape each piece into a very tight, smooth ball — maximum surface tension is critical for clean holes and tight crumb. Poke a floured thumb through the center, stretch the opening to ~2\" diameter. Place immediately on parchment-lined pans. Refrigerate uncovered. Start the 20-minute timer once all pieces are shaped and in the refrigerator — the cold proof begins now. Do not leave at room temperature after shaping.",
            )
        }

        if (isEnglishMuffin) {
            val piecesPerBatch = 12
            val totalPieces = numLoaves * piecesPerBatch
            val dwpp = FormulaCalculator.truthy(input.doughWeightPerPiece)
            val pieceG = if (dwpp != null) " of ~${(dwpp / piecesPerBatch).swiftRounded().toInt()}g each" else ""
            val totalNote = if (numLoaves > 1) " ($numLoaves batches × $piecesPerBatch = $totalPieces total)" else ""
            val ringSize = if (input.loafStyle == "em_large_12pk") "4\"" else "3.5\""
            return ProofStage(
                name = "Place in Rings",
                durationMinutes = 15,
                description = "Gently deflate the bulk dough and divide into $totalPieces equal portions$pieceG$totalNote. Round each piece into a smooth ball using gentle surface tension — do not over-tighten. Place each in a well-greased $ringSize ring mold on a semolina-dusted surface or parchment. Do not flatten — the ring controls the final shape. Dust the tops lightly with semolina. Cover loosely and proof.",
            )
        }

        if (isPretzel) {
            val piecesPerBatch = 6
            val totalPieces = numLoaves * piecesPerBatch
            val dwpp = FormulaCalculator.truthy(input.doughWeightPerPiece)
            val pieceG = if (dwpp != null) " of ~${(dwpp / piecesPerBatch).swiftRounded().toInt()}g each" else ""
            val totalNote = if (numLoaves > 1) " ($numLoaves batches × $piecesPerBatch = $totalPieces total)" else ""
            val shapeDesc = when (input.loafStyle) {
                "pretzel_sticks_6pk" -> "Roll each ball into an even stick (8–10\") — consistent diameter means consistent browning."
                "pretzel_slider_6pk" -> "Shape each ball into a smooth, slightly flattened bun. Score a shallow cross on top before bathing."
                else -> "Roll each ball into a 20–24\" rope starting from the center, working outward with steady pressure. Form a U-shape, cross the tails twice at the top, fold down onto the arch, and press the ends firmly to seal."
            }
            return ProofStage(
                name = "Rope & Shape",
                durationMinutes = 20,
                description = "Divide the dough into $totalPieces portions$pieceG$totalNote. Pre-shape each into a tight ball and rest uncovered for 10–15 min — slight surface drying helps the alkaline bath adhere evenly. $shapeDesc Leave shaped pretzels uncovered at room temperature until ready to bathe.",
            )
        }

        // Rolls / hoagie / baguette / brioche / pullman / generic boule-batard.
        val loafStyleTruthy = FormulaCalculator.truthy(input.loafStyle)
        val isRolls = loafStyleTruthy != null && (
            loafStyleTruthy.contains("_15oz") || loafStyleTruthy.contains("_25oz") ||
                loafStyleTruthy == "rolls" || loafStyleTruthy == "soft_burger_bun" || loafStyleTruthy == "brioche_burger_bun_4oz"
            )
        val isHoagie = input.loafStyle == "soft_hoagie_6in" || input.loafStyle == "soft_hoagie_8in"
        val isBaguetteShape = loafStyleTruthy != null && loafStyleTruthy.startsWith("baguette_") && !loafStyleTruthy.contains("rolls")
        val mod = maxOf(0.75, minOf(1.25, input.baguetteSizeModifier ?: 1.0))

        var baguetteSizeNote = ""
        if (mod < 0.9) {
            baguetteSizeNote = " Width modifier is set toward ficelle — this is a thinner, lighter piece that will proof even faster than a standard baguette of this length."
        } else if (mod > 1.1) {
            baguetteSizeNote = " Width modifier is set toward plump — slightly more mass means marginally more time, but still faster than a round loaf of similar weight."
        }

        var baguetteShapeNote = ""
        if (input.loafStyle == "baguette_6") {
            baguetteShapeNote = " The demi (6\") format is very small — expect it to be ready well before a full-length baguette."
        } else if (input.loafStyle == "baguette_12") {
            baguetteShapeNote = " The half-baguette format is compact — monitor closely from the 80% mark."
        }

        val dwpp = FormulaCalculator.truthy(input.doughWeightPerPiece)
        val description: String
        if (isRolls) {
            val piecesPerBatch = if (input.loafStyle == "soft_burger_bun" || input.loafStyle == "brioche_burger_bun_4oz") 6 else 13
            val totalPieces = numLoaves * piecesPerBatch
            val pieceG = if (dwpp != null) " of ~${(dwpp / piecesPerBatch).swiftRounded().toInt()}g each" else ""
            val totalNote = if (numLoaves > 1) " ($numLoaves batches × $piecesPerBatch per batch = $totalPieces total)" else ""
            val burgerBunNote = if (input.loafStyle == "soft_burger_bun") " For burger buns: flatten each ball into a 3.5–4\" disc before placing on the pan." else ""
            description = "Divide the bulk dough into $totalPieces individual pieces$pieceG$totalNote. Pre-shape each piece into a smooth, tight ball and bench rest 10–15 min covered, then roll into final shape.$burgerBunNote Small pieces have high surface-area-to-volume ratios — they equilibrate to ambient temperature fast and proof significantly quicker than a full loaf. Watch carefully: over-proofing risk is real."
        } else if (isHoagie) {
            val hoagieCount = numLoaves * 6
            val hoagieG: Double = if (input.loafStyle == "soft_hoagie_6in") 128.0 else 170.0
            val overrideG = dwpp?.let { (it / 6).swiftRounded() } ?: hoagieG
            val batchNote = if (numLoaves > 1) " ($numLoaves batches × 6)" else ""
            description = "Divide the bulk dough into $hoagieCount portions of ~${overrideG.toInt()}g each$batchNote. Pre-shape each into a ball, bench rest 10 minutes covered, then roll into a cylinder using the batard technique — fold the top down, the bottom up, seal the seam, and roll gently to even length. Place seam-side down on a parchment-lined sheet pan. Hoagie rolls proof faster than a full loaf — begin checking at ~75% of the estimated time."
        } else if (isBaguetteShape) {
            description = "Shape using the letter-fold and roll technique — build even surface tension along the full length. Place seam-side up in a well-floured couche or on a parchment-lined peel. Baguettes have far more surface area per gram than round loaves and proof faster regardless of size. Begin checking at ~80% of the estimated time.$baguetteShapeNote$baguetteSizeNote"
        } else if (isBrioche) {
            description = if (numLoaves > 1 && dwpp != null)
                "Divide the dough into $numLoaves equal portions of ~${dwpp.swiftRounded().toInt()}g each. Generously butter each 9×5\" loaf pan. Shape each portion: pat flat, roll firmly from the top edge down, pinch the seam tightly, and place seam-side down in the pan. Brush generously with egg wash before the final proof — brioche's lacquered crust depends on it. Handle gently: the butter lamination tears under rough shaping."
            else
                "Generously butter the 9×5\" loaf pan. Shape the dough into a log: pat flat, roll firmly from the top edge down, pinch the seam, and place seam-side down in the pan. Brush generously with egg wash. Handle gently — the butter network is fragile and tears under aggressive shaping. Brioche holds its pan shape beautifully; over-handling is the only real risk."
        } else if (isSoftRoll && input.loafStyle == "pullman") {
            description = if (numLoaves > 1 && dwpp != null)
                "Divide into $numLoaves equal portions of ~${dwpp.swiftRounded().toInt()}g each. Shape each into a log: fold the dough over itself and roll firmly into a cylinder just shorter than the pan. Place in a greased Pullman pan, seam-side down. Lid goes on after final proof has filled the pan to ~80%."
            else
                "Shape into a log just shorter than the pan. Place in a greased Pullman pan, seam-side down. Lid goes on after final proof has filled the pan to ~80%."
        } else if (input.loafStyle == "pullman_country" || (input.loafStyle == "pullman" && !isSoftRoll)) {
            description = if (numLoaves > 1 && dwpp != null)
                "Divide into $numLoaves equal portions of ~${dwpp.swiftRounded().toInt()}g each. Flatten each portion to the width of a 9\" loaf pan, roll tightly from one end, pinch the seam closed, and place seam-side down in a well-greased 9\"×4\"×4\" loaf pan."
            else
                "Flatten the dough to the width of a 9\" loaf pan. Roll tightly from one end, pinch the seam closed, and place seam-side down in a well-greased 9\"×4\"×4\" loaf pan. No banneton — this loaf proofs and bakes in the pan."
        } else if (numLoaves > 1 && dwpp != null) {
            description = "Divide into $numLoaves equal pieces of ~${dwpp.swiftRounded().toInt()}g each, then shape into final form — batard or boule. Focus on surface tension: the tighter the surface, the better the oven spring. Place seam-side up in a floured banneton or proofing pan."
        } else {
            description = "Shape into final form — batard or boule. Focus on surface tension: the tighter the surface, the better the oven spring. Place seam-side up in a floured banneton or proofing pan."
        }

        return ProofStage(
            name = if (isRolls) "Divide & Pre-shape" else if (isHoagie) "Divide & Roll" else "Final Shape",
            durationMinutes = if (isRolls || isHoagie) 15 else 10,
            description = description,
        )
    }

    // MARK: - Cold-retard-complete stage (coldRetardPercent >= 95, source lines 1066-1085)

    @Suppress("LongParameterList")
    private fun coldCompleteStage(
        math: ProofTimeMath, finalTempF: Double, pretzelDipTime: String, unit: TemperatureUnit,
        isFocaccia: Boolean, isPizza: Boolean, isBrioche: Boolean, isBagel: Boolean,
        isEnglishMuffin: Boolean, isPretzel: Boolean, isHighFat: Boolean,
    ): ProofStage {
        val name = when {
            isFocaccia -> "Pan Temper & Dimple"
            isPizza -> "Temper & Stretch"
            isBagel -> "Boil & Top"
            isEnglishMuffin -> "Ring Proof"
            isPretzel -> "Rest & Alkaline Bath"
            isHighFat -> "Bench Temper"
            else -> "Score from Cold"
        }

        val description = when {
            isFocaccia -> "The cold ferment completed the final proof. Allow ${math.finalProofMinutes} minutes at ${TemperatureFormatting.display(finalTempF, unit)} to temper. Then dimple deeply with oiled fingers across the entire surface, drizzle generously with olive oil, and bake."
            isPizza -> "Allow dough balls to temper at room temperature for ${math.finalProofMinutes} minutes until soft and extensible. Stretch by hand to your target diameter — do NOT use a rolling pin, which deflates the rim. Sauce, top, and bake immediately on a preheated stone or steel."
            isBrioche -> "The cold ferment completed the final proof. Allow ${math.finalProofMinutes} minutes at ${TemperatureFormatting.display(finalTempF, unit)} to temper. The cold retard firms the butter network — giving brioche its tight, feathered crumb. Bake in a well-buttered pan; the cold shape holds beautifully. Egg wash generously before loading."
            isBagel -> "Boil each bagel in the barley malt solution for 45–60 seconds per side. Transfer immediately to a wire rack — the window to add toppings is seconds, not minutes. Load onto a preheated baking surface and bake at 425°F for 15–18 min until deep mahogany. Internal temp 205–210°F."
            isEnglishMuffin -> "Proof rings at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes until the dough domes slightly above the ring rim. Preheat an ungreased griddle or cast-iron to medium-low (325–350°F surface temp). Cook 6–8 min per side. Internal temp 200–205°F."
            isPretzel -> "Rest shaped pretzels uncovered for 15–20 min — gluten relaxes, surface dries slightly for better bath adhesion. Baked baking soda bath: Spread baking soda on a foil-lined sheet pan and bake at 250°F for 1 hour before use — this significantly increases alkalinity for a better crust. No time? Unbaked baking soda works but produces a milder result. Dissolve 50g baked baking soda plus 1 tbsp barley malt syrup per 1000g water. Dip each pretzel $pretzelDipTime. Score the arch once, apply coarse pretzel salt. Bake at 400°F for 10–14 min to deep mahogany. Internal temp 190–195°F."
            isHighFat -> "The cold ferment completed the final proof. Allow ${math.finalProofMinutes} minutes at ${TemperatureFormatting.display(finalTempF, unit)} to temper — cold fat in enriched doughs can crack the crust when loaded into a hot oven directly from the refrigerator. Score after tempering."
            else -> "The cold ferment completed the final proof. Score directly from the refrigerator — cold dough holds its shape out of the banneton, scores more cleanly, and the temperature contrast with a hot oven produces better oven spring. Load immediately after scoring."
        }

        return ProofStage(name = name, durationMinutes = if (isHighFat) math.finalProofMinutes else 0, description = description)
    }

    // MARK: - Post-retard final proof stage (coldRetardPercent < 95, source lines 1087-1105)

    @Suppress("LongParameterList")
    private fun postRetardFinalProofStage(
        math: ProofTimeMath, finalTempF: Double, pretzelDipTime: String, unit: TemperatureUnit,
        isFocaccia: Boolean, isCiabatta: Boolean, isPizza: Boolean, isBrioche: Boolean,
        isBagel: Boolean, isEnglishMuffin: Boolean, isPretzel: Boolean,
    ): ProofStage {
        val name = when {
            isFocaccia -> "Final Proof & Dimple"
            isCiabatta -> "Final Proof & Cut"
            isPizza -> "Temper & Stretch"
            isBagel -> "Boil & Top"
            isEnglishMuffin -> "Ring Proof"
            isPretzel -> "Rest & Alkaline Bath"
            else -> "Final Proof (post-retard)"
        }

        val description = when {
            isFocaccia -> "Proof in the pan at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes. When puffy and filling the pan, dimple deeply with oiled fingers, drizzle generously with olive oil, and bake immediately."
            isCiabatta -> "Proof at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes until the dough is puffy and jiggly. Cut into portions AFTER proofing — not before — using a bench scraper or pizza wheel with a single decisive cut. Do not degas when cutting."
            isPizza -> "Allow dough balls to temper at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes until soft, puffy, and very extensible. Stretch by hand — do NOT use a rolling pin. Sauce, top, and bake immediately on a preheated stone or steel."
            isBrioche -> "Proof at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes. The cold ferment completed ${math.coldRetardPercent}% of the final proof. Brioche: target 1.5× volume — NOT doubled. Poke test: very gentle press leaves a faint dimple that fills back slowly. Over-proofed brioche collapses in the oven — better slightly under than over."
            isBagel -> "The cold proof developed the bagels. Boil each bagel in the barley malt solution for 45–60 seconds per side, transfer immediately to a rack, top while still wet, and bake at 425°F for 15–18 min to deep mahogany. Internal temp 205–210°F."
            isEnglishMuffin -> "Proof rings at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes until the dough domes slightly above the ring rim and feels puffy. The cold ferment completed ${math.coldRetardPercent}% of the proof. Cook on an ungreased griddle at medium-low for 6–8 min per side. Internal temp 200–205°F."
            isPretzel -> "Rest shaped pretzels uncovered for 15–20 min — gluten relaxes, surface dries slightly for better bath adhesion. Baked baking soda bath: Spread baking soda on a foil-lined sheet pan and bake at 250°F for 1 hour — this significantly increases alkalinity. No time? Unbaked works but produces a milder result. Dissolve 50g baked baking soda plus 1 tbsp barley malt syrup per 1000g water. Dip each pretzel $pretzelDipTime. Score the arch once, apply coarse pretzel salt. Bake at 400°F for 10–14 min to deep mahogany. Internal temp 190–195°F."
            else -> "Proof at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes. The cold ferment completed ${math.coldRetardPercent}% of the final proof. Use the poke test: slow spring-back leaving a faint indent means ready."
        }

        return ProofStage(name = name, durationMinutes = math.finalProofMinutes, description = description)
    }

    // MARK: - No-cold-retard final proof stage (source lines 1107-1125)

    @Suppress("LongParameterList")
    private fun noRetardFinalProofStage(
        input: ProofTimeInput, math: ProofTimeMath, finalTempF: Double, pretzelDipTime: String, unit: TemperatureUnit,
        isFocaccia: Boolean, isPizza: Boolean, isBrioche: Boolean, isBagel: Boolean,
        isEnglishMuffin: Boolean, isPretzel: Boolean,
    ): ProofStage {
        val name = when {
            isFocaccia -> "Proof & Dimple"
            isPizza -> "Temper & Stretch"
            isBagel -> "Boil & Top"
            isEnglishMuffin -> "Ring Proof"
            isPretzel -> "Rest & Alkaline Bath"
            else -> "Final Proof"
        }

        val tempDiffNote = if (finalTempF != input.ambientTempF)
            " (different from your bulk ambient of ${TemperatureFormatting.display(input.ambientTempF, unit)})"
        else
            ""

        val description = when {
            isFocaccia -> "Proof in the pan at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes until the dough is puffy and fills the pan. Dimple deeply with well-oiled fingers across the entire surface. Drizzle generously with olive oil, add your choice of toppings, and bake immediately."
            isPizza -> "Rest portioned dough balls at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes until soft, puffy, and very extensible. Stretch by hand to your target diameter — do NOT use a rolling pin, which deflates the rim. Sauce, top, and bake immediately on a preheated stone or steel."
            isBrioche -> "Proof at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes$tempDiffNote. Brioche: target 1.5× volume — NOT doubled. Poke test: very gentle press leaves a faint dimple that fills back slowly. Over-proofed brioche collapses in the oven — better slightly under than over. Egg wash generously before baking for a deep, lacquered crust."
            isBagel -> "Allow shaped bagels to rest covered for approximately ${math.finalProofMinutes} minutes at room temperature — do not let them over-proof or they will blow out in boiling water. Bring water + barley malt syrup to a full rolling boil. Boil each bagel 45–60 seconds per side. Transfer immediately to a wire rack, top while still wet, and bake at 425°F for 15–18 min to deep mahogany. Internal temp 205–210°F."
            isEnglishMuffin -> "Proof rings at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes until the dough domes slightly above the ring rim and feels puffy — not jiggly. Preheat an ungreased griddle or cast-iron to medium-low (325–350°F surface). Cook 6–8 min per side undisturbed. Internal temp 200–205°F. If crust colors before center sets, finish in a 350°F oven."
            isPretzel -> "Rest shaped pretzels uncovered for 15–20 min — gluten relaxes, surface dries slightly for better bath adhesion. Baked baking soda bath: Spread baking soda on a foil-lined sheet pan and bake at 250°F for 1 hour before use — this significantly increases alkalinity for a better crust. Short on time? Unbaked baking soda works but produces a milder result. Dissolve 50g baked baking soda (or unbaked) plus 1 tbsp barley malt syrup per 1000g water. Dip each pretzel $pretzelDipTime. Transfer to parchment. Score the thick arch once with a sharp blade, apply coarse pretzel salt. Bake at 400°F for 10–14 min to deep mahogany. Internal temp 190–195°F."
            else -> "Proof at ${TemperatureFormatting.display(finalTempF, unit)} for approximately ${math.finalProofMinutes} minutes$tempDiffNote. Poke test: a gentle press should spring back slowly, leaving a faint indent."
        }

        return ProofStage(name = name, durationMinutes = math.finalProofMinutes, description = description)
    }
}
