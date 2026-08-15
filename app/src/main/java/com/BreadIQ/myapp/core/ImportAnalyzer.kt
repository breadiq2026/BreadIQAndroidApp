package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.TemperatureUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * One row from the import wizard's ingredient list, already classified
 * and gram-converted ([IngredientClassifier]/[IngredientDensityConverter]).
 * Mirrors `/api/import/analyze`'s request-body ingredient shape
 * (`artifacts/api-server/src/routes/import.ts`, `router.post("/analyze")`).
 */
data class ImportIngredientInput(
    val name: String,
    val category: IngredientCategory,
    val grams: Double,
    val flourType: FlourType? = null,
    val yeastType: YeastType? = null,
    val fatSource: FatSource? = null,
    val sugarSource: SugarSource? = null,
    val dairySource: DairySource? = null,
    val flagged: String? = null,
)

enum class ImportFermentationType(val rawValue: String) { STRAIGHT("straight"), COLD("cold") }

enum class ImportPreFerment(val rawValue: String) { NONE("none"), BIGA("biga"), POOLISH("poolish") }

data class ImportEnvironmentInput(
    val ambientTempF: Double,
    val waterTempF: Double,
    val fermentationType: ImportFermentationType,
    val coldRetardHours: Double? = null,
    val preFerment: ImportPreFerment,
)

data class ImportBakerPercentages(
    val water: Double,
    val salt: Double,
    val fat: Double,
    val yeast: Double,
    val sugar: Double? = null,
    val eggs: Double? = null,
    val dairy: Double? = null,
)

data class ImportIngredientWeights(
    val flour: Double,
    val water: Double,
    val salt: Double,
    val fat: Double,
    val yeast: Double,
    val sugar: Double? = null,
    val eggs: Double? = null,
    val dairy: Double? = null,
)

data class ImportProofStage(val name: String, val durationMinutes: Int, val description: String)

data class ImportActiveStep(val step: String, val instruction: String)

data class ImportTierResult(val tier: String, val description: String)

data class ImportAnalysisResult(
    val recipeName: String? = null,
    val totalFlourGrams: Double,
    val bakerPercentages: ImportBakerPercentages,
    val ingredientWeights: ImportIngredientWeights,
    val totalDoughWeight: Double,
    val effectiveHydrationPercent: Double,
    val totalFatPercent: Double,
    val sugarPercent: Double,
    val yeastPercent: Double,
    val yeastType: YeastType,
    val inferredStyle: String,
    val hydrationTier: ImportTierResult,
    val fatTier: ImportTierResult,
    val sugarTier: ImportTierResult,
    val fermentationMultiplier: Double,
    val flags: List<String>,
    val advisories: List<String>,
    val stages: List<ImportProofStage>,
    val bulkFermentMinutes: Int,
    val finalProofMinutes: Int,
    val totalMinutes: Int,
    val activeSteps: List<ImportActiveStep>,
)

sealed class ImportAnalysisError {
    /** "Recipe must contain at least one flour ingredient." — the route's own 400 response when `byCategory("flour") <= 0`. */
    data object NoFlour : ImportAnalysisError()
}

sealed class ImportAnalysisOutcome {
    data class Success(val result: ImportAnalysisResult) : ImportAnalysisOutcome()
    data class Failure(val error: ImportAnalysisError) : ImportAnalysisOutcome()
}

/** `calcProofTime`'s return tuple — Kotlin has no anonymous labeled tuples, so this stands in for the source's `(stages:, bulkFermentMinutes:, finalProofMinutes:, totalMinutes:)`. */
data class ImportProofTimeMath(
    val stages: List<ImportProofStage>,
    val bulkFermentMinutes: Int,
    val finalProofMinutes: Int,
    val totalMinutes: Int,
)

/**
 * Port of `POST /api/import/analyze` (`artifacts/api-server/src/routes/import.ts`,
 * lines 418-623) — the wizard's "ingredients → formula" analysis, run
 * locally so `ImportModal`'s Step 3 works fully offline instead of
 * staying a permanent network stub. Same "port the computation, not the
 * round-trip" call already made for the Calculator tab's
 * `FormulaCalculator`/`ProofTimeCalculator`, since this endpoint is this
 * wizard's only real payoff — leaving it server-only would ship 2
 * working steps and a permanently-stubbed 3rd.
 *
 * **`calcProofTime` (lines 113-222) is a genuinely separate formula from
 * `ProofTimeCalculator.calculate`, not reusable** — confirmed by direct
 * comparison: different base constant (`baseBulk = 90` here vs.
 * `ProofTimeCalculator`'s own baseline), a Q10 doubling-every-18°F
 * temperature factor here vs. `fermentRate`'s different curve there, and
 * fat/sugar multipliers here vs. `sweetenerFermentFactor` there. Two
 * independent implementations in the source itself (the standalone
 * calculator vs. this import pipeline), ported as two independent Kotlin
 * implementations too.
 */
object ImportAnalyzer {

    // MARK: - round1() — matches every other Core file's local helper

    private fun round1(n: Double): Double = (n * 10).swiftRounded() / 10

    // MARK: - Tier classifiers — lines 27-82

    fun hydrationTier(h: Double): ImportTierResult = when {
        h < 60 -> ImportTierResult("stiff", "${oneDecimal(h)}% hydration — stiff dough. Dense crumb, easy to shape, long gluten development needed.")
        h < 70 -> ImportTierResult("moderate", "${oneDecimal(h)}% hydration — moderate. Workable dough, good structure, versatile crumb.")
        h < 78 -> ImportTierResult("high", "${oneDecimal(h)}% hydration — high. Open crumb, requires strong gluten development and confident shaping.")
        else -> ImportTierResult("very_high", "${oneDecimal(h)}% hydration — very high. Slack dough typical of ciabatta. Extensive stretch & fold essential.")
    }

    fun fatTier(f: Double): ImportTierResult = when {
        f < 5 -> ImportTierResult("lean", "Lean dough — little to no fat. Clean flavor, crisp crust, aggressive fermentation.")
        f < 15 -> ImportTierResult("lightly_enriched", "${oneDecimal(f)}% fat — lightly enriched. Tender crumb, slightly extended fermentation.")
        f < 30 -> ImportTierResult("moderately_enriched", "${oneDecimal(f)}% fat — moderately enriched. Soft, pillowy texture. Fermentation meaningfully slowed.")
        else -> ImportTierResult("highly_enriched", "${oneDecimal(f)}% fat — highly enriched (brioche territory). Rich, soft crumb. Fermentation significantly extended.")
    }

    fun sugarTier(s: Double): ImportTierResult = when {
        s < 3 -> ImportTierResult("low", "Minimal sugar — fermentation driven mainly by starch-derived sugars from flour.")
        s < 8 -> ImportTierResult("moderate", "${oneDecimal(s)}% sugar — moderate sweetness. Slight Maillard acceleration, minor fermentation effect.")
        s < 15 -> ImportTierResult("high", "${oneDecimal(s)}% sugar — high. Sweet loaf. Osmotic pressure on yeast slows fermentation; increase proof time.")
        else -> ImportTierResult("very_high", "${oneDecimal(s)}% sugar — very high. Osmotic stress is significant. Use osmotolerant yeast if possible.")
    }

    /** JS's `n.toFixed(1)` — always one decimal place, no thousands separator, used only inside the tier description strings above. */
    private fun oneDecimal(n: Double): String = String.format("%.1f", n)

    // MARK: - Moisture-fraction / yeast-conversion tables — lines 7-23

    private fun moistureFraction(source: DairySource): Double = when (source) {
        DairySource.WHOLE_MILK -> 0.87
        DairySource.BUTTERMILK -> 0.88
        DairySource.HEAVY_CREAM -> 0.57
    }

    private fun moistureFraction(source: SugarSource): Double = when (source) {
        SugarSource.HONEY -> 0.17
        SugarSource.MOLASSES -> 0.25
        SugarSource.BARLEY_MALT -> 0.25
        SugarSource.GRANULATED_SUGAR, SugarSource.BROWN_SUGAR, SugarSource.POWDERED_SUGAR -> 0.0
    }

    private const val EGG_WHOLE_MOISTURE_FRACTION = 0.75
    private const val EGG_YOLK_MOISTURE_FRACTION = 0.49

    private fun yeastConversionFactor(type: YeastType): Double = when (type) {
        YeastType.INSTANT -> 1.0
        YeastType.ACTIVE_DRY -> 1.25
        YeastType.FRESH -> 3.0
    }

    // MARK: - calcImportProofTime() — lines 113-222

    fun calcProofTime(
        yeastPercent: Double,
        yeastType: YeastType,
        ambientTempF: Double,
        fermentationType: ImportFermentationType,
        coldRetardHours: Double?,
        fermentationMultiplier: Double,
        inferredStyle: String,
        saltPercent: Double,
        preFerment: ImportPreFerment,
        temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    ): ImportProofTimeMath {
        // A native-only crash guard, not present in the source — JS
        // numbers have no fixed-width trap, so `Number(env.ambientTempF)
        // || 75` (the source's own parsing) can never crash no matter how
        // extreme the typed value is. Kotlin's `Int` conversion below
        // could misbehave on NaN/±infinity/out-of-range, and
        // `ambientTempF` arrives here from an unclamped free-text field
        // with no equivalent failure mode to preserve. Clamped to a
        // generous physical range rather than changing behavior for any
        // input a real user would actually type; shadows the parameter so
        // every use below (the Q10 factor and both "~X°F" stage
        // descriptions) is automatically covered.
        val ambientTempF = if (ambientTempF.isFinite()) ambientTempF.coerceIn(-1000.0, 1000.0) else 75.0
        // Q10 temperature correction — yeast rate doubles every ~18°F above 75°F baseline.
        val tempFactor = 2.0.pow((ambientTempF - 75) / 18)

        val yeastConvFactor = yeastConversionFactor(yeastType)
        val instantEquivPct = yeastPercent / yeastConvFactor
        val yeastFactor = minOf(instantEquivPct / 1.0, 2.0)
        val yeastTypeFactor = 1.0

        val preFermentFactor = when (preFerment) {
            ImportPreFerment.POOLISH -> 1.20
            ImportPreFerment.BIGA -> 1.15
            ImportPreFerment.NONE -> 1.0
        }

        val saltFactor = when {
            saltPercent < 1.5 -> 0.9
            saltPercent > 2.5 -> 1.15
            else -> 1.0
        }

        val baseBulk = 90.0
        val rawBulk = (baseBulk * fermentationMultiplier * saltFactor) / (tempFactor * yeastFactor * yeastTypeFactor * preFermentFactor)
        // `rawBulk` can only be non-finite if some upstream factor is
        // exactly 0 or non-finite despite the guard above (e.g. a future
        // change reintroducing an unbounded factor) — defensive belt-and-
        // suspenders, same crash class as the `ambientTempF` guard.
        val bulkFermentMinutes = if (rawBulk.isFinite()) rawBulk.coerceIn(45.0, 600.0).swiftRounded().toInt() else 90

        val finalRatio = when (inferredStyle) {
            "brioche" -> 0.45
            "soft_roll" -> 0.55
            else -> 0.65
        }
        val finalProofMinutes = (bulkFermentMinutes.toDouble() * finalRatio).coerceIn(30.0, 180.0).swiftRounded().toInt()

        val stages = mutableListOf<ImportProofStage>()

        if (preFerment == ImportPreFerment.BIGA) {
            stages.add(
                ImportProofStage(
                    name = "Prepare Biga", durationMinutes = 720,
                    description = "Mix biga (flour + water + pinch of yeast) 12–16 hours ahead at room temperature until dough doubles and shows small bubbles. Refrigerate if warmer than 72°F.",
                ),
            )
        } else if (preFerment == ImportPreFerment.POOLISH) {
            stages.add(
                ImportProofStage(
                    name = "Prepare Poolish", durationMinutes = 480,
                    description = "Mix poolish (equal parts flour and water by weight + pinch of yeast) 8–12 hours ahead until bubbly, domed, and just beginning to recede. Use at peak activity.",
                ),
            )
        }

        if ((inferredStyle == "artisan" || inferredStyle == "ciabatta") && preFerment == ImportPreFerment.NONE) {
            stages.add(
                ImportProofStage(
                    name = "Autolyse", durationMinutes = 30,
                    description = "Combine flour and water only. Rest covered 30 min to hydrate gluten passively before adding salt, yeast, and other ingredients.",
                ),
            )
        }

        stages.add(
            ImportProofStage(
                name = "Bulk Fermentation", durationMinutes = bulkFermentMinutes,
                description = "Bulk ferment at ~${TemperatureFormatting.display(ambientTempF, temperatureUnit)} until dough has grown ~75% and feels light and airy. Enriched doughs rise more slowly — look for feel over volume.",
            ),
        )

        val sfSets = if (inferredStyle == "ciabatta") 4 else if (inferredStyle == "artisan") 3 else 0
        if (sfSets > 0) {
            stages.add(
                ImportProofStage(
                    name = "Stretch & Fold", durationMinutes = sfSets * 30,
                    description = "Perform $sfSets sets of stretch & folds every 30 min during the first ${sfSets * 30} min of bulk fermentation. Use wet hands to prevent sticking.",
                ),
            )
        }

        if (inferredStyle == "soft_roll" || inferredStyle == "brioche") {
            stages.add(
                ImportProofStage(
                    name = "Degas & Portion", durationMinutes = 10,
                    description = "Gently degas the dough. Portion and pre-shape into rolls or loaf. Cover and rest 10–15 min.",
                ),
            )
        }

        if (inferredStyle == "artisan" || inferredStyle == "ciabatta") {
            stages.add(
                ImportProofStage(
                    name = "Pre-shape & Bench Rest", durationMinutes = 20,
                    description = "Pre-shape loosely on an unfloured surface. Cover and rest 20 min to relax gluten before final shaping.",
                ),
            )
        }

        stages.add(
            ImportProofStage(
                name = "Final Proof", durationMinutes = finalProofMinutes,
                description = "Final proof at ~${TemperatureFormatting.display(ambientTempF, temperatureUnit)}. Dough should spring back slowly (3–5 sec) when poked — not snap back (under-proofed) or collapse (over-proofed).",
            ),
        )

        if (fermentationType == ImportFermentationType.COLD && coldRetardHours != null && coldRetardHours > 0) {
            // `coldRetardHours > 0` above already excludes NaN (NaN
            // comparisons are always false), but not +infinity or an
            // extreme finite value from an unclamped free-text field —
            // same native-only crash guard as `ambientTempF`'s above.
            val clampedColdRetardHours = minOf(coldRetardHours, 1000.0)
            stages.add(
                ImportProofStage(
                    name = "Cold Retard", durationMinutes = (clampedColdRetardHours * 60).swiftRounded().toInt(),
                    description = "Refrigerate at 38°F for ${formatHours(clampedColdRetardHours)} hours. Score and bake directly from the fridge — no temper needed.",
                ),
            )
        }

        val totalMinutes = stages.sumOf { it.durationMinutes }
        return ImportProofTimeMath(stages, bulkFermentMinutes, finalProofMinutes, totalMinutes)
    }

    /**
     * JS's template-literal interpolation of a `number` just calls
     * `String(coldRetardHours)` — no fixed decimal formatting. Whole
     * values print without a decimal point (`"16"`, not `"16.0"`); only a
     * genuinely fractional value would show one. The `abs(n) < 1e15`
     * guard is a native-only crash fix, not present in the source — JS's
     * `String(n)` never traps regardless of magnitude, but Kotlin's
     * `Long` conversion could for an extreme value. Falls back to
     * `n.toString()` (never-crashing) for magnitudes no real cold-retard
     * duration would ever reach.
     */
    private fun formatHours(n: Double): String = if (n % 1.0 == 0.0 && abs(n) < 1e15) n.toLong().toString() else n.toString()

    // MARK: - buildActiveSteps() — lines 226-235

    fun buildActiveSteps(style: String): List<ImportActiveStep> = listOf(
        ImportActiveStep(
            step = "Preheat",
            instruction = if (style == "ciabatta") "Preheat oven to 450°F with a Dutch oven or steam-injected setup inside."
            else if (style == "brioche") "Preheat oven to 350°F. Use an egg wash for a deep golden crust."
            else if (style == "soft_roll") "Preheat oven to 375°F. Brush rolls with egg wash before baking."
            else "Preheat oven to 450°F with a Dutch oven or baking stone inside.",
        ),
        ImportActiveStep(
            step = "Score",
            instruction = if (style == "soft_roll" || style == "brioche") "Skip scoring — snip tops of rolls with scissors if desired."
            else if (style == "ciabatta") "No scoring needed — dimple gently with wet fingers before loading."
            else "Score dough with a sharp lame at a 30–45° angle just before loading.",
        ),
        ImportActiveStep(
            step = "Bake",
            instruction = if (style == "brioche") "Bake 30–35 min until deep golden brown. Internal temp should reach 190°F."
            else if (style == "soft_roll") "Bake 18–22 min until golden. Internal temp 200°F."
            else if (style == "ciabatta") "Bake covered 20 min, then uncovered 20–25 min until deep golden and hollow-sounding when tapped."
            else "Bake covered 20 min (steam phase), then remove lid and bake 20–25 min more until deep golden.",
        ),
        ImportActiveStep(
            step = "Cool",
            instruction = if (style == "soft_roll") "Cool on a rack at least 10 min before serving."
            else "Cool on a rack at least 1 hour before slicing. Cutting too soon compresses the crumb.",
        ),
    )

    // MARK: - POST /analyze route handler — lines 418-623

    fun analyze(
        ingredients: List<ImportIngredientInput>,
        recipeName: String?,
        environment: ImportEnvironmentInput,
        temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    ): ImportAnalysisOutcome {
        fun byCategory(cat: IngredientCategory): Double = ingredients.filter { it.category == cat }.sumOf { it.grams }

        val flourGrams = byCategory(IngredientCategory.FLOUR)
        if (flourGrams <= 0) return ImportAnalysisOutcome.Failure(ImportAnalysisError.NoFlour)

        val waterGrams = byCategory(IngredientCategory.WATER)
        val saltGrams = byCategory(IngredientCategory.SALT)
        val yeastGrams = byCategory(IngredientCategory.YEAST)
        val fatGrams = byCategory(IngredientCategory.FAT)
        val sugarGrams = byCategory(IngredientCategory.SUGAR)
        val eggWholeGrams = byCategory(IngredientCategory.EGG)
        val eggYolkGrams = byCategory(IngredientCategory.EGG_YOLK)
        val eggGrams = eggWholeGrams + eggYolkGrams
        val dairyGrams = byCategory(IngredientCategory.DAIRY)

        val totalDoughWeight = flourGrams + waterGrams + saltGrams + yeastGrams + fatGrams + sugarGrams + eggGrams + dairyGrams

        fun bp(g: Double): Double = round1((g / flourGrams) * 100)

        val bakerPercentages = ImportBakerPercentages(
            water = bp(waterGrams), salt = bp(saltGrams), fat = bp(fatGrams), yeast = bp(yeastGrams),
            sugar = if (sugarGrams > 0) bp(sugarGrams) else null,
            eggs = if (eggGrams > 0) bp(eggGrams) else null,
            dairy = if (dairyGrams > 0) bp(dairyGrams) else null,
        )
        val ingredientWeights = ImportIngredientWeights(
            flour = round1(flourGrams), water = round1(waterGrams), salt = round1(saltGrams),
            fat = round1(fatGrams), yeast = round1(yeastGrams),
            sugar = if (sugarGrams > 0) round1(sugarGrams) else null,
            eggs = if (eggGrams > 0) round1(eggGrams) else null,
            dairy = if (dairyGrams > 0) round1(dairyGrams) else null,
        )

        val detectedYeastType = ingredients.firstOrNull { it.category == IngredientCategory.YEAST }?.yeastType ?: YeastType.INSTANT

        val dairyMoistureFraction = ingredients.firstOrNull { it.category == IngredientCategory.DAIRY }
            ?.let { moistureFraction(it.dairySource ?: DairySource.WHOLE_MILK) } ?: 0.87
        val sugarMoisture = ingredients.firstOrNull { it.category == IngredientCategory.SUGAR }
            ?.let { moistureFraction(it.sugarSource ?: SugarSource.GRANULATED_SUGAR) } ?: 0.0

        val totalMoistureGrams = waterGrams +
            dairyGrams * dairyMoistureFraction +
            eggWholeGrams * EGG_WHOLE_MOISTURE_FRACTION +
            eggYolkGrams * EGG_YOLK_MOISTURE_FRACTION +
            sugarGrams * sugarMoisture

        val effectiveHydrationPercent = round1((totalMoistureGrams / flourGrams) * 100)

        val butterGrams = ingredients
            .filter { it.category == IngredientCategory.FAT && (it.fatSource == FatSource.BUTTER || (it.fatSource == null && it.name.lowercase().contains("butter"))) }
            .sumOf { it.grams }
        val butterPercent = (butterGrams / flourGrams) * 100
        val creamFatGrams = dairyGrams * 0.36
        val eggFatGrams = eggWholeGrams * 0.12 + eggYolkGrams * 0.26
        val totalFatGrams = fatGrams + creamFatGrams + eggFatGrams
        val totalFatPercent = round1((totalFatGrams / flourGrams) * 100)

        val yeastPercent = round1((yeastGrams / flourGrams) * 100)
        val saltPercent = round1((saltGrams / flourGrams) * 100)
        val sugarPercent = round1((sugarGrams / flourGrams) * 100)

        var inferredStyle = "artisan"
        if (butterPercent >= 30) {
            inferredStyle = "brioche"
        } else if (totalFatPercent >= 5 && sugarPercent >= 2 && (eggGrams > 0 || dairyGrams > 0)) {
            inferredStyle = "soft_roll"
        } else if (effectiveHydrationPercent >= 78) {
            inferredStyle = "ciabatta"
        }

        val fatMult = when {
            totalFatPercent >= 30 -> 1.30
            totalFatPercent >= 15 -> 1.15
            totalFatPercent >= 5 -> 1.05
            else -> 1.0
        }
        val sugarMult = when {
            sugarPercent >= 15 -> 1.35
            sugarPercent >= 8 -> 1.20
            sugarPercent >= 3 -> 1.05
            else -> 1.0
        }

        val fermentationMultiplier = round1(minOf(fatMult * sugarMult, 2.0))

        val hydrTier = hydrationTier(effectiveHydrationPercent)
        val fTier = fatTier(totalFatPercent)
        val sTier = sugarTier(sugarPercent)

        val proof = calcProofTime(
            yeastPercent = yeastPercent, yeastType = detectedYeastType, ambientTempF = environment.ambientTempF,
            fermentationType = environment.fermentationType, coldRetardHours = environment.coldRetardHours,
            fermentationMultiplier = fermentationMultiplier, inferredStyle = inferredStyle,
            saltPercent = saltPercent, preFerment = environment.preFerment, temperatureUnit = temperatureUnit,
        )

        val activeSteps = buildActiveSteps(inferredStyle)

        val flags = mutableListOf<String>()
        val advisories = mutableListOf<String>()

        for (i in ingredients) {
            if (i.flagged != null) flags.add("${i.name}: ${i.flagged}")
        }
        if (saltPercent == 0.0) flags.add("No salt detected — this is unusual. Verify your ingredient list.")
        if (yeastPercent == 0.0) flags.add("No yeast detected — if this is a sourdough recipe, adjust the timeline manually.")
        if (waterGrams == 0.0 && dairyGrams == 0.0 && eggGrams == 0.0) {
            flags.add("No liquid detected — check ingredient conversions.")
        }

        if (yeastPercent > 2.5) advisories.add("Yeast at ${jsNumber(yeastPercent)}% is high — proof times will be very short. Watch carefully.")
        if (environment.waterTempF > 100) advisories.add("Water temperature above 100°F can begin to damage yeast. Consider cooling to 80–90°F.")
        if (environment.waterTempF < 65) advisories.add("Cool water (below 65°F) will extend bulk fermentation. Add 15–30% to estimated times.")
        if (totalFatPercent > 15 && yeastPercent < 0.8) {
            advisories.add("Enriched dough with low yeast — proof times may exceed estimates. Use the poke test.")
        }

        return ImportAnalysisOutcome.Success(
            ImportAnalysisResult(
                recipeName = recipeName,
                totalFlourGrams = round1(flourGrams),
                bakerPercentages = bakerPercentages,
                ingredientWeights = ingredientWeights,
                totalDoughWeight = round1(totalDoughWeight),
                effectiveHydrationPercent = effectiveHydrationPercent,
                totalFatPercent = totalFatPercent,
                sugarPercent = sugarPercent,
                yeastPercent = yeastPercent,
                yeastType = detectedYeastType,
                inferredStyle = inferredStyle,
                hydrationTier = hydrTier,
                fatTier = fTier,
                sugarTier = sTier,
                fermentationMultiplier = fermentationMultiplier,
                flags = flags,
                advisories = advisories,
                stages = proof.stages,
                bulkFermentMinutes = proof.bulkFermentMinutes,
                finalProofMinutes = proof.finalProofMinutes,
                totalMinutes = proof.totalMinutes,
                activeSteps = activeSteps,
            ),
        )
    }

    /**
     * JS's bare `${yeastPercent}%` template interpolation — a whole value
     * prints with no decimal point, matching `ProofStageNarrator.jsNumber`'s
     * already-established JS-number-to-string handling elsewhere in this
     * codebase (reimplemented locally rather than reaching into that
     * unrelated file for a one-line utility, same call already made in
     * `IngredientLineParser.round3AndFormat`). Same native-only crash
     * guard as [formatHours] above — `yeastPercent` here can go
     * arbitrarily high if an imported ingredient's gram quantity is
     * extreme relative to flour, with no equivalent trap in the source's
     * `${n}%` string interpolation.
     */
    private fun jsNumber(n: Double): String = if (n % 1.0 == 0.0 && abs(n) < 1e15) n.toLong().toString() else n.toString()
}
