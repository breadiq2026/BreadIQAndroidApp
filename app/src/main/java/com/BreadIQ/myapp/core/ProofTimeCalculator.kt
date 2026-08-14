package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BreadStyleCatalog
import com.BreadIQ.myapp.model.FlourBlendEntry
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Ported from the iOS app's `Core/ProofTimeCalculator.swift`.
 *
 * Input parameters for [ProofTimeCalculator.calculate]. Mirrors
 * `calcProofTime()`'s destructured input type exactly
 * (`api-server/src/routes/calculator.ts`), with one deliberate omission
 * carried over from the iOS port: `flourWeight` appears in the source's
 * TypeScript parameter type but is never destructured or read anywhere
 * in the function body — a dead field, exactly like `targetDoughWeight`
 * was in `FormulaCalculator`. Left out here rather than ported as a no-op.
 *
 * `numLoaves`/`doughWeightPerPiece` ARE real, read fields — but only by
 * the stage-*text* half of `calcProofTime()` (divide/portion prose),
 * never by the numeric bulk/final-proof math this type feeds into (per
 * the iOS port's own verification: their only usages in the source are
 * inside stage-description template strings). Kept on this shared input
 * class anyway since the source's real `calcProofTime()` takes one input
 * object for both halves, and a future stage-narrator port will need
 * this same shape — duplicating a near-identical input type later would
 * risk drift.
 */
data class ProofTimeInput(
    val hydrationPercent: Double,
    val yeastPercent: Double,
    val waterTempF: Double,
    val ambientTempF: Double,
    val fermentationType: String,
    val usePrefermant: Boolean,
    val isSourdough: Boolean,
    val coldRetardHours: Double? = null,
    val coldRetardTempF: Double? = null,
    val finalProofTempF: Double? = null,
    val diastaticMaltPercent: Double? = null,
    val saltPercent: Double? = null,
    val sweetenerType: String? = null,
    val sweetenerPercent: Double? = null,
    val breadStyle: String? = null,
    val loafStyle: String? = null,
    val baguetteSizeModifier: Double? = null,
    val isSpeedRun: Boolean? = null,
    val relativeHumidity: Int? = null,
    val numLoaves: Int? = null,
    val doughWeightPerPiece: Double? = null,
    val flourBlend: List<FlourBlendEntry>? = null,
    val fatPercent: Double? = null,
    /**
     * Total dough mass in grams — drives the mass-tiered bulk-window
     * cooling drift (`THERMAL_FERMENTATION_ENGINE_SPEC.md` §5 on the iOS
     * side). Plumbed straight from `FormulaResult.totalDoughWeight` at
     * the call site, no new computation.
     */
    val totalDoughWeightG: Double,
)

/**
 * The numeric half of `calcProofTime()`'s output — bulk/final proof
 * durations and their supporting cold-retard/recovery figures.
 *
 * `calcProofTime()` is one large function that computes these numbers
 * AND builds a 13-style-branch prose stage array in the same pass,
 * returning bulk/final proof minutes plus `notes`/`stages` prose.
 * `totalMinutes` is literally the sum of stage durations and
 * `notes`/`stages` are pure narration — none of the three can be
 * honestly produced without first building the full stage array, which
 * is a separate, not-yet-ported item (matches the iOS port's own
 * scoping: `ProofStageNarrator` is still a future roadmap item there
 * too). This type holds everything that *is* pure numeric computation.
 * A future narrator port combines this with a freshly-built stage array
 * to assemble the real `ProofTimeResult`.
 *
 * `coldRetardPercent` is an internal local the source only uses later to
 * build stage prose (e.g. "the cold ferment completed X% of the final
 * proof"). Exposed here anyway so a future narrator port can reuse this
 * exact value instead of recomputing it and risking drift.
 *
 * `estimatedIDTF` — Estimated Initial Dough Temperature, per
 * `THERMAL_FERMENTATION_ENGINE_SPEC.md` §4 on the iOS side. Stays
 * internal-only per that spec's own explicit decision: it feeds the
 * bulk-window drift math below and is exposed here for potential future
 * background range-check validation, but must never be surfaced in
 * `ProofTimeResult.notes` or anywhere else baker-facing.
 */
data class ProofTimeMath(
    val bulkFermentMinutes: Int,
    val finalProofMinutes: Int,
    val coldFermentHours: Double? = null,
    val recoveryMinutes: Int? = null,
    val coldRetardPercent: Int,
    val estimatedIDTF: Double,
)

/**
 * Port of `calcProofTime()`'s numeric half (`api-server/src/routes/
 * calculator.ts`), via the iOS port's own line-by-line verification
 * against the source.
 */
object ProofTimeCalculator {

    const val defaultSaltPercent: Double = 2.0

    /**
     * Arrhenius kinetics rate function — `THERMAL_FERMENTATION_ENGINE_SPEC.md`
     * §3 on the iOS side, replacing an old Q10 doubling-every-18°F curve.
     * Baseline: rate at 24°C (75.2°F) is exactly 1.0, within 0.2°F of the
     * old 75°F reference every `baseBulkAt75`/`baseFinalProofAt75`
     * constant below is calibrated against, so this is a safe drop-in
     * replacement — those constants don't need recalibration. Returns 0
     * outside 0–42°C: yeast is dormant at or below freezing and
     * denatured at or above 42°C. Fahrenheit in (every call site already
     * works in Fahrenheit).
     */
    fun fermentRate(tempF: Double): Double {
        val tCelsius = (tempF - 32) * 5 / 9
        if (!(tCelsius > 0.0 && tCelsius < 42.0)) return 0.0
        val tKelvin = tCelsius + 273.15
        return exp(-(55000 / 8.314) * (1 / tKelvin - 1 / 297.15))
    }

    /**
     * Estimated Initial Dough Temperature — `THERMAL_FERMENTATION_ENGINE_SPEC.md`
     * §4. Friction is averaged in alongside water/ambient (not added on
     * top), re-derived from the classical bakery water-temp formula
     * solved for the resulting dough temp instead of the input water temp.
     */
    fun estimatedInitialDoughTempF(waterTempF: Double, ambientTempF: Double, frictionF: Double): Double =
        (waterTempF + 2 * ambientTempF + frictionF) / 3

    /**
     * Per-style friction lookup, falling back to Artisan's calibrated
     * value for any unrecognized style — same fallback convention a
     * future technique-guide lookup port will use (`kneading[style] ?:
     * kneading["artisan"]`).
     */
    private fun frictionFactorF(forStyle: String?): Double =
        BreadStyleCatalog.all.firstOrNull { it.value == forStyle }?.frictionFactorF
            ?: BreadStyleCatalog.all.first { it.value == "artisan" }.frictionFactorF

    /**
     * Mass-tiered closure rate toward ambient — `THERMAL_FERMENTATION_ENGINE_SPEC.md`
     * §5. The spec's table literally overlaps at 750g ("≤750g" small,
     * "750g–2kg" medium); resolved (per the iOS port) by keeping exactly
     * 750g in the small tier, matching the spec's own "≤750g" wording
     * for that row.
     */
    fun closureRatePerHour(totalDoughWeightG: Double): Double = when {
        totalDoughWeightG <= 750 -> 0.28
        totalDoughWeightG <= 2000 -> 0.16
        else -> 0.09
    }

    /**
     * Dough temperature at [elapsedHours] into the bulk window, drifting
     * from [idt] toward [ambientTempF] at [closureRatePerHour] —
     * `THERMAL_FERMENTATION_ENGINE_SPEC.md` §5.
     */
    fun currentTempF(elapsedHours: Double, idt: Double, ambientTempF: Double, closureRatePerHour: Double): Double {
        val fractionClosed = min(1.0, closureRatePerHour * elapsedHours)
        return idt + fractionClosed * (ambientTempF - idt)
    }

    // TODO: calibrate against real bake data. These are provisional
    // placeholder values, deliberately NOT reusing [closureRatePerHour]'s
    // bulk constants (0.28/0.16/0.09) — a refrigerator's forced/still
    // airflow and a covered retard container cool dough at a genuinely
    // different rate than an open bulk tub sitting in ambient kitchen
    // air, even at the same mass tier.
    const val coldRetardClosureRateSmall = 0.35 // ≤750g
    const val coldRetardClosureRateMedium = 0.20 // 750g–2kg
    const val coldRetardClosureRateLarge = 0.12 // >2kg

    /**
     * Mass-tiered closure rate toward fridge temperature — same
     * ≤750g/750g–2kg/>2kg breakpoints as bulk's [closureRatePerHour]
     * (same "keep the boundary weight in the smaller tier" convention),
     * but with cold-retard-specific constants above.
     */
    fun coldRetardClosureRate(totalDoughWeightG: Double): Double = when {
        totalDoughWeightG <= 750 -> coldRetardClosureRateSmall
        totalDoughWeightG <= 2000 -> coldRetardClosureRateMedium
        else -> coldRetardClosureRateLarge
    }

    /**
     * Dough temperature at [elapsedHours] into cold retard, drifting
     * from [startTempF] (wherever the dough actually was when it entered
     * the fridge — NOT assumed to already be at fridge temperature)
     * toward [fridgeTempF] at [closureRate]. Structurally identical to
     * [currentTempF] above, just for the cold-retard call site and
     * drifting toward a fridge target instead of kitchen ambient.
     */
    fun currentColdRetardTempF(startTempF: Double, fridgeTempF: Double, closureRate: Double, elapsedHours: Double): Double {
        val fractionClosed = min(1.0, closureRate * elapsedHours)
        return startTempF + fractionClosed * (fridgeTempF - startTempF)
    }

    /** `saltFactor()`. */
    fun saltFactor(saltPct: Double): Double = when {
        saltPct <= 1.6 -> 0.88
        saltPct <= 2.4 -> 1.0
        saltPct <= 2.9 -> 1.20
        else -> 1.40
    }

    /**
     * `sweetenerFermentFactor()`. Requires BOTH a truthy type string and
     * a positive percent (mirrors the source's guard, which is a truthy
     * check on `pct`, not a null check — `pct: 0` is excluded same as
     * `pct: null`).
     */
    fun sweetenerFermentFactor(type: String?, pct: Double?): Double {
        val truthyType = FormulaCalculator.truthy(type)
        if (truthyType == null || pct == null || pct <= 0) return 1.0
        return when (truthyType) {
            "granulated_sugar" -> if (pct <= 8) max(0.85, 1 - pct * 0.018) else min(1.25, 0.856 + (pct - 8) * 0.05)
            "honey" -> if (pct <= 8) max(0.80, 1 - pct * 0.025) else min(1.15, 0.80 + (pct - 8) * 0.035)
            "barley_malt" -> max(0.72, 1 - pct * 0.040)
            "molasses" -> min(1.20, 1.0 + pct * 0.020)
            else -> 1.0
        }
    }

    /**
     * `humidityProofFactor()`. Distinct from
     * `FormulaCalculator.humidityWaterFactor` — proof-time uses its own
     * breakpoints (compresses time at high RH, extends at low RH), a
     * fully independent table from the water-weight factor despite the
     * near-identical shape. Reuses `FormulaCalculator.interpolateHumidity`,
     * which is generic piecewise-linear interpolation with no
     * formula-specific behavior baked in.
     */
    fun humidityProofFactor(rh: Int?): Double {
        val rhD = rh?.toDouble() ?: return 1.0
        if (rhD >= 65) {
            val table = listOf(
                65.0 to 0.05, 70.0 to 0.10, 75.0 to 0.15, 80.0 to 0.20,
                85.0 to 0.25, 90.0 to 0.30, 95.0 to 0.35, 100.0 to 0.40,
            )
            return 1.0 - FormulaCalculator.interpolateHumidity(rhD, table)
        }
        if (rhD <= 35) {
            val table = listOf(
                5.0 to 0.15, 10.0 to 0.10, 15.0 to 0.10, 20.0 to 0.05,
                25.0 to 0.05, 30.0 to 0.00, 35.0 to 0.00,
            )
            return 1.0 + FormulaCalculator.interpolateHumidity(rhD, table)
        }
        return 1.0
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun calculate(input: ProofTimeInput): ProofTimeMath {
        // Specialty flour percentages — used for fermentation adjustment
        // factors below. `firstOrNull` matches the source's `.find()` —
        // only the first matching blend entry counts.
        val blend = input.flourBlend ?: emptyList()
        val ryePct = blend.firstOrNull { it.type == "rye" }?.percent ?: 0.0
        val wwheatPct = blend.firstOrNull { it.type == "whole_wheat" }?.percent ?: 0.0
        val speltPct = blend.firstOrNull { it.type == "spelt" }?.percent ?: 0.0
        val einkornPct = blend.firstOrNull { it.type == "einkorn" }?.percent ?: 0.0

        // Style booleans — drive bulk/final-proof multipliers below.
        // (isSoftRoll/isBaguette/pretzelDipTime from the source are
        // stage-*text*-only — verified no numeric formula reads them —
        // so they're left for a future stage-narrator port.)
        val isCiabatta = input.breadStyle == "ciabatta"
        val isArtisan = input.breadStyle == "artisan"
        val isPizza = input.breadStyle == "pizza_ny" || input.breadStyle == "pizza_neo"
        val isBrioche = input.breadStyle == "brioche"
        val isBagel = input.breadStyle == "bagel"
        val isEnglishMuffin = input.breadStyle == "english_muffin"
        val isPretzel = input.breadStyle == "pretzel"
        val isPizzaNeo = input.breadStyle == "pizza_neo"

        val isSpeedRun = input.isSpeedRun ?: false

        // High-fat threshold: >=15% total fat disables bulk cold-retard
        // compression (below) and would trigger Room-Temp Recovery stage
        // text (a future stage-narrator port's concern, not this one's).
        val isHighFat = (input.fatPercent ?: 0.0) >= 15

        val saltPct = input.saltPercent ?: defaultSaltPercent
        val sf = saltFactor(saltPct)
        val sweetFf = sweetenerFermentFactor(input.sweetenerType, input.sweetenerPercent)

        val idtF = estimatedInitialDoughTempF(
            waterTempF = input.waterTempF, ambientTempF = input.ambientTempF,
            frictionF = frictionFactorF(forStyle = input.breadStyle),
        )

        // Bulk fermentation

        var baseBulkAt75: Double = if (input.isSourdough) {
            240.0
        } else {
            max(45.0, (90 / input.yeastPercent).swiftRounded())
        }

        if (input.hydrationPercent < 65) {
            baseBulkAt75 = (baseBulkAt75 * 1.15).swiftRounded()
        } else if (input.hydrationPercent > 80) {
            baseBulkAt75 = (baseBulkAt75 * 0.9).swiftRounded()
        }

        if (input.usePrefermant) {
            baseBulkAt75 = (baseBulkAt75 * 0.7).swiftRounded()
        }

        // Diastatic malt: plain nil-coalescing to 0 here (NOT the
        // truthy-ternary FormulaCalculator uses for maltWeight) — an
        // explicit 0 and an absent value behave identically either way
        // once gated by the `> 0` check that follows.
        val maltPct = input.diastaticMaltPercent ?: 0.0
        if (maltPct > 0) {
            val maltFactor = max(0.78, 1 - maltPct * 0.22)
            baseBulkAt75 = (baseBulkAt75 * maltFactor).swiftRounded()
        }

        baseBulkAt75 = (baseBulkAt75 * sf).swiftRounded()
        baseBulkAt75 = (baseBulkAt75 * sweetFf).swiftRounded()

        if (isArtisan && !input.isSourdough) {
            baseBulkAt75 = max(120.0, baseBulkAt75)
        }

        if (isCiabatta) {
            baseBulkAt75 = (baseBulkAt75 * 1.15).swiftRounded()
        }

        if (isBrioche) {
            baseBulkAt75 = (baseBulkAt75 * 1.75).swiftRounded()
        }

        if (isEnglishMuffin) {
            baseBulkAt75 = (baseBulkAt75 * 1.1).swiftRounded()
        }

        // Specialty-flour bulk adjustments compound multiplicatively
        // with everything above — intentional per the source's own
        // comment (independent physical effects on the same dough).
        if (ryePct > 30) {
            val ryeFactor = max(0.60, 0.85.pow((ryePct - 30) / 10))
            baseBulkAt75 = (baseBulkAt75 * ryeFactor).swiftRounded()
        }

        if (wwheatPct > 50) {
            baseBulkAt75 = (baseBulkAt75 * 1.10).swiftRounded()
        }

        // Time-stepped accumulation instead of a single division —
        // `THERMAL_FERMENTATION_ENGINE_SPEC.md` §6 on the iOS side. The
        // bulk window's temperature drifts from the estimated initial
        // dough temp toward ambient over time (mass-tiered closure rate,
        // §5), so the Arrhenius rate has to be re-evaluated at each step
        // rather than assumed frozen at one temperature the way final
        // proof still is (cold retard now uses this same time-stepped
        // shape below, not a frozen rate either).
        val closureRate = closureRatePerHour(totalDoughWeightG = input.totalDoughWeightG)
        val stepMinutes = 5.0
        // Defensive cap only — every calculator slider that feeds this
        // (ambientTempF 40-100°F) keeps the drift target comfortably
        // inside the Arrhenius model's 0-42°C active range, so this loop
        // always terminates well under the cap for any UI-reachable
        // input. The cap exists purely so a future caller passing raw,
        // unclamped values can't hang.
        val maxElapsedMinutes = 24.0 * 60.0

        var accumulated = 0.0
        var elapsedMinutes = 0.0
        while (accumulated < baseBulkAt75 && elapsedMinutes < maxElapsedMinutes) {
            val stepTempF = currentTempF(
                elapsedHours = elapsedMinutes / 60, idt = idtF,
                ambientTempF = input.ambientTempF, closureRatePerHour = closureRate,
            )
            accumulated += fermentRate(stepTempF) * stepMinutes
            elapsedMinutes += stepMinutes
        }
        // The dough's actual physical temperature at the moment bulk
        // fermentation ends — real elapsed time from the loop above, NOT
        // the lean-dough-compressed `bulkFermentMinutes` computed just
        // below (that compression is a fermentation-progress heuristic,
        // not a redefinition of how long bulk physically took). This
        // becomes cold retard's real starting temperature — the dough
        // enters the fridge at whatever temperature bulk left it at, not
        // instantly at fridge temperature.
        val bulkEndTempF = currentTempF(
            elapsedHours = elapsedMinutes / 60, idt = idtF,
            ambientTempF = input.ambientTempF, closureRatePerHour = closureRate,
        )
        var bulkFermentMinutes = max(30, elapsedMinutes.swiftRounded().toInt())

        // Cold retard

        val hasColdRetard = input.fermentationType == "cold" || ((input.coldRetardHours ?: 0.0) > 0)

        // Lean-dough-only bulk compression (fermentation continues
        // during the first hours of refrigeration).
        if (hasColdRetard && !isHighFat && !isBagel) {
            bulkFermentMinutes = max(30, (bulkFermentMinutes * 0.65).swiftRounded().toInt())
        }

        val coldHours = input.coldRetardHours ?: (if (hasColdRetard) 12.0 else 0.0)
        val coldTempF = input.coldRetardTempF ?: 38.0
        val finalTempF = input.finalProofTempF ?: input.ambientTempF

        // Final proof

        var baseFinalProofAt75: Double = if (input.isSourdough) 90.0 else 60.0

        if (maltPct > 0) {
            val maltFinalFactor = max(0.88, 1 - maltPct * 0.12)
            baseFinalProofAt75 = (baseFinalProofAt75 * maltFinalFactor).swiftRounded()
        }
        // Salt/sweetener affect final proof at HALF their bulk-phase
        // weight — dough is already active by this point.
        val saltFinalFactor = 1 + (sf - 1) * 0.5
        baseFinalProofAt75 = (baseFinalProofAt75 * saltFinalFactor).swiftRounded()
        val sweetFinalFf = 1 + (sweetFf - 1) * 0.5
        baseFinalProofAt75 = (baseFinalProofAt75 * sweetFinalFf).swiftRounded()

        if (isCiabatta) {
            baseFinalProofAt75 = (baseFinalProofAt75 * 1.15).swiftRounded()
        }

        if (isSpeedRun && isPizza) {
            baseFinalProofAt75 = (baseFinalProofAt75 * 0.6).swiftRounded()
        }

        if (isBrioche) {
            baseFinalProofAt75 = (baseFinalProofAt75 * 0.75).swiftRounded()
        }

        if (isPretzel) {
            baseFinalProofAt75 = 20.0
        }

        if (isBagel) {
            baseFinalProofAt75 = (baseFinalProofAt75 * 0.5).swiftRounded()
        }

        if (speltPct > 40) {
            val speltFactor = max(0.65, 0.90.pow((speltPct - 40) / 10))
            baseFinalProofAt75 = (baseFinalProofAt75 * speltFactor).swiftRounded()
        }

        if (einkornPct > 20) {
            val einkornFactor = max(0.65, 0.90.pow((einkornPct - 20) / 10))
            baseFinalProofAt75 = (baseFinalProofAt75 * einkornFactor).swiftRounded()
        }

        // Piece-size factor — applied to final proof only.
        val loafStyle = FormulaCalculator.truthy(input.loafStyle)
        if (loafStyle != null) {
            val mod = max(0.75, min(1.25, input.baguetteSizeModifier ?: 1.0))
            val psf = when {
                loafStyle.contains("_15oz") -> 0.70
                loafStyle.contains("_25oz") -> 0.80
                loafStyle == "rolls" -> 0.85
                loafStyle == "baguette_6" -> min(1.0, 0.70 * sqrt(mod))
                loafStyle == "baguette_12" -> min(1.0, 0.82 * sqrt(mod))
                loafStyle == "baguette_15" -> min(1.0, 0.88 * sqrt(mod))
                loafStyle == "baguette_18" -> min(1.0, 0.93 * sqrt(mod))
                loafStyle == "ciabatta_panino_6" -> 0.82
                loafStyle == "soft_hoagie_6in" -> 0.82
                loafStyle == "soft_hoagie_8in" -> 0.88
                loafStyle == "soft_burger_bun" -> 0.78
                // Unreachable in the source too: "brioche_rolls_15oz" and
                // "brioche_rolls_25oz" already match the "_15oz"/"_25oz"
                // `contains()` branches above with the SAME values (0.70/
                // 0.80), so control never actually reaches these two
                // `when` branches. Kept in this exact order (below the
                // substring checks) to match the source precisely rather
                // than silently deduping.
                loafStyle == "brioche_rolls_15oz" -> 0.70
                loafStyle == "brioche_rolls_25oz" -> 0.80
                // The only one of the three brioche-specific branches
                // that's actually reachable (no _15oz/_25oz substring).
                loafStyle == "brioche_burger_bun_4oz" -> 0.76
                // THERMAL_FERMENTATION_ENGINE_SPEC.md §10 addendum (iOS
                // side) — a pre-existing gap (predates the thermal work,
                // otherwise untouched by it): none of the six pizza
                // shapes had a psf branch, so they all silently fell
                // through to 1.0. Values interpolated in log-weight space
                // against the same finished-dough-weight curve the
                // baguette branches above already use (110g→0.70 ...
                // 445g→0.93), against each shape's own finishedWeight
                // from LoafShapeCatalog.
                loafStyle == "pizza_8in_ny" -> 0.72
                loafStyle == "pizza_12in_ny" -> 0.85
                loafStyle == "pizza_16in_ny" -> 0.95
                loafStyle == "pizza_8in_neo" -> 0.71
                loafStyle == "pizza_12in_neo" -> 0.84
                loafStyle == "pizza_16in_neo" -> 0.93
                else -> 1.0
            }
            baseFinalProofAt75 = (baseFinalProofAt75 * psf).swiftRounded()
        }

        var finalProofMinutes: Int
        var coldRetardPercent = 0

        if (hasColdRetard && coldHours > 0) {
            // Time-stepped drift instead of a single frozen-temperature
            // multiplication — the dough enters cold retard at
            // `bulkEndTempF` (wherever bulk's own drift left off, or the
            // best available signal if shaping happened in between —
            // this app doesn't track a separate shaping-stage
            // temperature), not instantly at fridge temperature, and
            // cools toward `coldTempF` over the retard window. Mirrors
            // the bulk loop's shape exactly (5-minute steps, re-evaluate
            // the Arrhenius rate at the drifting temperature each step),
            // with its own mass-tiered closure rate distinct from bulk's.
            // Unlike bulk, `coldHours` is a real, fixed, user-chosen
            // duration — the loop runs for that actual real-world time
            // (not solved for), with an early exit once
            // `baseFinalProofAt75` is fully reached.
            val coldClosureRate = coldRetardClosureRate(totalDoughWeightG = input.totalDoughWeightG)
            val coldRetardTotalMinutes = coldHours * 60

            var accumulatedColdRetard = 0.0
            var coldElapsedMinutes = 0.0
            while (coldElapsedMinutes < coldRetardTotalMinutes && accumulatedColdRetard < baseFinalProofAt75) {
                val coldStepTempF = currentColdRetardTempF(
                    startTempF = bulkEndTempF, fridgeTempF = coldTempF,
                    closureRate = coldClosureRate, elapsedHours = coldElapsedMinutes / 60,
                )
                accumulatedColdRetard += fermentRate(coldStepTempF) * stepMinutes
                coldElapsedMinutes += stepMinutes
            }
            val fermentedDuringRetard = accumulatedColdRetard
            coldRetardPercent = min(100, (fermentedDuringRetard / baseFinalProofAt75 * 100).swiftRounded().toInt())

            val remainingAt75 = max(0.0, baseFinalProofAt75 - fermentedDuringRetard)
            val finalRate = fermentRate(finalTempF)

            finalProofMinutes = if (remainingAt75 < 5) {
                // Retard fully completed the final proof — only a short
                // temper/bench rest needed.
                max(15, (20 / finalRate).swiftRounded().toInt())
            } else {
                max(10, (remainingAt75 / finalRate).swiftRounded().toInt())
            }
        } else {
            val finalRate = fermentRate(finalTempF)
            finalProofMinutes = max(15, (baseFinalProofAt75 / finalRate).swiftRounded().toInt())
        }

        // Neapolitan pizza cold-retard: enforce a 120-min minimum temper.
        if (isPizzaNeo && hasColdRetard && coldHours > 0) {
            finalProofMinutes = max(120, finalProofMinutes)
        }

        // Humidity adjustment applied AFTER all style minimums and
        // cold-retard math are settled.
        val hpf = humidityProofFactor(input.relativeHumidity)
        if (hpf != 1.0) {
            bulkFermentMinutes = max(20, (bulkFermentMinutes * hpf).swiftRounded().toInt())
            finalProofMinutes = max(10, (finalProofMinutes * hpf).swiftRounded().toInt())
        }

        // Autolyse bulk-time adjustment (BreadIQ-native — not part of
        // the ported `calcProofTime()` source; see [AutolyseCalculator]'s
        // doc comment for the two comparison tables this implements).
        // Applied last, same "settle everything else first" convention
        // the humidity adjustment just above uses, so this feature
        // layers cleanly on top of the verified port rather than
        // touching its internals — deliberately NOT folded into the
        // existing ryePct>30/wwheatPct>50 branches above, which stay
        // exactly as ported.
        val autolyseGuidance = AutolyseCalculator.calculate(blend = blend)
        if (autolyseGuidance.bulkTimeFactor != 1.0) {
            bulkFermentMinutes = max(20, (bulkFermentMinutes * autolyseGuidance.bulkTimeFactor).swiftRounded().toInt())
        }

        // Room-temp recovery (cold retard only)

        val recoveryMinutes: Int? = if (hasColdRetard && coldHours > 0)
            max(20, (45 / fermentRate(input.ambientTempF)).swiftRounded().toInt())
        else null

        return ProofTimeMath(
            bulkFermentMinutes = bulkFermentMinutes,
            finalProofMinutes = finalProofMinutes,
            coldFermentHours = if (hasColdRetard) coldHours else null,
            recoveryMinutes = recoveryMinutes,
            coldRetardPercent = coldRetardPercent,
            estimatedIDTF = idtF,
        )
    }
}
