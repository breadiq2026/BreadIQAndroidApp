package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BreadStyleDef
import com.BreadIQ.myapp.model.FlourBlendEntry
import com.BreadIQ.myapp.model.FormulaResult
import com.BreadIQ.myapp.model.ProofStage
import com.BreadIQ.myapp.model.ProofTimeResult
import com.BreadIQ.myapp.model.TemperatureUnit

/**
 * Input for [BakeStepAssembler.assemble] — everything the iOS source's
 * `handleStartBake` (`app/(tabs)/calculator.tsx`, ~lines 1457-1753) reads
 * to turn a calculated formula/proof result into the actual
 * `List<RawBakeStep>` a [com.BreadIQ.myapp.model.BakeSession] starts with.
 */
data class BakeStepAssemblyInput(
    val formulaResult: FormulaResult,
    val proofResult: ProofTimeResult,
    val style: BreadStyleDef,
    val flourBlend: List<FlourBlendEntry>,
    val yeastType: String,
    val sweetenerType: String?,
    val usePrefermant: Boolean,
    val prefermentType: String,
    val prefermentFlourPercent: Double,
    /** "water" | "milk" — only read to compute `suppressWater`. */
    val liquidType: String,
    /** "baked_baking_soda" | "lye". */
    val pretzelBathType: String,
    val selectedShapeValue: String,
    /**
     * Display-only, per [TemperatureFormatting]'s own contract — used
     * solely for the recipe-card oven/internal temp range text below.
     */
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
)

/**
 * Ported from the iOS app's `Core/BakeStepAssembler.swift` — port of
 * `handleStartBake`'s step-assembly logic: genuinely novel client-only
 * orchestration, not covered by [ProofStageNarrator] (which only
 * produces `calcProofTime()`'s own stage array, one input to this). This
 * is what actually builds the recipe-card text, ingredient-line
 * breakdowns, preferment-vs-straight-dough step splitting, and per-style
 * boil/bath/mixing special-casing that a user sees once a bake starts.
 *
 * **Two label tables kept deliberately separate from the ones already
 * built for other items, not reused** (matches the iOS source's own
 * documented reasoning):
 * - [yeastLabels] here ("Instant Yeast"/"Active Dry Yeast"/"Fresh
 *   Yeast") is a genuine drift from `FormulaResultView`'s own yeast
 *   labels ("Instant"/"Active Dry"/"Fresh/Cake", combined with a
 *   separate " Yeast" suffix at its own call sites). Two different
 *   source components disagreeing on this string, not a porting mistake
 *   to silently unify.
 * - [flourTypeLabels] here (8 entries, includes `all_purpose`) is the
 *   calculator screen's own client-side table — distinct from
 *   [FormulaCalculator.flourLabels] (7 entries, no `all_purpose`),
 *   which ports the server's own table and has its own confirmed
 *   pre-existing gap (see that val's own doc comment). Sweetener labels,
 *   by contrast, ARE safely reused from [FormulaCalculator.sweetenerMeta]
 *   — the iOS port verified its 4 entries match this screen's own table
 *   exactly, no drift.
 *
 * **A source inconsistency preserved exactly, not "fixed"**: the final-
 * dough ingredient lines read the TOP-LEVEL `formulaResult.saltWeight`
 * for salt, not `finalMix.saltWeight` — even though [FormulaResult.preferment]'s
 * `finalMix` has its own `saltWeight` field. All salt lands in the final
 * dough in practice (no preferment in this app ever carries salt except
 * the old-dough case, which [FormulaResult] doesn't model as a
 * preferment type at all), so the two values are equal whenever this
 * runs — but the source's own choice of which field to read is kept
 * exactly as written rather than "corrected" to the arguably-more-
 * consistent `finalMix.saltWeight`.
 *
 * **The multi-flour-without-a-breakdown ingredient-line branch is
 * unreachable given this app's actual [FormulaCalculator]**: it only
 * omits `flourBreakdown` when there's exactly one flour, so "multiple
 * flours AND no breakdown" never actually happens here. Ported anyway
 * for source fidelity — cheap to keep once the single-flour fallback
 * path already needs the same label table.
 */
object BakeStepAssembler {

    private val yeastLabels: Map<String, String> = mapOf(
        "instant" to "Instant Yeast",
        "active_dry" to "Active Dry Yeast",
        "fresh" to "Fresh Yeast",
    )

    private val flourTypeLabels: Map<String, String> = mapOf(
        "bread" to "Bread Flour",
        "all_purpose" to "All-Purpose Flour",
        "00" to "00 Flour",
        "semolina" to "Semolina (Fine Durum)",
        "whole_wheat" to "Whole Wheat",
        "rye" to "Dark Rye",
        "spelt" to "Spelt",
        "einkorn" to "Einkorn",
    )

    private val bakeMinutes: Map<String, Int> = mapOf(
        "baguette" to 22, "country" to 45, "artisan" to 45, "ciabatta" to 28, "focaccia" to 25,
        "pizza_ny" to 12, "pizza_neo" to 10, "soft_roll" to 22, "brioche" to 32,
        "bagel" to 18, "english_muffin" to 14, "pretzel" to 13,
    )

    /** Baker-paced (`manualStart`), not timed — matches the source's `SHAPING_STAGE_LABELS` set exactly. */
    private val shapingStageLabels: Set<String> = setOf(
        "Pre-shape", "Bench Rest", "Degas & Bench Rest",
        "Divide & Shape (Bagel)", "Rope & Shape", "Divide & Pre-shape",
        "Score & Load", "Boil & Top", "Place in Rings",
        "Boil", "Alkaline Bath", "Final Shape", "Divide & Roll",
    )

    private const val BAGEL_BOIL_DESCRIPTION = "Boiling solution: 3800g (1 gallon) water · 60g barley malt syrup · 12g baking soda. Bring to a full rolling boil.\n\nLower bagels gently — do not crowd the pot. Boil 30–45 seconds per side. Transfer immediately to a wire rack.\n\nAdd toppings now — sesame, poppy, everything, or plain. The surface dries within seconds of leaving the water. Load the oven now."

    /**
     * `${r1(v)}g`-shaped call sites — [FormulaCalculator.r1] alone isn't
     * enough, since Kotlin's `Double` string conversion always shows a
     * trailing `.0` for whole numbers where a JS template literal
     * wouldn't. Reuses [ProofStageNarrator.jsNumber], built for this
     * exact mismatch, rather than duplicating it.
     */
    private fun fmt(v: Double): String = ProofStageNarrator.jsNumber(FormulaCalculator.r1(v))

    /**
     * `parseMixMinutes()` — a range like "8–10 min" averages to 9, "Up
     * to 10 min" reads the single number, otherwise the first number
     * found, defaulting to 10 if none.
     */
    fun parseMixMinutes(text: String): Int {
        Regex("""(\d+)\s*[–-]\s*(\d+)""").find(text)?.let { m ->
            val low = m.groupValues[1].toIntOrNull()
            val high = m.groupValues[2].toIntOrNull()
            if (low != null && high != null) return ((low + high) / 2.0).swiftRounded().toInt()
        }
        Regex("""up to\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            m.groupValues[1].toIntOrNull()?.let { return it }
        }
        Regex("""(\d+)""").find(text)?.let { m ->
            m.groupValues[1].toIntOrNull()?.let { return it }
        }
        return 10
    }

    /**
     * Real, tier-driven Autolyse step — replaces the old generic
     * fixed-30-min autolyse step that only ever appeared via the (not
     * ported) import path on iOS. Timed rest (not manual-start): the
     * baker just waits it out before mixing begins.
     */
    private fun autolyseStep(autolyse: AutolyseGuidance): RawBakeStep = RawBakeStep(
        label = "Autolyse",
        description = "Combine the flour blend and water only — no salt, yeast, or preferment yet.\n\n" +
            "Hydration range for this blend: ${autolyse.hydrationRangeLabel}.\n\n" +
            "Mix just until no dry flour remains, cover, and rest at room temperature.\n\n" +
            autolyse.explainer,
        durationMinutes = autolyse.autolyseDurationMinutes,
    )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun assemble(input: BakeStepAssemblyInput): List<RawBakeStep> {
        val formulaResult = input.formulaResult
        val proofResult = input.proofResult
        val style = input.style
        val yLabel = yeastLabels[input.yeastType] ?: "Instant Yeast"

        // Autolyse guidance for high whole-wheat/rye blends — BreadIQ-
        // native, see AutolyseCalculator's doc comment. STANDARD tier
        // changes nothing below (existing behavior, untouched).
        val autolyse = AutolyseCalculator.calculate(input.flourBlend)
        val autolyseApplies = autolyse.tier != AutolyseTier.STANDARD
        val suppressWater = (style.value == "brioche" || style.value == "soft_roll") && input.liquidType == "milk"
        val isBrioche = style.value == "brioche"
        val isPretzel = style.value == "pretzel"
        val isPizzaStyle = style.value == "pizza_ny" || style.value == "pizza_neo"
        val isGriddle = style.value == "english_muffin"

        // MARK: - All-ingredient lines (straight-dough scale step / recipe card fallback)

        val allIngLines = mutableListOf<String>()
        val breakdown = formulaResult.flourBreakdown
        if (!breakdown.isNullOrEmpty()) {
            for (f in breakdown) {
                allIngLines.add(if (breakdown.size > 1) "${f.label} (${ProofStageNarrator.jsNumber(f.percent)}%): ${fmt(f.grams)}g" else "${f.label}: ${fmt(f.grams)}g")
            }
        } else if (input.flourBlend.size == 1) {
            val fl = flourTypeLabels[input.flourBlend[0].type] ?: input.flourBlend[0].type
            allIngLines.add("$fl: ${fmt(formulaResult.flourWeight)}g")
        } else {
            for (f in input.flourBlend) {
                val fl = flourTypeLabels[f.type] ?: f.type
                allIngLines.add("$fl (${ProofStageNarrator.jsNumber(f.percent)}%): ${fmt(formulaResult.flourWeight * f.percent / 100)}g")
            }
        }
        if (!suppressWater && formulaResult.waterWeight > 0) allIngLines.add("Water: ${fmt(formulaResult.waterWeight)}g")
        if (formulaResult.saltWeight > 0) allIngLines.add("Salt: ${fmt(formulaResult.saltWeight)}g")
        if (formulaResult.yeastWeight > 0) allIngLines.add("$yLabel: ${fmt(formulaResult.yeastWeight)}g")
        if (formulaResult.fatWeight > 0) allIngLines.add("Fat / Oil: ${fmt(formulaResult.fatWeight)}g")
        formulaResult.eggWeight?.let { if (it != 0.0) allIngLines.add("Eggs: ${fmt(it)}g") }
        formulaResult.milkWeight?.let { if (it != 0.0) allIngLines.add("${formulaResult.dairyDisplayName ?: "Milk"}: ${fmt(it)}g") }
        formulaResult.butterWeight?.let { if (it != 0.0) allIngLines.add("Butter: ${fmt(it)}g") }
        formulaResult.sweetenerWeight?.let { sw ->
            if (sw != 0.0 && input.sweetenerType != null) {
                val label = FormulaCalculator.sweetenerMeta[input.sweetenerType]?.label ?: input.sweetenerType
                allIngLines.add("$label: ${fmt(sw)}g")
            }
        }
        formulaResult.maltWeight?.let { if (it != 0.0) allIngLines.add("Diastatic Malt: ${fmt(it)}g") }

        // MARK: - Preferment / final-dough breakdown

        val prefTypeName = if (input.prefermentType == "biga") "Biga" else if (input.prefermentType == "poolish") "Poolish" else "Pre-ferment"
        val prefIngLines = mutableListOf<String>()
        val finalDoughIngLines = mutableListOf<String>()
        val pref = if (input.usePrefermant) formulaResult.preferment else null

        if (pref != null) {
            if (!breakdown.isNullOrEmpty()) {
                for (f in breakdown) {
                    val g = f.prefermentGrams ?: FormulaCalculator.r1(f.grams * input.prefermentFlourPercent / 100)
                    if (g > 0) {
                        prefIngLines.add(if (breakdown.size > 1) "${f.label} (${ProofStageNarrator.jsNumber(f.percent)}%): ${fmt(g)}g" else "${f.label}: ${fmt(g)}g")
                    }
                }
            } else {
                val fl = input.flourBlend.firstOrNull()?.let { flourTypeLabels[it.type] ?: it.type } ?: "Flour"
                prefIngLines.add("$fl: ${fmt(pref.flourWeight)}g")
            }
            prefIngLines.add("Water: ${fmt(pref.waterWeight)}g")
            if (pref.yeastWeight > 0) prefIngLines.add("Yeast (tiny amount): ${fmt(pref.yeastWeight)}g")

            val fm = pref.finalMix
            if (!breakdown.isNullOrEmpty()) {
                for (f in breakdown) {
                    if (f.finalDoughGrams > 0) {
                        finalDoughIngLines.add(if (breakdown.size > 1) "${f.label} (${ProofStageNarrator.jsNumber(f.percent)}%): ${fmt(f.finalDoughGrams)}g" else "${f.label}: ${fmt(f.finalDoughGrams)}g")
                    }
                }
            } else {
                val fl = input.flourBlend.firstOrNull()?.let { flourTypeLabels[it.type] ?: it.type } ?: "Flour"
                finalDoughIngLines.add("$fl: ${fmt(fm.flourWeight)}g")
            }
            if (!suppressWater && fm.waterWeight > 0) finalDoughIngLines.add("Water: ${fmt(fm.waterWeight)}g")
            // Reads the top-level formulaResult.saltWeight, not
            // fm.saltWeight — see this object's own doc comment.
            if (formulaResult.saltWeight > 0) finalDoughIngLines.add("Salt: ${fmt(formulaResult.saltWeight)}g")
            if (fm.yeastWeight > 0) finalDoughIngLines.add("$yLabel: ${fmt(fm.yeastWeight)}g")
            if (fm.fatWeight > 0) finalDoughIngLines.add("Fat / Oil: ${fmt(fm.fatWeight)}g")
            formulaResult.eggWeight?.let { if (it != 0.0) finalDoughIngLines.add("Eggs: ${fmt(it)}g") }
            formulaResult.milkWeight?.let { if (it != 0.0) finalDoughIngLines.add("${formulaResult.dairyDisplayName ?: "Milk"}: ${fmt(it)}g") }
            formulaResult.butterWeight?.let { if (it != 0.0) finalDoughIngLines.add("Butter: ${fmt(it)}g") }
            formulaResult.sweetenerWeight?.let { sw ->
                if (sw != 0.0 && input.sweetenerType != null) {
                    val label = FormulaCalculator.sweetenerMeta[input.sweetenerType]?.label ?: input.sweetenerType
                    finalDoughIngLines.add("$label: ${fmt(sw)}g")
                }
            }
            formulaResult.maltWeight?.let { if (it != 0.0) finalDoughIngLines.add("Diastatic Malt: ${fmt(it)}g") }
            finalDoughIngLines.add("$prefTypeName (add last): ${fmt(fm.prefermentWeight)}g")
        }

        // MARK: - Recipe Card description

        var recipeCardDesc: String = if (pref != null && prefIngLines.isNotEmpty()) {
            "${prefTypeName.uppercase()}:\n${prefIngLines.joinToString("\n")}" +
                "\n\nFINAL DOUGH:\n${finalDoughIngLines.joinToString("\n")}" +
                "\n\nReview your complete formula before starting. This is your reference card throughout the bake."
        } else {
            allIngLines.joinToString("\n") + "\n\nReview your complete formula before starting. This is your reference card throughout the bake."
        }

        if (style.value == "bagel") {
            recipeCardDesc += "\n\nBOIL PREP (prepare before baking):\n3800g (1 gallon) water · 60g barley malt syrup · 12g baking soda\n\nBring to a full rolling boil just before the Boil step. Toppings: have sesame, poppy, everything seasoning, or coarse salt ready."
        } else if (style.value == "pretzel") {
            recipeCardDesc += if (input.pretzelBathType == "lye") {
                "\n\nALKALINE BATH PREP:\n1000g cold water · 30–40g food-grade lye\n\n⚠ Always add lye to water — never reverse. Wear rubber gloves and eye protection. Lye neutralizes completely during baking."
            } else {
                "\n\nALKALINE BATH PREP:\n1000g water · 50g baked baking soda · 20g barley malt syrup\n\nBake baking soda on foil-lined sheet at 250°F for 1 hour first — this significantly increases alkalinity. Prepare before shaping."
            }
        }

        // MARK: - Mix / bake step text

        // Autolyse tier overrides the style's own generic mixing copy
        // for non-brioche doughs — brioche's two-phase butter-lamination
        // mix (below) is left untouched; combining high-WW/rye with an
        // enriched brioche dough is an out-of-scope edge case for now.
        val mixMinutes = if (autolyseApplies && !isBrioche) parseMixMinutes(autolyse.kneadTimeLabel) else parseMixMinutes(style.mixingTime)
        val mixDesc = if (autolyseApplies && !isBrioche) {
            "Mixing style: ${autolyse.mixingStyleLabel}\n${autolyse.kneadTimeLabel}\n\nThis blend is ${ProofStageNarrator.jsNumber((autolyse.combinedPercent * 10).swiftRounded() / 10)}% whole wheat/rye combined — mix just until the dough comes together. Full gluten development happens through the autolyse rest and stretch-and-fold sets, not the mixer."
        } else {
            "Speed: ${style.mixingSpeed} · ${style.mixingTime}\n\n${style.mixingNote}"
        }

        val ovenRangeText = TemperatureFormatting.displayRange(style.ovenTempF.low, style.ovenTempF.high, input.temperatureUnit)
        val ovenLine = if (isGriddle) "Griddle temp: $ovenRangeText" else "Oven: $ovenRangeText"
        val hasInternalTemp = style.internalTempF.low > 0
        val internalLine = if (hasInternalTemp) "  ·  Internal: ${TemperatureFormatting.displayRange(style.internalTempF.low, style.internalTempF.high, input.temperatureUnit)}" else ""
        val bakeTimerNote = if (isPizzaStyle) {
            "\n\nBake time varies by heat source — watch the color and char. Pull when the crust is deep golden and the cornicione is charred."
        } else {
            "\n\nTimer is a guide — check color and internal temp at the lower end. Visual and temperature cues override the timer."
        }
        val bakeDesc = "$ovenLine$internalLine\n\n${style.crustNote}$bakeTimerNote"

        val butterGrams = formulaResult.butterWeight ?: 0.0
        val mixingPhase1Desc = if (isBrioche) {
            "Speed: ${style.mixingSpeed} — Gluten development phase.\n\nMix flour, water, eggs, and salt on ${style.mixingSpeed} speed for 8–10 minutes. Dough should pull clean from the bowl sides and become smooth and elastic. Do not add butter yet — gluten must fully develop first.\n\nWhen the timer ends: reduce mixer to medium-low and begin adding butter."
        } else {
            mixDesc
        }
        val mixingPhase2Desc = "Add ${fmt(butterGrams)}g butter — one tablespoon at a time — with the mixer running on medium-low.\n\nWait for each addition to fully incorporate before adding the next. Rushing creates lumps and breaks the emulsion. Continue until all butter is incorporated (approximately 10–15 min).\n\nStop when the dough is smooth, glossy, and pulls cleanly from the bowl. Slightly slack and silky — this is correct."

        val pretzelDip = if (input.selectedShapeValue in setOf("pretzel_sticks_6pk", "pretzel_slider_6pk")) "15–20 sec" else "20–25 sec"
        val pretzelBathDesc = if (input.pretzelBathType == "lye") {
            "Rest shaped pretzels uncovered 15–20 min — gluten relaxes, surface dries slightly for better bath adhesion.\n\nLye bath: Dissolve 30–40g food-grade lye per 1000g cold water (always add lye to water, never reverse). Wear rubber gloves and eye protection. Dip each pretzel $pretzelDip. Transfer to parchment. Score the thick arch once with a sharp blade. Apply coarse pretzel salt while still wet. Lye neutralizes completely during baking."
        } else {
            "Rest shaped pretzels uncovered 15–20 min — gluten relaxes, surface dries slightly for better bath adhesion.\n\nBaked baking soda bath: Spread baking soda on a foil-lined sheet and bake at 250°F for 1 hour before use — this significantly increases alkalinity. Dissolve 50g baked baking soda plus 20g barley malt syrup per 1000g water. Dip each pretzel $pretzelDip. Transfer to parchment. Score the thick arch once with a sharp blade. Apply coarse pretzel salt while still wet."
        }

        fun getStyledDesc(label: String, desc: String): String {
            if (label == "Degas & Bench Rest") {
                return if (isBrioche) {
                    "Fold gently 2–3 times — do NOT punch down. The butter lamination tears easily. Pre-shape loosely and cover."
                } else {
                    "Punch down, fold once, and cover. Pre-shape with moderate tension — the dough should feel smooth and unified."
                }
            }
            if (label == "Final Proof" || label == "Final Proof (post-retard)") {
                // Autolyse tier's own poke-test guidance overrides the
                // per-style dictionary below — bran/hydration dominate
                // spring-back behavior more than style at this point.
                if (autolyseApplies) {
                    return desc + "\n\n" + autolyse.pokeTestGuidance
                }
                val pokeTests: Map<String, String> = mapOf(
                    "baguette" to "\n\nPoke test: press a floured finger ½\" into the surface. Springs back 75–80% in 3–4 seconds with a slight indent remaining. No spring at all = over-proofed.",
                    "artisan" to "\n\nPoke test: press a floured finger ½\" in. Springs back 75–80% in 3–4 seconds with gentle resistance. Full immediate spring = under-proofed.",
                    "country" to "\n\nPoke test: press a floured finger ½\" in. Springs back 75–80% in 3–4 seconds. A faint indent remaining = ready.",
                    "ciabatta" to "\n\nDo not poke — the dough is too delicate. Look for a jiggling, slack mass that wobbles gently when the pan is moved. Touch only to score.",
                    "soft_roll" to "\n\nPoke test: gentle press — springs back fully in 1–2 seconds. Rolls should be touching and domed above the pan edge.",
                    "brioche" to "\n\nPoke test: very gentle press — faint dimple fills back slowly and never fully. Over-proofed brioche collapses in the oven. Better slightly under than over.",
                    "pretzel" to "\n\nSlightly under-proofed is better for pretzels. The alkaline bath and oven heat complete the rise. Over-proofing causes blowouts and a weak pretzel structure.",
                )
                return desc + (pokeTests[style.value] ?: "\n\nPoke test: press a floured finger ½\" in. Springs back slowly with a slight indent remaining — this is ready.")
            }
            return desc
        }

        fun mapStage(s: ProofStage): RawBakeStep {
            val stageName = if (s.name == "Boil & Top") "Boil" else if (s.name == "Rest & Alkaline Bath") "Alkaline Bath" else s.name
            val stageDesc: String = if (s.name == "Boil & Top") {
                BAGEL_BOIL_DESCRIPTION
            } else if (s.name == "Rest & Alkaline Bath" && isPretzel) {
                pretzelBathDesc
            } else if (stageName == "Bulk Fermentation" && autolyseApplies) {
                val foldLine = autolyse.foldScheduleLabel?.let { "\n\n$it" } ?: ""
                getStyledDesc(stageName, s.description) +
                    "\n\nBulk target for this blend: ${autolyse.bulkVolumeTargetLabel}" +
                    foldLine
            } else {
                getStyledDesc(stageName, s.description)
            }
            val isNoTimerStep = s.name == "Boil & Top" || s.name == "Rest & Alkaline Bath" || s.name == "Score from Cold"
            val isManualStart = shapingStageLabels.contains(s.name) || shapingStageLabels.contains(stageName)
            return RawBakeStep(label = stageName, description = stageDesc, durationMinutes = s.durationMinutes, noTimer = isNoTimerStep, manualStart = isManualStart)
        }

        // MARK: - Assemble

        val prefStageLabels = setOf("Pre-ferment Build")
        val prefStages = proofResult.stages.filter { prefStageLabels.contains(it.name) }
        val mainStages = proofResult.stages.filter { !prefStageLabels.contains(it.name) }

        val steps = mutableListOf(
            RawBakeStep(label = "Recipe Card", description = recipeCardDesc, durationMinutes = 0, noTimer = true),
        )

        if (pref != null && prefIngLines.isNotEmpty()) {
            val prefMixInstructions = if (input.prefermentType == "biga") {
                "Combine flour(s) and water. Add the yeast and mix until just shaggy — no gluten development. Cover tightly with plastic wrap and ferment at room temperature."
            } else {
                "Combine flour, water, and yeast. Whisk or stir 2–3 min until smooth — should look like a thick batter with no dry spots. Cover loosely to allow CO₂ off-gassing."
            }
            steps.add(RawBakeStep(
                label = "Scale & Mix Preferment",
                description = "${prefTypeName.uppercase()} INGREDIENTS:\n${prefIngLines.joinToString("\n")}\n\n$prefMixInstructions",
                durationMinutes = 0, noTimer = true,
            ))

            if (prefStages.isNotEmpty()) {
                for (s in prefStages) {
                    val readinessCue = if (input.prefermentType == "biga") {
                        "\n\nReady when: doubled in size, the surface is domed, and you can see bubbles throughout with a fresh yeasty aroma."
                    } else {
                        "\n\nReady when: doubled in size, the surface is domed and just beginning to recede at the crown, with bubbles visible across the surface and a slightly alcoholic aroma."
                    }
                    val mapped = mapStage(s)
                    steps.add(RawBakeStep(label = mapped.label, description = mapped.description + readinessCue, durationMinutes = mapped.durationMinutes, noTimer = mapped.noTimer, manualStart = mapped.manualStart))
                }
            } else {
                val pfFermentDesc = if (input.prefermentType == "biga") {
                    "Cover tightly and ferment at room temperature 12–16 hours until doubled and domed.\n\nReady when: doubled in size, the surface is domed, and you can see bubbles throughout with a fresh yeasty aroma."
                } else {
                    "Cover loosely and ferment at room temperature 12–16 hours until doubled, small bubbles across the surface, just beginning to recede at the crown.\n\nReady when: doubled in size, the surface is domed and just beginning to recede at the crown, with bubbles visible across the surface and a slightly alcoholic aroma."
                }
                steps.add(RawBakeStep(label = "Ferment Pre-ferment", description = pfFermentDesc, durationMinutes = 14 * 60))
            }

            steps.add(RawBakeStep(
                label = "Scale Final Dough",
                description = "FINAL DOUGH INGREDIENTS:\n${finalDoughIngLines.joinToString("\n")}\n\nZero the scale between each addition. The ${prefTypeName.lowercase()} goes in last — scoop it directly from its container. Weigh to the gram.",
                durationMinutes = 0, noTimer = true,
            ))

            if (isBrioche) {
                steps.add(RawBakeStep(label = "Mix Final Dough", description = mixingPhase1Desc, durationMinutes = 9, manualStart = true))
                steps.add(RawBakeStep(label = "Incorporate Butter", description = mixingPhase2Desc, durationMinutes = 0, noTimer = true))
            } else {
                if (autolyseApplies) steps.add(autolyseStep(autolyse))
                steps.add(RawBakeStep(label = "Mix Final Dough", description = mixDesc, durationMinutes = mixMinutes, manualStart = true))
            }
        } else {
            steps.add(RawBakeStep(
                label = "Scale Ingredients",
                description = allIngLines.joinToString("\n") + "\n\nZero the scale between each addition. Weigh to the gram.",
                durationMinutes = 0, noTimer = true,
            ))
            if (isBrioche) {
                steps.add(RawBakeStep(label = "Mix Dough", description = mixingPhase1Desc, durationMinutes = 9, manualStart = true))
                steps.add(RawBakeStep(label = "Incorporate Butter", description = mixingPhase2Desc, durationMinutes = 0, noTimer = true))
            } else {
                if (autolyseApplies) steps.add(autolyseStep(autolyse))
                steps.add(RawBakeStep(label = "Mix Dough", description = mixDesc, durationMinutes = mixMinutes, manualStart = true))
            }
        }

        for (s in mainStages) steps.add(mapStage(s))

        steps.add(RawBakeStep(
            label = if (isGriddle) "Griddle Cook" else "Bake",
            description = bakeDesc,
            durationMinutes = bakeMinutes[style.value] ?: 25,
            noTimer = isPizzaStyle,
            manualStart = true,
        ))

        return steps
    }
}
