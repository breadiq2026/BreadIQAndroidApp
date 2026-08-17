package com.BreadIQ.myapp.core

import com.BreadIQ.myapp.model.SubscriptionPackage
import com.BreadIQ.myapp.model.TierInfo

/**
 * Seam between [com.BreadIQ.myapp.viewmodel.SubscriptionViewModel] and
 * the real backing implementation ([RevenueCatPurchasesService]) — same
 * "interface + error type + Unconfigured stub in one file" bundling
 * this codebase already uses for [com.BreadIQ.myapp.data.AuthServicing]/
 * [ImportServices].
 */
interface PurchasesServicing {
    /**
     * Currently-active RevenueCat entitlement identifiers — matches
     * [RevenueCatTierResolution.rcTier]'s input shape directly, so the
     * real implementation just needs to map `CustomerInfo.entitlements
     * .active`'s keys into a `Set<String>`.
     */
    suspend fun activeEntitlements(): Set<String>

    suspend fun logIn(userId: String)

    suspend fun logOut()

    /**
     * `Purchases.restorePurchases()`. Returns BOTH fields the two
     * source restore handlers (`settings.tsx` and `subscription.tsx`)
     * actually read off the resulting `CustomerInfo` — verified
     * directly against both, not assumed to be the same field:
     * `entitlements.active` (for tier resolution) and
     * `activeSubscriptions` (what BOTH source handlers' restored/
     * not-found branch is keyed on — genuinely a different field from
     * entitlements, not interchangeable with it in general, even
     * though they're normally in lockstep for a correctly-entitled
     * product).
     */
    suspend fun restorePurchases(): RestoreResult

    /**
     * The raw RC packages, already mapped into the pure
     * [SubscriptionPackage] shape via
     * [SubscriptionPackageClassification.classify] when mapping the
     * real SDK response.
     */
    suspend fun fetchOfferings(): List<SubscriptionPackage>

    /**
     * `useSubscription().purchase` (`Purchases.purchasePackage(pkg)`).
     * Takes the product identifier rather than the pure
     * [SubscriptionPackage] value itself, since only the real
     * implementation needs to resolve that back to an actual RC
     * `Package` object to hand to the SDK.
     *
     * **A real Android SDK shape difference from iOS, not a straight
     * port**: Google Play Billing's purchase flow needs a live
     * `Activity` to launch its UI from — `Purchases.shared.purchase(package:)`
     * on iOS needs no such thing (StoreKit's purchase sheet is
     * presented by the OS itself, no view controller reference
     * required). [activity] is threaded down from the Composable call
     * site (`SubscriptionScreen.kt`'s own `LocalContext.current`),
     * fetched fresh at the moment of the tap rather than stored on this
     * service or on [com.BreadIQ.myapp.viewmodel.SubscriptionViewModel] —
     * same "Context-needing calls happen at the Composable layer"
     * boundary already established for Haptics/camera capture/calendar
     * permissions elsewhere in this app.
     */
    suspend fun purchase(activity: android.app.Activity, productIdentifier: String): PurchaseOutcome
}

data class RestoreResult(
    val activeEntitlements: Set<String>,
    val activeSubscriptionProductIdentifiers: Set<String>,
)

/** `(err as { message?: string })?.message ?? ""` — the source's raw, unhumanized purchase error, same treatment as `AuthServiceError`. */
data class PurchaseError(val message: String)

sealed class PurchaseOutcome {
    data object Success : PurchaseOutcome()
    data class Failure(val error: PurchaseError) : PurchaseOutcome()
}

/** Seam for the "Backend REST API client" — specifically `GET /api/me/tier`, the fallback `useUserTier` reaches for when RevenueCat reports free. */
interface TierServicing {
    suspend fun fetchTier(): TierInfo?
}

class UnconfiguredPurchasesService : PurchasesServicing {
    override suspend fun activeEntitlements(): Set<String> = emptySet()
    override suspend fun logIn(userId: String) {}
    override suspend fun logOut() {}
    override suspend fun restorePurchases(): RestoreResult = RestoreResult(emptySet(), emptySet())
    override suspend fun fetchOfferings(): List<SubscriptionPackage> = emptyList()
    override suspend fun purchase(activity: android.app.Activity, productIdentifier: String): PurchaseOutcome =
        PurchaseOutcome.Failure(PurchaseError("Purchases aren't available yet — check back once the store connection is finished."))
}

class UnconfiguredTierService : TierServicing {
    override suspend fun fetchTier(): TierInfo? = null
}
