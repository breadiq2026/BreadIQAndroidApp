package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.BakeStepContent
import com.BreadIQ.myapp.model.NotifCopy

/**
 * Ported from the iOS app's `Core/BakeStepContentLookup.swift` — the
 * behavior half of the bake-step content system, sitting on top of the
 * static data ([BakeStepContent]) already ported alongside step 1's
 * models. Pure lookup/decision logic only — actual notification
 * scheduling is out of scope this session, same boundary as
 * [BakeSessionEngine]'s own `ovenPreheatFireTime`/`coilFoldFireTimes`.
 */
object BakeStepContentLookup {

    /** `getCoilFoldNotif()` — fixed content, no lookup at all. */
    fun coilFoldNotif(): NotifCopy = NotifCopy(title = "Time for your coil fold.", body = "Your dough is building strength.")

    /**
     * Explicit iteration order for [stepCompleteNotif]'s fold-variant
     * fallback, matching the source's `Object.entries()` (JS preserves
     * string-key insertion order). A Kotlin `Map`'s iteration order
     * isn't guaranteed either (though `LinkedHashMap`, which `mapOf`
     * happens to return, does preserve it in practice) — this explicit
     * list is kept anyway for the same "don't rely on an incidental
     * implementation detail" reasoning as the iOS port.
     */
    private val stretchFoldVariantOrder = listOf("Set 1", "Set 2", "Set 3", "Set 4")

    /**
     * `getStepCompleteNotif()` — three-tier fallback:
     * 1. Exact match in [BakeStepContent.stepComplete].
     * 2. For labels starting with "Stretch & Fold" or "Coil Fold":
     *    substring-match (not prefix!) against each fold-variant key,
     *    in declaration order, first match wins; if none match, a
     *    generic "next fold" notification.
     * 3. Fully generic "Step complete." notification for anything else.
     */
    fun stepCompleteNotif(label: String): NotifCopy {
        BakeStepContent.stepComplete[label]?.let { return it }

        if (label.startsWith("Stretch & Fold") || label.startsWith("Coil Fold")) {
            for (key in stretchFoldVariantOrder) {
                if (label.contains(key)) {
                    BakeStepContent.stretchFoldVariants[key]?.let { return it }
                }
            }
            return NotifCopy(
                title = "Time for your next fold.",
                body = "Wet hands. Lift, stretch, fold, rotate 90°. Four sides. Thirty seconds.",
            )
        }

        return NotifCopy(title = "Step complete.", body = "Your next bake step is ready.")
    }

    /**
     * Styles [com.BreadIQ.myapp.model.BreadStyleDef] actually calls for
     * a Dutch oven or heavy steam — the only two whose "Final Proof"/
     * "Final Proof (post-retard)" prep notification should mention
     * preheating one.
     */
    private val dutchOvenStyles: Set<String> = setOf("artisan", "country")

    /**
     * `getStepPrepNotif()` — exact match only, `null` when absent (no
     * prefix-matching fallback at all, unlike [stepCompleteNotif]/
     * [stepDescription]). The Dutch-oven reminder used to be hardcoded
     * into every "Final Proof" copy regardless of style — now appended
     * only for the two styles that genuinely call for one.
     */
    fun stepPrepNotif(label: String, style: String): NotifCopy? {
        val base = BakeStepContent.stepPrep[label] ?: return null
        if ((label == "Final Proof" || label == "Final Proof (post-retard)") && dutchOvenStyles.contains(style)) {
            return NotifCopy(title = base.title, body = base.body + " Preheat your Dutch oven if not done.")
        }
        return base
    }

    /** `getOvenPreheatNotif()` — templated string, fixed body. */
    fun ovenPreheatNotif(ovenTempF: Int): NotifCopy = NotifCopy(
        title = "Preheat your oven to ${ovenTempF}°F now.",
        body = "The bake starts in 45 minutes. Your oven needs time to reach temperature and fully heat-soak.",
    )

    /**
     * `getStepDescription()` — exact match, then a PREFIX (not
     * substring) check against "Coil Fold"/"Stretch & Fold" for the two
     * generic fold descriptions, then empty string.
     */
    fun stepDescription(label: String): String {
        BakeStepContent.stepDescriptions[label]?.let { return it }
        if (label.startsWith("Coil Fold")) return BakeStepContent.foldDescriptionCoil
        if (label.startsWith("Stretch & Fold")) return BakeStepContent.foldDescriptionSF
        return ""
    }
}
