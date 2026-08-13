package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/TechniqueGuide.swift`.
 *
 * Port of `bread-lab/src/components/technique-guide.tsx`'s exact type
 * shapes (verified directly against the source, not inferred), per the iOS
 * port's own note. Types only in this pass — the six real catalogs
 * (`KNEADING`/`SHAPING_BY_STYLE`/`SHAPING_BY_SHAPE`/`PROOFING_GENERAL`/
 * `BAKING`/`BAKING_BY_STYLE`, ~60 entries of real technique prose) and the
 * lookup/fallback logic are deferred to a later pass, matching the iOS
 * port's own phased-delivery plan (see `XLSX_EXPORT_SPEC.md` there).
 */
data class TechniqueSection(
    val method: String,
    val steps: List<String>,
    val tip: String,
    val lexiconId: String? = null,
)

data class BakingSection(
    val temp: String,
    val duration: String,
    val steam: String,
    val scoring: String,
    val internalTemp: String,
    val tip: String,
)
