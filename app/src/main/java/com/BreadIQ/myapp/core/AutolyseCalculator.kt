package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.FlourBlendEntry
import kotlin.math.max
import kotlin.math.min

/**
 * Ported from the iOS app's `Core/AutolyseCalculator.swift`.
 *
 * Three-tier trigger band for high whole-wheat/rye blends — thresholds
 * and per-tier guidance come directly from the baker's own framework
 * (two comparison tables: hydration/autolyse-requirement/mixing-style by
 * %, and a Standard-vs-High-Whole-Wheat metric table, per the iOS port's
 * own writeup). [AutolyseCalculator.calculate] is the single source of
 * truth for both tables — every call site should read from the same
 * [AutolyseGuidance] value rather than re-deriving tiers locally.
 */
enum class AutolyseTier(val rawValue: String) {
    STANDARD("standard"),
    RECOMMENDED("recommended"),
    MANDATORY("mandatory"),
}

/**
 * Tiered mixing/fermentation guidance for high whole-wheat and rye flour
 * blends — a BreadIQ-native feature, not ported from the original Expo
 * app (no source-app equivalent exists there either). Whole wheat's bran
 * and germ physically shear developing gluten strands, and rye carries
 * almost no gluten-forming protein at all, so both push a formula toward
 * the same mitigation: a longer autolyse rest, gentler mixing, and a
 * shorter, less-aggressive bulk fermentation than a standard bread-flour
 * dough would use.
 *
 * `combinedPercent` sums whole wheat + rye together rather than tracking
 * two separate single-flour thresholds — both flours drive the same
 * gluten-development problem, per the iOS port's own note that the
 * baker's original ask was explicitly "high percentage whole wheat OR
 * rye" blends. A 20% whole wheat + 20% rye blend should trigger the same
 * guidance as 40% of either alone, not fall through the gap between two
 * independent thresholds.
 *
 * Trigger boundary: 30%+ combined is the line, with the specific
 * tier/guidance following the tables below.
 *
 * **What's a direct table transcription vs. an estimate** (per the iOS
 * port's own accounting): Tier 1 (≤30%) and Tier 3 (61-100%) are both
 * fully specified by the baker's two tables. Tier 2 (31-60%) sits
 * between those two fully-specified endpoints, but the tables only gave
 * numeric knead-time/bulk-volume/bulk-time figures for the two
 * endpoints, not the middle band — Tier 2's knead time, bulk volume
 * target, and fold schedule text below are reasonable midpoint
 * estimates, not literal transcription. `bulkTimeFactor` is the one
 * number computed continuously (linear interpolation from 1.0 at 30% to
 * the Tier-3 table ratio at 60%, flat beyond that) rather than picked ad
 * hoc, mirroring [ProofTimeCalculator]'s own preference for smooth
 * curves over step functions elsewhere in this same calculation.
 */
data class AutolyseGuidance(
    val wholeWheatPercent: Double,
    val ryePercent: Double,
    val combinedPercent: Double,
    val tier: AutolyseTier,

    /** True only for Tier 3 — autolyse is mandatory, not just advised. */
    val autolyseRequired: Boolean,
    /** True for Tier 2 and Tier 3 — anything above the 30% trigger line. */
    val autolyseAdvised: Boolean,
    val autolyseDurationMinutes: Int,

    val hydrationRangeLow: Double,
    val hydrationRangeHigh: Double,
    /** e.g. "78–85%+" — Tier 3's table entry has an open-ended top end, so this is kept as display text rather than forcing a second numeric bound that doesn't exist in the source table. */
    val hydrationRangeLabel: String,

    val mixingStyleLabel: String,
    val kneadTimeLabel: String,
    val foldSetCount: Int,
    /** `null` for Tier 1, where folds are optional with no set schedule. */
    val foldScheduleLabel: String? = null,

    val bulkVolumeTargetLabel: String,
    /** Multiplier applied on top of [ProofTimeCalculator]'s own bulk-time math — 1.0 for Tier 1 (no change), easing down to ~0.65 by 60% combined and flat from there. */
    val bulkTimeFactor: Double,

    val coldRetardStrengthLabel: String,
    val coldRetardGuidance: String,

    /** Original copy — no equivalent poke-test guidance exists anywhere else in the app for this blend type. */
    val pokeTestGuidance: String,

    /** One-line "why" shown in the UI — kept separate from the mixing/hydration fields so a details screen can lead with it before the detail cards. */
    val explainer: String,
)

object AutolyseCalculator {

    fun calculate(blend: List<FlourBlendEntry>?): AutolyseGuidance {
        val resolvedBlend = blend ?: emptyList()
        val wwheatPct = resolvedBlend.firstOrNull { it.type == "whole_wheat" }?.percent ?: 0.0
        val ryePct = resolvedBlend.firstOrNull { it.type == "rye" }?.percent ?: 0.0
        val combined = wwheatPct + ryePct

        val tier = when {
            combined <= 30 -> AutolyseTier.STANDARD
            combined <= 60 -> AutolyseTier.RECOMMENDED
            else -> AutolyseTier.MANDATORY
        }

        // Continuous 1.0 → ~0.65 ease, flat past 60% combined — see
        // class doc comment for why this one field is interpolated
        // rather than tier-stepped like everything else.
        val t = min(1.0, max(0.0, (combined - 30) / 30))
        val bulkTimeFactor = if (combined <= 30) 1.0 else (1.0 - t * 0.35)

        return when (tier) {
            AutolyseTier.STANDARD -> AutolyseGuidance(
                wholeWheatPercent = wwheatPct, ryePercent = ryePct, combinedPercent = combined, tier = tier,
                autolyseRequired = false, autolyseAdvised = false, autolyseDurationMinutes = 15,
                hydrationRangeLow = 65.0, hydrationRangeHigh = 72.0, hydrationRangeLabel = "65–72%",
                mixingStyleLabel = "Standard / Standard Mechanical",
                kneadTimeLabel = "8–10 min, Medium Speed",
                foldSetCount = 0, foldScheduleLabel = null,
                bulkVolumeTargetLabel = "Double in size (100% growth) — the standard target.",
                bulkTimeFactor = bulkTimeFactor,
                coldRetardStrengthLabel = "Optional",
                coldRetardGuidance = "Countertop or cold retard both work well at this blend — no strong lean either way.",
                pokeTestGuidance = "",
                explainer = "30% or less whole wheat/rye — standard mixing and fermentation apply, autolyse is optional.",
            )
            AutolyseTier.RECOMMENDED -> {
                // Linear midpoint between Tier 1 and Tier 3's
                // fully-specified endpoints — see class doc comment: not
                // a literal table value.
                val volumeGrowthPct = (100 - t * 60).swiftRounded().toInt() // 100% at combined=30 -> 40% at combined=60
                AutolyseGuidance(
                    wholeWheatPercent = wwheatPct, ryePercent = ryePct, combinedPercent = combined, tier = tier,
                    autolyseRequired = false, autolyseAdvised = true, autolyseDurationMinutes = 30,
                    hydrationRangeLow = 72.0, hydrationRangeHigh = 78.0, hydrationRangeLabel = "72–78%",
                    mixingStyleLabel = "Medium Mix + 2 Stretch & Folds",
                    kneadTimeLabel = "5–7 min, Medium-Low Speed", // interpolated — flagged above
                    foldSetCount = 2, foldScheduleLabel = "Every 20–30 min during bulk fermentation, until 2 sets are complete.",
                    bulkVolumeTargetLabel = "~$volumeGrowthPct% growth — noticeably less than doubling.",
                    bulkTimeFactor = bulkTimeFactor,
                    coldRetardStrengthLabel = "Recommended",
                    coldRetardGuidance = "8–12 hours in the fridge firms up a slacker, higher-hydration dough and makes it easier to shape.",
                    pokeTestGuidance = "Poke test: press a floured finger ½\" in. Expect a slower, duller spring-back than a standard dough — 60–70% recovery in 4–5 seconds is normal here, not a sign of under-proofing. Full, immediate spring-back means it's still under-proofed.",
                    explainer = "31–60% whole wheat/rye — bran and reduced gluten content slow gluten development. A 30-min autolyse gets most of the way there without over-working the dough.",
                )
            }
            AutolyseTier.MANDATORY -> {
                // Table gives "45m+"; scale gently above 60% rather than
                // holding flat, capped at 60 min.
                val duration = min(60, 45 + (((combined - 60) / 10).swiftRounded().toInt()) * 5)
                AutolyseGuidance(
                    wholeWheatPercent = wwheatPct, ryePercent = ryePct, combinedPercent = combined, tier = tier,
                    autolyseRequired = true, autolyseAdvised = true, autolyseDurationMinutes = duration,
                    hydrationRangeLow = 78.0, hydrationRangeHigh = 85.0, hydrationRangeLabel = "78–85%+",
                    mixingStyleLabel = "Minimal Mix + 3–4 Gentle Folds",
                    kneadTimeLabel = "2–3 min, Low Speed — just to incorporate",
                    foldSetCount = 4, foldScheduleLabel = "Every 20–30 min during bulk fermentation — gentle coil/stretch folds, 3–4 sets total.",
                    bulkVolumeTargetLabel = "30–50% growth — do not expect a full double.",
                    bulkTimeFactor = bulkTimeFactor,
                    coldRetardStrengthLabel = "Strongly Recommended",
                    coldRetardGuidance = "8–12 hours in the fridge. This dough is slack and sticky at room temperature — cold retard stiffens it enough to shape and score cleanly. First formula type in the app to get a proactive cold-retard recommendation.",
                    pokeTestGuidance = "Poke test: press a floured finger ½\" in. This dough won't spring back like a standard loaf — bran and low gluten content mean the indent stays mostly in place, filling back only slightly over 5–6 seconds. Judge readiness more by the 30–50% volume increase than by spring-back alone; over-proofed high-whole-wheat dough collapses rather than over-domes.",
                    explainer = "Over 60% whole wheat/rye — mechanical kneading alone won't develop enough structure before the bran cuts gluten strands. Autolyse does the real work here; mixing stays deliberately minimal.",
                )
            }
        }
    }

    /**
     * Convenience for call sites that only need the yes/no gate, not the
     * full guidance payload (e.g. deciding whether to show a UI button
     * at all).
     */
    fun appliesAbove(blend: List<FlourBlendEntry>?): Boolean = calculate(blend).tier != AutolyseTier.STANDARD
}
