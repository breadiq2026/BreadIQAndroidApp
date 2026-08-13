package com.BreadIQ.myapp.model

/**
 * Ported from the iOS app's `Models/SubscriptionPackage.swift`.
 *
 * A RevenueCat package's purchase-relevant fields, already classified
 * into a `tier`/`period` at the SDK boundary (see the iOS
 * `SubscriptionPackageClassification` type this ports, whose job is
 * classifying `productIdentifier` once here rather than re-deriving it
 * from string matching in view logic — port that classifier alongside
 * `purchases-android` wiring in a later phase).
 *
 * Not persisted/serialized: this is a value mapped from a live
 * RevenueCat SDK object at runtime, not JSON fetched/persisted directly —
 * matching `TierInfo`'s "fetched fresh, not independently persisted"
 * treatment.
 */
data class SubscriptionPackage(
    /** "basic" | "premium" — see `SubscriptionPackageClassification`. */
    val tier: String,
    /** "monthly" | "annual" — see `SubscriptionPackageClassification`. */
    val period: String,
    val productIdentifier: String,
    /**
     * `pkg.product.title` — display-only (NOT used for classification,
     * unlike the source Expo app's fragile heuristic).
     */
    val title: String,
    val priceString: String,
    val price: Double,
    val currencyCode: String,
    val introPrice: SubscriptionIntroPrice? = null,
) {
    val id: String get() = productIdentifier
}

/** Port of the original Expo app's `subscription.tsx`'s local `IntroPrice` type. */
data class SubscriptionIntroPrice(
    val price: Double? = null,
    val priceString: String? = null,
    val paymentMode: String? = null,
    val period: String? = null,
    val periodUnit: String? = null,
    val periodNumberOfUnits: Int? = null,
)
