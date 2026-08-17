package com.BreadIQ.myapp.core

/**
 * Redesigned replacement for `subscription.tsx`'s `packageBelongsTo`/
 * `packagePeriod`, which classify a RevenueCat package's tier/period by
 * lowercasing and substring-searching a concatenation of THREE loosely
 * related fields — the package identifier, the product identifier, and
 * the product's (storefront-controlled, storefront-localized) display
 * title. That's fragile: RC's own default package identifiers
 * (`$rc_monthly`/`$rc_annual`) contain neither "basic" nor "premium", a
 * title rename silently breaks classification with no signal anywhere,
 * and the two independent substring checks can double-match a single
 * package into both tiers.
 *
 * **Verified against the real RevenueCat dashboard before redesigning**
 * (not assumed) — the iOS port's own finding, still true here since
 * entitlements/dashboard config are project-scoped, shared across both
 * apps: no per-package metadata is configured, and package identifiers
 * are inconsistent — some are custom-named, others still use RC's
 * `$rc_monthly`/`$rc_annual` defaults, which carry no tier information
 * at all. So neither metadata nor package identifiers are usable today
 * without a dashboard change. The one field left that's both fully
 * developer-controlled and consistently namespaced across the real
 * catalog is the product identifier itself.
 *
 * Structural parsing (prefix + exactly two dot-separated components),
 * not a hardcoded string whitelist — so the live catalog's own
 * inconsistencies (case, a trailing digit on some products but not
 * others) are handled by case-folding and a prefix check rather than
 * baked in as special cases, and a future re-submission that bumps a
 * suffix (`annual2`) still classifies correctly with no code change.
 *
 * **Open decision, not yet resolved — flagged rather than guessed at**:
 * iOS's real App Store Connect product identifiers already use
 * `io.breadiq.app.{basic|premium}.{annual|monthly}[N]`. Play Store
 * subscription products don't exist yet (confirmed: no build uploaded,
 * nothing created in Play Console's Monetize section either), so
 * Android has no real product identifiers to classify against yet.
 * [EXPECTED_PREFIX] below reuses iOS's exact prefix as a placeholder —
 * Play Console product IDs are arbitrary developer-chosen strings with
 * no bundle-ID-style constraint the way Apple's are, so this doesn't
 * need to match iOS at all, but reusing the identical convention keeps
 * this classifier's logic truly identical cross-platform, which is the
 * simplest option once real products get created. Revisit this constant
 * then, not now.
 */
object SubscriptionPackageClassification {

    private const val EXPECTED_PREFIX = "io.breadiq.app."

    data class Classification(val tier: String, val period: String)

    /**
     * Returns `null` for anything that doesn't match the expected shape
     * — mirrors the source's own fail-safe behavior of simply excluding
     * an unrecognized package from both tier arrays rather than
     * guessing.
     */
    fun classify(productIdentifier: String): Classification? {
        val id = productIdentifier.lowercase()
        if (!id.startsWith(EXPECTED_PREFIX)) return null

        val remainder = id.substring(EXPECTED_PREFIX.length)
        val parts = remainder.split(".")
        if (parts.size != 2) return null

        val tier = when (parts[0]) {
            "basic" -> "basic"
            "premium" -> "premium"
            else -> return null
        }

        val periodComponent = parts[1]
        val period = when {
            periodComponent.startsWith("annual") || periodComponent.startsWith("year") -> "annual"
            periodComponent.startsWith("monthly") -> "monthly"
            else -> return null
        }

        return Classification(tier, period)
    }
}
